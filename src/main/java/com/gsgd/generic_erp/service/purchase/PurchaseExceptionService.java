package com.gsgd.generic_erp.service.purchase;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gsgd.generic_erp.configuration.i18n.I18n;
import com.gsgd.generic_erp.configuration.i18n.LocalizedText;
import com.gsgd.generic_erp.dto.purchase.PurchaseExceptionActionRequest;
import com.gsgd.generic_erp.dto.purchase.PurchaseExceptionReportRequest;
import com.gsgd.generic_erp.dto.purchase.PurchaseExceptionView;
import com.gsgd.generic_erp.entity.auth.User;
import com.gsgd.generic_erp.entity.purchase.PurchaseException;
import com.gsgd.generic_erp.entity.purchase.PurchaseExceptionLog;
import com.gsgd.generic_erp.entity.purchase.PurchaseOrder;
import com.gsgd.generic_erp.enums.NotifactionPriority;
import com.gsgd.generic_erp.enums.NotificationType;
import com.gsgd.generic_erp.enums.PurchaseExceptionAction;
import com.gsgd.generic_erp.enums.PurchaseExceptionCategory;
import com.gsgd.generic_erp.enums.PurchaseExceptionSeverity;
import com.gsgd.generic_erp.enums.PurchaseExceptionStatus;
import com.gsgd.generic_erp.repository.auth.UserRepository;
import com.gsgd.generic_erp.repository.purchase.PurchaseExceptionLogRepository;
import com.gsgd.generic_erp.repository.purchase.PurchaseExceptionRepository;
import com.gsgd.generic_erp.repository.purchase.PurchaseOrderRepository;
import com.gsgd.generic_erp.service.notification.NotificationRecipientResolver;
import com.gsgd.generic_erp.service.notification.NotificationRequestedEvent;

import lombok.RequiredArgsConstructor;

/**
 * Purchasing exceptions: a purchaser reports a supplier problem on a purchase
 * order, PMC / Planning / Operations handle it, and the reporter or the
 * purchasing manager closes it.
 *
 * <pre>
 * REPORTED --ACKNOWLEDGE--> ACKNOWLEDGED --START--> IN_PROGRESS --RESOLVE--> RESOLVED --CLOSE--> CLOSED
 *     \______________________ RESOLVE _____________________/        ^          |
 *                                                                   +-REOPEN---+ (back to IN_PROGRESS)
 * </pre>
 *
 * Every step writes a log row. Reporting and every status change alert the
 * reporter, the purchaser of the order, the {@code notify-roles} and the users
 * of the {@code notify-dept-ids} departments (Planning, Operations), except
 * the user who acted. A comment alerts only the reporter and the handler.
 */
@Service
@RequiredArgsConstructor
public class PurchaseExceptionService {

    static final String BIZ_TYPE = "PURCHASE_EXCEPTION";
    static final int TEXT_MAX = 2000;
    private static final DateTimeFormatter NO_DATE = DateTimeFormatter.ofPattern("yyMMdd", Locale.ROOT);
    private static final Set<PurchaseExceptionStatus> OPEN = EnumSet.of(PurchaseExceptionStatus.REPORTED,
            PurchaseExceptionStatus.ACKNOWLEDGED, PurchaseExceptionStatus.IN_PROGRESS);
    private static final Set<PurchaseExceptionStatus> ALL = EnumSet.allOf(PurchaseExceptionStatus.class);

    private final PurchaseExceptionRepository exceptionRepository;
    private final PurchaseExceptionLogRepository logRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final UserRepository userRepository;
    private final NotificationRecipientResolver recipientResolver;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Value("${erp.purchase.exception.notify-roles:purchaser,pmc}")
    private List<String> notifyRoles = List.of("purchaser", "pmc");

    @Value("${erp.purchase.exception.notify-dept-ids:}")
    private List<Long> notifyDeptIds = List.of();

    /**
     * What the current user may do, read from their permission codes by the controller.
     *
     * @param seesAll  data.purchaseException.all: sees every exception
     * @param handles  menu.purchaseException.update and data.purchaseException.all:
     *                 acknowledges, starts and resolves
     * @param reports  menu.purchaseException.create
     * @param poAll    data.purchaseOrder.all (purchasing manager): reports on any
     *                 order, closes and reopens any exception
     */
    public record Actor(Long userId, boolean seesAll, boolean handles, boolean reports, boolean poAll) {
    }

    /** A request the user may not make, or that does not fit the exception's state. */
    public static class PurchaseExceptionError extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final int status;

        public PurchaseExceptionError(int status, String localizedMessage) {
            super(localizedMessage);
            this.status = status;
        }

