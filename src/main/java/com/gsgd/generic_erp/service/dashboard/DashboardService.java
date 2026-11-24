package com.gsgd.generic_erp.service.dashboard;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gsgd.generic_erp.dto.dashboard.PmcDashboard;
import com.gsgd.generic_erp.dto.dashboard.PurchasingDashboard;
import com.gsgd.generic_erp.entity.auth.User;
import com.gsgd.generic_erp.entity.purchase.LegacySyncState;
import com.gsgd.generic_erp.enums.CheckpointStatus;
import com.gsgd.generic_erp.enums.PlacementTaskStatus;
import com.gsgd.generic_erp.enums.PurchaseExceptionSeverity;
import com.gsgd.generic_erp.enums.PurchaseExceptionStatus;
import com.gsgd.generic_erp.enums.PurchaseOrderStatus;
import com.gsgd.generic_erp.repository.auth.UserRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;

/**
 * Read-only numbers for the PMC and purchasing dashboards. Everything is
 * counted in the database when asked (a few dozen small queries), so the
 * numbers are always current and there is nothing to keep in sync.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DashboardService {

    static final int DUE_SOON_HOURS = 12;
    static final int LIST_SIZE = 5;
    private static final List<String> AUDIT_CLOSED = List.of("VERIFIED", "CANCELLED");
    private static final List<PurchaseExceptionStatus> EXC_OPEN = List.of(PurchaseExceptionStatus.REPORTED,
            PurchaseExceptionStatus.ACKNOWLEDGED, PurchaseExceptionStatus.IN_PROGRESS,
            PurchaseExceptionStatus.PENDING_APPROVAL);
    private static final List<PurchaseOrderStatus> PO_ACTIVE = List.of(PurchaseOrderStatus.PLACED,
            PurchaseOrderStatus.PARTIALLY_RECEIVED);
    private static final String CUSTOMER_ORDER_SYNC_KEY = "CUSTOMER_ORDER";

    private final EntityManager em;
    private final UserRepository userRepository;
    private final Clock clock;

    // ----- PMC ----------------------------------------------------------------

    public PmcDashboard pmc() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();

        long pendingAudit = count("""
                select count(c) from CustomerOrder c where c.legacyClosed = false
                  and not exists (select 1 from AuditTaskEntity t where t.orderNumber = c.orderNo)""", Map.of());
        long newToday = count("select count(c) from CustomerOrder c where c.firstSeenAt >= :d",
                Map.of("d", today.atStartOfDay()));
        LegacySyncState sync = em.find(LegacySyncState.class, CUSTOMER_ORDER_SYNC_KEY);

        Map<String, Long> auditsByStatus = new TreeMap<>();
        for (Object[] r : rows("select t.status, count(t) from AuditTaskEntity t where t.status not in :closed"
                + " group by t.status", Map.of("closed", AUDIT_CLOSED))) {
            auditsByStatus.put((String) r[0], (Long) r[1]);
        }
        long auditsOverdue = count("select count(t) from AuditTaskEntity t where t.status not in :closed"
                + " and t.effectiveDueAt < :now", Map.of("closed", AUDIT_CLOSED,
                        "now", now.atZone(clock.getZone()).toInstant()));
        long passed7 = count("select count(p) from PlacementTask p where p.auditResult = 'PASS'"
                + " and p.auditVerifiedAt >= :since", Map.of("since", now.minusDays(7)));

        PurchasingDashboard.Placement p = placement(null, now);
        LocalDateTime since30 = now.minusDays(30);
        long done30 = count("select count(p) from PlacementTask p where p.status = :done and p.doneAt >= :since",
                Map.of("done", PlacementTaskStatus.DONE, "since", since30));
        long onTime30 = count("select count(p) from PlacementTask p where p.status = :done and p.doneAt >= :since"
                + " and p.doneAt <= p.deadlineAt", Map.of("done", PlacementTaskStatus.DONE, "since", since30));

        Map<String, Long> bySeverity = new LinkedHashMap<>();
        for (PurchaseExceptionSeverity s : PurchaseExceptionSeverity.values()) {
            bySeverity.put(s.name(), 0L);
        }
        for (Object[] r : rows("select e.severity, count(e) from PurchaseException e where e.status in :open"
                + " group by e.severity", Map.of("open", EXC_OPEN))) {
            bySeverity.put(((Enum<?>) r[0]).name(), (Long) r[1]);
        }
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (PurchaseExceptionStatus s : EXC_OPEN) {
            byStatus.put(s.name(), 0L);
        }
        for (Object[] r : rows("select e.status, count(e) from PurchaseException e where e.status in :open"
                + " group by e.status", Map.of("open", EXC_OPEN))) {
            byStatus.put(((Enum<?>) r[0]).name(), (Long) r[1]);
        }
        long excOpen = bySeverity.values().stream().mapToLong(Long::longValue).sum();
        long reported7 = count("select count(e) from PurchaseException e where e.reportedAt >= :since",
                Map.of("since", now.minusDays(7)));

        return new PmcDashboard(now,
                new PmcDashboard.CustomerOrders(pendingAudit, newToday, sync == null ? null : sync.getLastRunAt(),
                        sync == null ? null : sync.getLastStatus()),
                new PmcDashboard.Audits(auditsByStatus, auditsOverdue, passed7),
                new PmcDashboard.Placement(p.open(), p.overdue(), p.dueSoon(), p.unassigned(), done30,
                        done30 == 0 ? null : (double) onTime30 / done30),
                new PmcDashboard.Exceptions(excOpen, bySeverity, byStatus, reported7),
                overduePlacements(null, now, true),
                urgentExceptions());
    }

    // ----- purchasing ---------------------------------------------------------

    /**
     * @param userId the purchaser; ignored when {@code all} is true
     * @param all    purchasing manager: everyone's numbers plus the per-purchaser table
     */
    public PurchasingDashboard purchasing(Long userId, boolean all) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        Long scope = all ? null : userId;

        String poScope = scope == null ? "" : " and po.purchaserId = :u";
        Map<String, Object> base = scope == null ? Map.of() : Map.of("u", scope);

        long activeOrders = count("select count(po) from PurchaseOrder po where po.status in :active" + poScope,
                with(base, "active", PO_ACTIVE));
        long dueToday = count("""
                select count(c) from FollowUpCheckpoint c, PurchaseOrder po where po.id = c.purchaseOrderId
                  and c.status = :open and c.windowStart <= :d and c.windowEnd >= :d""" + poScope,
                with(with(base, "open", CheckpointStatus.OPEN), "d", today));
        long missed7 = count("""
                select count(c) from FollowUpCheckpoint c, PurchaseOrder po where po.id = c.purchaseOrderId
                  and c.status = :missed and c.windowEnd >= :since""" + poScope,
                with(with(base, "missed", CheckpointStatus.MISSED), "since", today.minusDays(7)));

        String excScope = scope == null ? ""
                : " and (e.reporterId = :u or exists (select 1 from PurchaseOrder po"
                        + " where po.id = e.purchaseOrderId and po.purchaserId = :u))";
        long excOpen = count("select count(e) from PurchaseException e where e.status in :open" + excScope,
                with(base, "open", EXC_OPEN));
        long excUrgent = count("select count(e) from PurchaseException e where e.status in :open"
                + " and e.severity = :urgent" + excScope,
                with(with(base, "open", EXC_OPEN), "urgent", PurchaseExceptionSeverity.URGENT));

        List<PurchasingDashboard.FollowUpRow> due = new ArrayList<>();
        TypedQuery<Object[]> dq = em.createQuery("""
                select po.id, po.externalPoNo, coalesce(po.supplierName, po.supplierCode), c.stage, c.seq,
                       c.windowEnd
                from FollowUpCheckpoint c, PurchaseOrder po where po.id = c.purchaseOrderId
                  and c.status = :open and c.windowStart <= :d""" + poScope + " order by c.windowEnd, po.externalPoNo",
                Object[].class);
        bind(dq, with(with(base, "open", CheckpointStatus.OPEN), "d", today));
        for (Object[] r : dq.setMaxResults(LIST_SIZE).getResultList()) {
            LocalDate end = (LocalDate) r[5];
            due.add(new PurchasingDashboard.FollowUpRow((Long) r[0], (String) r[1], (String) r[2],
                    ((Enum<?>) r[3]).name(), (Integer) r[4], end, ChronoUnit.DAYS.between(today, end)));
        }

        return new PurchasingDashboard(now, all, placement(scope, now),
                new PurchasingDashboard.FollowUps(activeOrders, dueToday, missed7),
                new PurchasingDashboard.Exceptions(excOpen, excUrgent),
                all ? byPurchaser(now) : List.of(),
                due,
                overduePlacements(scope, now, false));
    }

    // ----- shared parts -------------------------------------------------------

    private PurchasingDashboard.Placement placement(Long assignee, LocalDateTime now) {
        String s = assignee == null ? "" : " and p.assigneeId = :u";
        Map<String, Object> base = assignee == null ? Map.of("open", PlacementTaskStatus.OPEN)
                : Map.of("open", PlacementTaskStatus.OPEN, "u", assignee);
        long open = count("select count(p) from PlacementTask p where p.status = :open" + s, base);
        long overdue = count("select count(p) from PlacementTask p where p.status = :open and p.deadlineAt < :now" + s,
                with(base, "now", now));
        long dueSoon = count("select count(p) from PlacementTask p where p.status = :open and p.deadlineAt >= :now"
                + " and p.deadlineAt <= :soon" + s, with(with(base, "now", now), "soon", now.plusHours(DUE_SOON_HOURS)));
        long unassigned = assignee != null ? 0
                : count("select count(p) from PlacementTask p where p.status = :open and p.assigneeId is null", base);
        return new PurchasingDashboard.Placement(open, overdue, dueSoon, unassigned);
    }

    /** Open placement tasks, nearest deadline first; only overdue ones when {@code overdueOnly}. */
    private List<PmcDashboard.PlacementRow> overduePlacements(Long assignee, LocalDateTime now, boolean overdueOnly) {
        String jpql = "select p.id, p.orderNo, p.deadlineAt, p.assigneeId from PlacementTask p where p.status = :open"
                + (overdueOnly ? " and p.deadlineAt < :now" : "")
                + (assignee == null ? "" : " and p.assigneeId = :u")
                + " order by p.deadlineAt, p.id";
        TypedQuery<Object[]> q = em.createQuery(jpql, Object[].class);
        q.setParameter("open", PlacementTaskStatus.OPEN);
        if (overdueOnly) {
            q.setParameter("now", now);
        }
        if (assignee != null) {
            q.setParameter("u", assignee);
        }
        List<Object[]> rs = q.setMaxResults(LIST_SIZE).getResultList();
        Map<Long, String> names = names(rs.stream().map(r -> (Long) r[3]).toList());
        return rs.stream().map(r -> new PmcDashboard.PlacementRow((Long) r[0], (String) r[1],
                (LocalDateTime) r[2], names.get((Long) r[3]))).toList();
    }

    private List<PmcDashboard.ExceptionRow> urgentExceptions() {
        TypedQuery<Object[]> q = em.createQuery("""
                select e.id, e.exceptionNo, e.poNo, coalesce(e.supplierName, e.supplierCode), e.category,
                       e.severity, e.status, e.reportedAt
                from PurchaseException e where e.status in :open and e.severity in :sev
                order by e.severity desc, e.reportedAt desc""", Object[].class);
        q.setParameter("open", EXC_OPEN);
        q.setParameter("sev", List.of(PurchaseExceptionSeverity.HIGH, PurchaseExceptionSeverity.URGENT));
        return q.setMaxResults(LIST_SIZE).getResultList().stream()
                .map(r -> new PmcDashboard.ExceptionRow((Long) r[0], (String) r[1], (String) r[2], (String) r[3],
                        ((Enum<?>) r[4]).name(), ((Enum<?>) r[5]).name(), ((Enum<?>) r[6]).name(),
                        (LocalDateTime) r[7]))
                .toList();
    }

    /** One row per purchaser who has active orders or open placement tasks. */
    private List<PurchasingDashboard.PurchaserRow> byPurchaser(LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        Map<Long, long[]> m = new HashMap<>();
        java.util.function.BiConsumer<List<Object[]>, Integer> put = (rs, col) -> {
            for (Object[] r : rs) {
                if (r[0] != null) {
                    m.computeIfAbsent((Long) r[0], k -> new long[6])[col] = (Long) r[1];
                }
            }
        };
        put.accept(rows("select po.purchaserId, count(po) from PurchaseOrder po where po.status in :active"
                + " group by po.purchaserId", Map.of("active", PO_ACTIVE)), 0);
        put.accept(rows("""
                select po.purchaserId, count(c) from FollowUpCheckpoint c, PurchaseOrder po
                where po.id = c.purchaseOrderId and c.status = :open and c.windowStart <= :d and c.windowEnd >= :d
                group by po.purchaserId""", Map.of("open", CheckpointStatus.OPEN, "d", today)), 1);
        put.accept(rows("""
                select po.purchaserId, count(c) from FollowUpCheckpoint c, PurchaseOrder po
                where po.id = c.purchaseOrderId and c.status = :missed and c.windowEnd >= :since
                group by po.purchaserId""", Map.of("missed", CheckpointStatus.MISSED, "since", today.minusDays(7))), 2);
        put.accept(rows("select p.assigneeId, count(p) from PlacementTask p where p.status = :open"
                + " group by p.assigneeId", Map.of("open", PlacementTaskStatus.OPEN)), 3);
        put.accept(rows("select p.assigneeId, count(p) from PlacementTask p where p.status = :open"
                + " and p.deadlineAt < :now group by p.assigneeId",
                Map.of("open", PlacementTaskStatus.OPEN, "now", now)), 4);
        put.accept(rows("""
                select po.purchaserId, count(e) from PurchaseException e, PurchaseOrder po
                where po.id = e.purchaseOrderId and e.status in :open group by po.purchaserId""",
                Map.of("open", EXC_OPEN)), 5);
        Map<Long, String> names = names(m.keySet());
        return m.entrySet().stream()
                .map(e -> new PurchasingDashboard.PurchaserRow(e.getKey(), names.get(e.getKey()), e.getValue()[0],
                        e.getValue()[1], e.getValue()[2], e.getValue()[3], e.getValue()[4], e.getValue()[5]))
                .sorted(java.util.Comparator.comparingLong(PurchasingDashboard.PurchaserRow::overduePlacements)
                        .thenComparingLong(PurchasingDashboard.PurchaserRow::missedLast7Days)
                        .thenComparingLong(PurchasingDashboard.PurchaserRow::followUpsDueToday).reversed()
                        .thenComparing(PurchasingDashboard.PurchaserRow::purchaserId))
                .toList();
    }

    // ----- query helpers ------------------------------------------------------

    private long count(String jpql, Map<String, Object> params) {
        TypedQuery<Long> q = em.createQuery(jpql, Long.class);
        bind(q, params);
        return q.getSingleResult();
    }

    private List<Object[]> rows(String jpql, Map<String, Object> params) {
        TypedQuery<Object[]> q = em.createQuery(jpql, Object[].class);
        bind(q, params);
        return q.getResultList();
    }

    private static void bind(TypedQuery<?> q, Map<String, Object> params) {
        params.forEach(q::setParameter);
    }

    private static Map<String, Object> with(Map<String, Object> base, String key, Object value) {
        Map<String, Object> m = new HashMap<>(base);
        m.put(key, value);
        return m;
    }

    private Map<Long, String> names(Collection<Long> ids) {
        Set<Long> wanted = new LinkedHashSet<>();
        ids.stream().filter(Objects::nonNull).forEach(wanted::add);
        Map<Long, String> out = new HashMap<>();
        if (!wanted.isEmpty()) {
            for (User u : userRepository.findAllById(wanted)) {
                out.put(u.getId(), u.getDisplayName() == null || u.getDisplayName().isBlank() ? u.getUsername()
                        : u.getDisplayName());
            }
        }
        return out;
    }
}
