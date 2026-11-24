package com.gsgd.generic_erp.service.purchase;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gsgd.generic_erp.configuration.i18n.I18n;
import com.gsgd.generic_erp.configuration.i18n.LocalizedText;
import com.gsgd.generic_erp.dto.purchase.PlacementTaskView;
import com.gsgd.generic_erp.entity.auth.User;
import com.gsgd.generic_erp.entity.customer.CustomerOrder;
import com.gsgd.generic_erp.entity.customer.PoCustomerLink;
import com.gsgd.generic_erp.entity.purchase.PlacementTask;
import com.gsgd.generic_erp.enums.NotifactionPriority;
import com.gsgd.generic_erp.enums.NotificationType;
import com.gsgd.generic_erp.enums.PlacementTaskStatus;
import com.gsgd.generic_erp.pmc.AuditJson;
import com.gsgd.generic_erp.pmc.AuditTaskEntity;
import com.gsgd.generic_erp.pmc.AuditTaskRepository;
import com.gsgd.generic_erp.pmc.AuditTypes;
import com.gsgd.generic_erp.repository.auth.UserRepository;
import com.gsgd.generic_erp.repository.customer.CustomerOrderRepository;
import com.gsgd.generic_erp.repository.customer.PoCustomerLinkRepository;
import com.gsgd.generic_erp.repository.purchase.PlacementTaskRepository;
import com.gsgd.generic_erp.service.auth.DepartmentPeers;
import com.gsgd.generic_erp.service.notification.NotificationRecipientResolver;
import com.gsgd.generic_erp.service.notification.NotificationRequestedEvent;

import lombok.RequiredArgsConstructor;