        public int getStatus() {
            return status;
        }
    }

    // ----- report -------------------------------------------------------------

    @Transactional
    public PurchaseExceptionView report(Actor actor, PurchaseExceptionReportRequest req) {
        if (!actor.reports()) {
            throw error(403, "exception.error.cannotReport");
        }
        if (req == null) {
            throw error(400, "exception.error.orderRequired");
        }
        PurchaseOrder po = findOrder(actor, req);
        PurchaseExceptionCategory category = parse(PurchaseExceptionCategory.class, req.category(),
                "exception.error.category");
        PurchaseExceptionSeverity severity = req.severity() == null || req.severity().isBlank()
                ? PurchaseExceptionSeverity.NORMAL
                : parse(PurchaseExceptionSeverity.class, req.severity(), "exception.error.severity");
        String description = text(req.description(), true, "exception.error.descriptionRequired");

        LocalDateTime now = LocalDateTime.now(clock);
        PurchaseException e = exceptionRepository.save(PurchaseException.builder()
                .purchaseOrderId(po.getId())
                .poNo(po.getExternalPoNo())
                .supplierCode(po.getSupplierCode())
                .supplierName(po.getSupplierName())
                .reporterId(actor.userId())
                .category(category)
                .severity(severity)
                .description(description)
                .impactDate(req.impactDate())
                .status(PurchaseExceptionStatus.REPORTED)
                .reportedAt(now)
                .updatedAt(now)
                .build());
        e.setExceptionNo("EX-" + now.format(NO_DATE) + "-" + String.format(Locale.ROOT, "%04d", e.getId()));
        exceptionRepository.save(e);
        log(e, PurchaseExceptionAction.REPORT, null, actor.userId(), null, now);

        Set<Long> to = everyone(e, po);
        to.add(actor.userId()); // the reporter gets a confirmation
        publish(NotificationType.EXCEPTION_REPORTED, severity.priority(),
                LocalizedText.of("exception.notify.reportedTitle", label("severity", severity), e.getPoNo(),
                        label("category", category)),
                LocalizedText.of("exception.notify.reportedContent", e.getExceptionNo(), supplier(e),
                        clip(description), impact(e)),
                e.getId(), to);
        return view(e, actor, true);
    }

    // ----- status actions -----------------------------------------------------

    @Transactional
    public PurchaseExceptionView act(Actor actor, Long id, PurchaseExceptionActionRequest req) {
        PurchaseException e = visible(actor, id);
        PurchaseExceptionAction action = req == null ? null
                : parse(PurchaseExceptionAction.class, req.action(), "exception.error.action");
        if (action == null || action == PurchaseExceptionAction.REPORT) {
            throw error(400, "exception.error.action");
        }
        if (req.lockVersion() != null && req.lockVersion() != e.getLockVersion()) {
            throw error(409, "exception.error.stale");
        }
        if (!allowed(e, actor).contains(action)) {
            throw error(409, "exception.error.notAllowed");
        }
        boolean needsText = action == PurchaseExceptionAction.COMMENT || action == PurchaseExceptionAction.RESOLVE
                || action == PurchaseExceptionAction.REOPEN;
        String comment = text(req.comment(), needsText, switch (action) {
        case RESOLVE -> "exception.error.resolutionRequired";
        case REOPEN -> "exception.error.reasonRequired";
        default -> "exception.error.commentRequired";
        });

        LocalDateTime now = LocalDateTime.now(clock);
        PurchaseExceptionStatus from = e.getStatus();
        switch (action) {
        case ACKNOWLEDGE -> {
            e.setStatus(PurchaseExceptionStatus.ACKNOWLEDGED);
            e.setHandlerId(actor.userId());
        }
        case START -> {
            e.setStatus(PurchaseExceptionStatus.IN_PROGRESS);
            if (e.getHandlerId() == null) {
                e.setHandlerId(actor.userId());
            }
        }
        case RESOLVE -> {
            e.setStatus(PurchaseExceptionStatus.RESOLVED);
            e.setResolution(comment);
            e.setResolvedAt(now);
            if (e.getHandlerId() == null) {
                e.setHandlerId(actor.userId());
            }
        }
        case CLOSE -> {
            e.setStatus(PurchaseExceptionStatus.CLOSED);
            e.setClosedAt(now);
        }
        case REOPEN -> {
            e.setStatus(PurchaseExceptionStatus.IN_PROGRESS);
            e.setResolvedAt(null);
        }
        default -> {
            // COMMENT keeps the status.
        }
        }
        e.setUpdatedAt(now);
        exceptionRepository.save(e);
        log(e, action, from, actor.userId(), comment, now);

        Set<Long> to;
        if (action == PurchaseExceptionAction.COMMENT) {
            to = new LinkedHashSet<>();
            to.add(e.getReporterId());
            if (e.getHandlerId() != null) {
                to.add(e.getHandlerId());
            }
        } else {
            to = everyone(e, purchaseOrderRepository.findById(e.getPurchaseOrderId()).orElse(null));
        }
        to.remove(actor.userId());
        publish(NotificationType.EXCEPTION_UPDATED,
                action == PurchaseExceptionAction.COMMENT ? NotifactionPriority.NORMAL : e.getSeverity().priority(),
                LocalizedText.of("exception.notify.updatedTitle", e.getExceptionNo(), e.getPoNo(),
                        label("status", e.getStatus())),
                LocalizedText.of(comment == null ? "exception.notify.updatedContent"
                        : "exception.notify.updatedContentWithComment", name(actor.userId()),
                        label("action", action), clip(comment == null ? "" : comment)),
                e.getId(), to);
        return view(e, actor, true);
    }

    // ----- reads --------------------------------------------------------------

    @Transactional(readOnly = true)
    public Page<PurchaseExceptionView> list(Actor actor, boolean openOnly, Pageable pageable) {
        Set<PurchaseExceptionStatus> statuses = openOnly ? OPEN : ALL;
        Page<PurchaseException> page = actor.seesAll() ? exceptionRepository.findByStatusIn(statuses, pageable)
                : exceptionRepository.findVisibleTo(actor.userId(), statuses, pageable);
        Map<Long, String> names = names(page.getContent().stream()
                .flatMap(e -> java.util.stream.Stream.of(e.getReporterId(), e.getHandlerId())).toList());
        return page.map(e -> view(e, actor, names, List.of()));
    }

    @Transactional(readOnly = true)
    public PurchaseExceptionView detail(Actor actor, Long id) {
        return view(visible(actor, id), actor, true);
    }

    /** Open exceptions (REPORTED, ACKNOWLEDGED, IN_PROGRESS) the user can see, for the menu badge. */
    @Transactional(readOnly = true)
    public long openCount(Actor actor) {
        return actor.seesAll() ? exceptionRepository.countByStatusIn(OPEN)
                : exceptionRepository.findVisibleTo(actor.userId(), OPEN, Pageable.ofSize(1)).getTotalElements();
    }

    /** The actions the user may take on the exception now. */
    public List<PurchaseExceptionAction> allowed(PurchaseException e, Actor actor) {
        List<PurchaseExceptionAction> out = new ArrayList<>();
        boolean owner = actor.poAll() || Objects.equals(e.getReporterId(), actor.userId());
        switch (e.getStatus()) {
        case REPORTED -> {
            if (actor.handles()) {
                out.add(PurchaseExceptionAction.ACKNOWLEDGE);
                out.add(PurchaseExceptionAction.RESOLVE);
            }
        }
        case ACKNOWLEDGED -> {
            if (actor.handles()) {
                out.add(PurchaseExceptionAction.START);
                out.add(PurchaseExceptionAction.RESOLVE);
            }
        }
        case IN_PROGRESS -> {
            if (actor.handles()) {
                out.add(PurchaseExceptionAction.RESOLVE);
            }
        }
        case RESOLVED -> {
            if (owner) {
                out.add(PurchaseExceptionAction.CLOSE);
                out.add(PurchaseExceptionAction.REOPEN);
            }
        }
        case CLOSED -> {
            return out;
        }
        }
        out.add(PurchaseExceptionAction.COMMENT);
        return out;
    }

    // ----- helpers ------------------------------------------------------------

    private PurchaseOrder findOrder(Actor actor, PurchaseExceptionReportRequest req) {
        List<PurchaseOrder> found;
        if (req.purchaseOrderId() != null) {
            found = purchaseOrderRepository.findById(req.purchaseOrderId()).map(List::of).orElse(List.of());
        } else if (req.poNo() != null && !req.poNo().isBlank()) {
            found = purchaseOrderRepository.findByExternalPoNo(req.poNo().strip());
        } else {
            throw error(400, "exception.error.orderRequired");
        }
        List<PurchaseOrder> mine = found.stream()
                .filter(po -> actor.poAll() || Objects.equals(po.getPurchaserId(), actor.userId()))
                .toList();
        if (mine.isEmpty()) {
            throw error(404, "exception.error.orderNotFound");
        }
        return mine.get(0);
    }

    private PurchaseException visible(Actor actor, Long id) {
        PurchaseException e = exceptionRepository.findById(id)
                .orElseThrow(() -> error(404, "exception.error.notFound"));
        if (actor.seesAll() || Objects.equals(e.getReporterId(), actor.userId())) {
            return e;
        }
        boolean purchaser = purchaseOrderRepository.findById(e.getPurchaseOrderId())
                .map(po -> Objects.equals(po.getPurchaserId(), actor.userId())).orElse(false);
        if (!purchaser) {
            throw error(404, "exception.error.notFound");
        }
        return e;
    }

    private Set<Long> everyone(PurchaseException e, PurchaseOrder po) {
        Set<Long> to = new LinkedHashSet<>();
        to.add(e.getReporterId());
        if (po != null && po.getPurchaserId() != null) {
            to.add(po.getPurchaserId());
        }
        if (e.getHandlerId() != null) {
            to.add(e.getHandlerId());
        }
        to.addAll(recipientResolver.usersWithRoles(notifyRoles));
        to.addAll(recipientResolver.usersInDepartments(notifyDeptIds));
        return to;
    }

    private void log(PurchaseException e, PurchaseExceptionAction action, PurchaseExceptionStatus from, Long userId,
            String comment, LocalDateTime now) {
        logRepository.save(PurchaseExceptionLog.builder()
                .exceptionId(e.getId())
                .action(action)
                .fromStatus(from)
                .toStatus(e.getStatus())
                .operatorId(userId)
                .comment(comment)
                .createdAt(now)
                .build());
    }

    private PurchaseExceptionView view(PurchaseException e, Actor actor, boolean withHistory) {
        List<PurchaseExceptionLog> logs = withHistory
                ? logRepository.findByExceptionIdOrderByCreatedAtAscIdAsc(e.getId())
                : List.of();
        List<Long> ids = new ArrayList<>();
        ids.add(e.getReporterId());
        ids.add(e.getHandlerId());
        logs.forEach(l -> ids.add(l.getOperatorId()));
        return view(e, actor, names(ids), logs);
    }

    private PurchaseExceptionView view(PurchaseException e, Actor actor, Map<Long, String> names,
            List<PurchaseExceptionLog> logs) {
        List<PurchaseExceptionView.Log> history = logs.stream()
                .map(l -> new PurchaseExceptionView.Log(l.getAction(), l.getFromStatus(), l.getToStatus(),
                        l.getOperatorId(), names.get(l.getOperatorId()), l.getComment(), l.getCreatedAt()))
                .toList();
        return new PurchaseExceptionView(e.getId(), e.getExceptionNo(), e.getPurchaseOrderId(), e.getPoNo(),
                e.getSupplierName() != null ? e.getSupplierName() : e.getSupplierCode(), e.getCategory(),
                e.getSeverity(), e.getDescription(), e.getImpactDate(), e.getStatus(), e.getReporterId(),
                names.get(e.getReporterId()), e.getHandlerId(), names.get(e.getHandlerId()), e.getResolution(),
                e.getReportedAt(), e.getResolvedAt(), e.getClosedAt(), e.getUpdatedAt(), e.getLockVersion(),
                allowed(e, actor), history);
    }

    private Map<Long, String> names(Collection<Long> ids) {
        Map<Long, String> out = new HashMap<>();
        List<Long> wanted = ids.stream().filter(Objects::nonNull).distinct().toList();
        if (!wanted.isEmpty()) {
            userRepository.findAllById(wanted).forEach(u -> out.put(u.getId(), displayName(u)));
        }
        return out;
    }

    private String name(Long userId) {
        return names(List.of(userId)).getOrDefault(userId, String.valueOf(userId));
    }

    private static String displayName(User u) {
        return u.getDisplayName() == null || u.getDisplayName().isBlank() ? u.getUsername() : u.getDisplayName();
    }

    private void publish(NotificationType type, NotifactionPriority priority, LocalizedText title,
            LocalizedText content, Long bizId, Set<Long> to) {
        if (!to.isEmpty()) {
            events.publishEvent(NotificationRequestedEvent.localized(type, priority, title, content, BIZ_TYPE,
                    bizId, to));
        }
    }

    private static LocalizedText label(String group, Enum<?> value) {
        return LocalizedText.of("exception." + group + "." + value.name());
    }

    private static Object supplier(PurchaseException e) {
        String s = e.getSupplierName() != null ? e.getSupplierName() : e.getSupplierCode();
        return s == null ? LocalizedText.of("exception.notify.noSupplier") : s;
    }

    private static Object impact(PurchaseException e) {
        return e.getImpactDate() == null ? LocalizedText.of("exception.notify.noImpactDate")
                : LocalizedText.of("exception.notify.impactDate", e.getImpactDate().toString());
    }

    /** Notification text stays short; the page shows the full text. */
    private static String clip(String s) {
        return s.length() <= 120 ? s : s.substring(0, 119) + "…";
    }

    private static String text(String value, boolean required, String requiredKey) {
        String v = value == null ? "" : value.strip();
        if (v.isEmpty()) {
            if (required) {
                throw error(400, requiredKey);
            }
            return null;
        }
        if (v.length() > TEXT_MAX) {
            throw error(400, "exception.error.textTooLong");
        }
        return v;
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, String errorKey) {
        try {
            return Enum.valueOf(type, value == null ? "" : value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw error(400, errorKey);
        }
    }

    private static PurchaseExceptionError error(int status, String key) {
        return new PurchaseExceptionError(status, I18n.t(key));
    }
}