/**
 * Purchase placement tasks: "place the purchase orders for customer order X
 * within 2 calendar days of its order audit passing".
 * <ol>
 * <li>{@link #createFromVerifiedAudits()}: every order audit round that is
 * VERIFIED gets one row. Result PASS makes an OPEN task with a deadline of
 * verification time + {@code deadline-days}, and tells the purchasing
 * department, whose members take it or give it to a colleague. Any other result, or a later round of an order that
 * already has a task, is stored as SKIPPED so it is not checked again.</li>
 * <li>{@link #closeLinked()}: an OPEN task closes at the first purchase order
 * that the old ERP links to its customer order (decided 2026-11-20).</li>
 * <li>{@link #remindAndEscalate()}: one "due soon" reminder
 * {@code remind-hours} before the deadline, and one overdue alert to the
 * assignee and the purchasing department.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class PlacementTaskService {

    private static final Logger log = LoggerFactory.getLogger(PlacementTaskService.class);
    /** The one role of the purchasing department. */
    static final String PURCHASER_ROLE = "purchaser";
    static final String BIZ_TYPE = "PLACEMENT_TASK";
    private static final List<PlacementTaskStatus> OPEN_ONLY = List.of(PlacementTaskStatus.OPEN);
    private static final List<PlacementTaskStatus> OPEN_AND_DONE = List.of(PlacementTaskStatus.OPEN,
            PlacementTaskStatus.DONE);

    private final PlacementTaskRepository taskRepository;
    private final AuditTaskRepository auditTaskRepository;
    private final AuditJson auditJson;
    private final PoCustomerLinkRepository linkRepository;
    private final CustomerOrderRepository customerOrderRepository;
    private final UserRepository userRepository;
    private final NotificationRecipientResolver recipientResolver;
    private final DepartmentPeers departmentPeers;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Value("${erp.purchase.placement.deadline-days:2}")
    private int deadlineDays = 2;

    @Value("${erp.purchase.placement.remind-hours:12}")
    private int remindHours = 12;

    /** Thrown for a request the caller may not make or that does not fit the task's state. */
    public static class PlacementException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final int status;

        public PlacementException(int status, String localizedMessage) {
            super(localizedMessage);
            this.status = status;
        }

        public int getStatus() {
            return status;
        }
    }

    public record RunResult(int created, int skipped, int closed, int reminded, int overdue) {
    }

    /** One scheduled pass: create, close, remind. */
    @Transactional
    public RunResult runOnce() {
        int[] made = createFromVerifiedAudits();
        int closed = closeLinked();
        int[] alerts = remindAndEscalate();
        return new RunResult(made[0], made[1], closed, alerts[0], alerts[1]);
    }

    /** Returns {created OPEN, stored SKIPPED}. */
    int[] createFromVerifiedAudits() {
        int created = 0;
        int skipped = 0;
        LocalDateTime now = LocalDateTime.now(clock);
        for (String auditId : taskRepository.findVerifiedAuditIdsWithoutTask()) {
            Optional<AuditTaskEntity> found = auditTaskRepository.findById(auditId);
            if (found.isEmpty()) {
                continue;
            }
            AuditTaskEntity e = found.get();
            AuditTypes.Task doc;
            try {
                doc = auditJson.read(e.document, AuditTypes.Task.class);
            } catch (RuntimeException ex) {
                // An unreadable document will not become readable: store it as SKIPPED once.
                log.warn("Audit task {} has an unreadable document; no placement task", e.id);
                doc = new AuditTypes.Task();
            }
            LocalDateTime verifiedAt = verifiedAt(doc, e).orElse(now);
            boolean pass = "PASS".equals(doc.verificationResult);
            boolean duplicate = pass && taskRepository.existsByOrderNoAndStatusIn(e.orderNumber, OPEN_AND_DONE);
            PlacementTask t = taskRepository.save(PlacementTask.builder()
                    .auditTaskId(e.id)
                    .auditRound(e.round)
                    .orderNo(e.orderNumber)
                    .auditResult(doc.verificationResult)
                    .auditVerifiedAt(verifiedAt)
                    .deadlineAt(verifiedAt.plusDays(deadlineDays))
                    .status(pass && !duplicate ? PlacementTaskStatus.OPEN : PlacementTaskStatus.SKIPPED)
                    .createdAt(now)
                    .build());
            if (t.getStatus() == PlacementTaskStatus.OPEN) {
                created++;
                notifyDepartment(NotificationType.PO_PLACEMENT_DUE, NotifactionPriority.NORMAL,
                        LocalizedText.of("placement.notify.newTitle", t.getOrderNo()),
                        LocalizedText.of("placement.notify.newContent", t.getOrderNo(), stamp(t.getDeadlineAt())),
                        t.getId());
            } else {
                skipped++;
            }
        }
        return new int[] { created, skipped };
    }

    /** Closes OPEN tasks whose customer order has a linked purchase order. */
    int closeLinked() {
        List<PlacementTask> open = taskRepository.findByStatus(PlacementTaskStatus.OPEN);
        if (open.isEmpty()) {
            return 0;
        }
        Map<String, List<PoCustomerLink>> links = linksByOrder(open.stream().map(PlacementTask::getOrderNo).toList());
        LocalDateTime now = LocalDateTime.now(clock);
        int closed = 0;
        for (PlacementTask t : open) {
            List<PoCustomerLink> found = links.getOrDefault(t.getOrderNo(), List.of());
            if (found.isEmpty()) {
                continue;
            }
            PoCustomerLink first = found.stream()
                    .min(Comparator.comparing(PoCustomerLink::getPoDate,
                            Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(PoCustomerLink::getPoNo))
                    .orElseThrow();
            t.setStatus(PlacementTaskStatus.DONE);
            t.setDoneAt(now);
            t.setDoneSource(PlacementTask.SOURCE_LINKED_PO);
            t.setDonePoNo(first.getPoNo());
            closed++;
        }
        taskRepository.saveAll(open);
        return closed;
    }

    /** Returns {reminders sent, overdue alerts sent}. */
    int[] remindAndEscalate() {
        LocalDateTime now = LocalDateTime.now(clock);
        List<PlacementTask> dueSoon = taskRepository.findDueSoonNotReminded(now, now.plusHours(remindHours));
        for (PlacementTask t : dueSoon) {
            t.setRemindedAt(now);
            Set<Long> to = t.getAssigneeId() != null ? Set.of(t.getAssigneeId()) : department();
            publish(NotificationType.PO_PLACEMENT_DUE, NotifactionPriority.HIGH,
                    LocalizedText.of("placement.notify.dueTitle", t.getOrderNo()),
                    LocalizedText.of("placement.notify.dueContent", t.getOrderNo(), stamp(t.getDeadlineAt())),
                    t.getId(), to);
        }
        List<PlacementTask> overdue = taskRepository.findOverdueNotNotified(now);
        for (PlacementTask t : overdue) {
            t.setOverdueNotifiedAt(now);
            Set<Long> to = new java.util.LinkedHashSet<>(department());
            if (t.getAssigneeId() != null) {
                to.add(t.getAssigneeId());
            }
            publish(NotificationType.PO_PLACEMENT_DUE, NotifactionPriority.ERGENT,
                    LocalizedText.of("placement.notify.overdueTitle", t.getOrderNo()),
                    LocalizedText.of("placement.notify.overdueContent", t.getOrderNo(), stamp(t.getDeadlineAt()),
                            t.getAssigneeId() == null ? LocalizedText.of("placement.notify.unassigned")
                                    : LocalizedText.of("placement.notify.assignedTo", name(t.getAssigneeId()))),
                    t.getId(), to);
        }
        taskRepository.saveAll(dueSoon);
        taskRepository.saveAll(overdue);
        return new int[] { dueSoon.size(), overdue.size() };
    }

    // ----- user actions -------------------------------------------------------

    /**
     * Gives the task to the caller or to another member of the caller's
     * department (re-assigning is allowed the same way).
     */
    @Transactional
    public PlacementTaskView assign(Long taskId, Long assigneeId, Long callerId) {
        PlacementTask t = openTask(taskId);
        if (assigneeId == null || !assignable(callerId).containsKey(assigneeId)) {
            throw new PlacementException(400, I18n.t("placement.error.notInDepartment"));
        }
        t.setAssigneeId(assigneeId);
        t.setAssignedBy(callerId);
        t.setAssignedAt(LocalDateTime.now(clock));
        taskRepository.save(t);
        publish(NotificationType.PO_PLACEMENT_DUE, NotifactionPriority.HIGH,
                LocalizedText.of("placement.notify.assignedTitle", t.getOrderNo()),
                LocalizedText.of("placement.notify.assignedContent", t.getOrderNo(), stamp(t.getDeadlineAt())),
                t.getId(), Set.of(assigneeId));
        return views(List.of(t)).get(0);
    }

    /**
     * Closes the task with a PO number typed by hand, for when the old ERP link
     * is missing (about 15% of PO lines have none). Allowed for the assignee and
     * the members of the assignee's department, for anyone in the department
     * while the task has no assignee, and for holders of
     * {@code data.purchaseOrder.all}.
     */
    @Transactional
    public PlacementTaskView completeManually(Long taskId, String poNo, Long userId, boolean all) {
        PlacementTask t = openTask(taskId);
        if (!all && t.getAssigneeId() != null && !departmentPeers.of(userId).contains(t.getAssigneeId())) {
            throw new PlacementException(403, I18n.t("placement.error.notYours"));
        }
        String po = poNo == null ? "" : poNo.strip();
        if (po.isEmpty() || po.length() > 50) {
            throw new PlacementException(400, I18n.t("placement.error.poNoRequired"));
        }
        t.setStatus(PlacementTaskStatus.DONE);
        t.setDoneAt(LocalDateTime.now(clock));
        t.setDoneSource(PlacementTask.SOURCE_MANUAL);
        t.setDonePoNo(po);
        t.setDoneBy(userId);
        taskRepository.save(t);
        return views(List.of(t)).get(0);
    }

    // ----- reads --------------------------------------------------------------

    /**
     * Holders of {@code data.purchaseOrder.all} see every task; everyone else
     * sees the tasks of their department (assigned to them or a colleague) and
     * the tasks nobody has taken yet.
     */
    @Transactional(readOnly = true)
    public Page<PlacementTaskView> list(Long userId, boolean all, boolean openOnly, Pageable pageable) {
        List<PlacementTaskStatus> statuses = openOnly ? OPEN_ONLY : OPEN_AND_DONE;
        Page<PlacementTask> page = all ? taskRepository.findByStatusIn(statuses, pageable)
                : taskRepository.findForDepartment(departmentPeers.of(userId), statuses, pageable);
        List<PlacementTaskView> views = views(page.getContent());
        return page.map(t -> views.get(page.getContent().indexOf(t)));
    }

    /**
     * Users the caller can give a task to (themselves and the active members of
     * their departments), id -> display name.
     */
    @Transactional(readOnly = true)
    public Map<Long, String> assignable(Long callerId) {
        Set<Long> ids = departmentPeers.of(callerId);
        Map<Long, String> out = new LinkedHashMap<>();
        userRepository.findAllById(ids).stream()
                .filter(u -> Boolean.TRUE.equals(u.getStatus()))
                .sorted(Comparator.comparing(PlacementTaskService::displayName))
                .forEach(u -> out.put(u.getId(), displayName(u)));
        return out;
    }

    List<PlacementTaskView> views(List<PlacementTask> tasks) {
        if (tasks.isEmpty()) {
            return List.of();
        }
        List<String> orderNos = tasks.stream().map(PlacementTask::getOrderNo).distinct().toList();
        Map<String, List<PoCustomerLink>> links = linksByOrder(orderNos);
        Map<String, CustomerOrder> orders = customerOrderRepository.findByOrderNoIn(orderNos).stream()
                .collect(Collectors.toMap(CustomerOrder::getOrderNo, o -> o, (a, b) -> a));
        Map<Long, String> names = new LinkedHashMap<>();
        userRepository.findAllById(tasks.stream().map(PlacementTask::getAssigneeId)
                .filter(java.util.Objects::nonNull).distinct().toList())
                .forEach(u -> names.put(u.getId(), displayName(u)));
        LocalDateTime now = LocalDateTime.now(clock);
        List<PlacementTaskView> out = new ArrayList<>();
        for (PlacementTask t : tasks) {
            List<PoCustomerLink> l = links.getOrDefault(t.getOrderNo(), List.of());
            CustomerOrder o = orders.get(t.getOrderNo());
            out.add(new PlacementTaskView(t.getId(), t.getOrderNo(), o == null ? null : o.getCustomerName(),
                    t.getAuditRound(), t.getAuditVerifiedAt(), t.getDeadlineAt(),
                    Duration.between(now, t.getDeadlineAt()).toHours(), t.getStatus(), t.getAssigneeId(),
                    names.get(t.getAssigneeId()), t.getAssignedAt(), t.getDoneAt(), t.getDoneSource(),
                    t.getDonePoNo(),
                    l.stream().map(PoCustomerLink::getPoNo).distinct().sorted().toList(),
                    (int) l.stream().map(PoCustomerLink::getCustomerLineLegacyId).distinct().count(),
                    o == null ? null : o.getLineCount()));
        }
        return out;
    }

    // ----- helpers ------------------------------------------------------------

    private PlacementTask openTask(Long id) {
        PlacementTask t = taskRepository.findById(id)
                .orElseThrow(() -> new PlacementException(404, I18n.t("placement.error.notFound")));
        if (t.getStatus() != PlacementTaskStatus.OPEN) {
            throw new PlacementException(409, I18n.t("placement.error.notOpen"));
        }
        return t;
    }

    private Map<String, List<PoCustomerLink>> linksByOrder(List<String> orderNos) {
        return linkRepository.findByCustomerOrderNoIn(orderNos).stream()
                .collect(Collectors.groupingBy(PoCustomerLink::getCustomerOrderNo));
    }

    /** Time of the last PASS/FAIL verification record, in the business zone. */
    private Optional<LocalDateTime> verifiedAt(AuditTypes.Task doc, AuditTaskEntity e) {
        List<AuditTypes.Submission> subs = doc.submissions == null ? List.of() : doc.submissions;
        for (int s = subs.size() - 1; s >= 0; s--) {
            List<AuditTypes.Verification> records = subs.get(s).verificationRecords;
            if (records == null) {
                continue;
            }
            for (int i = records.size() - 1; i >= 0; i--) {
                AuditTypes.Verification v = records.get(i);
                if (v.verifiedAt() != null && v.result() != null && v.result().equals(doc.verificationResult)) {
                    return Optional.of(LocalDateTime.ofInstant(v.verifiedAt(), clock.getZone()));
                }
            }
        }
        if (e.completedAt != null) {
            return Optional.of(LocalDateTime.ofInstant(e.completedAt, clock.getZone()));
        }
        log.warn("Audit task {} is VERIFIED but has no verification time; using now", e.id);
        return Optional.empty();
    }

    /** Every member of the purchasing department (role {@value #PURCHASER_ROLE}). */
    private Set<Long> department() {
        return recipientResolver.usersWithRoles(List.of(PURCHASER_ROLE));
    }

    private void notifyDepartment(NotificationType type, NotifactionPriority priority, LocalizedText title,
            LocalizedText content, Long bizId) {
        publish(type, priority, title, content, bizId, department());
    }

    private void publish(NotificationType type, NotifactionPriority priority, LocalizedText title,
            LocalizedText content, Long bizId, Set<Long> to) {
        if (!to.isEmpty()) {
            events.publishEvent(NotificationRequestedEvent.localized(type, priority, title, content, BIZ_TYPE,
                    bizId, to));
        }
    }

    private String name(Long userId) {
        return userRepository.findById(userId).map(PlacementTaskService::displayName).orElse(String.valueOf(userId));
    }

    private static String displayName(User u) {
        return u.getDisplayName() == null || u.getDisplayName().isBlank() ? u.getUsername() : u.getDisplayName();
    }

    /** "2026-11-22 14:30": language-neutral, so it can be stored as an argument. */
    private static String stamp(LocalDateTime t) {
        return t.toString().replace('T', ' ').substring(0, 16);
    }
}
