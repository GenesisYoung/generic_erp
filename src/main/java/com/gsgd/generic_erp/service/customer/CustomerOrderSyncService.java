package com.gsgd.generic_erp.service.customer;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gsgd.generic_erp.configuration.i18n.I18n;
import com.gsgd.generic_erp.configuration.i18n.LocalizedText;
import com.gsgd.generic_erp.entity.customer.CustomerOrder;
import com.gsgd.generic_erp.entity.customer.PoCustomerLink;
import com.gsgd.generic_erp.entity.purchase.LegacySyncState;
import com.gsgd.generic_erp.enums.NotifactionPriority;
import com.gsgd.generic_erp.enums.NotificationType;
import com.gsgd.generic_erp.repository.customer.CustomerOrderRepository;
import com.gsgd.generic_erp.repository.customer.PoCustomerLinkRepository;
import com.gsgd.generic_erp.repository.purchase.LegacySyncStateRepository;
import com.gsgd.generic_erp.service.notification.NotificationRecipientResolver;
import com.gsgd.generic_erp.service.notification.NotificationRequestedEvent;
import com.gsgd.generic_erp.service.purchase.legacy.LegacyCustomerOrderReader;
import com.gsgd.generic_erp.service.purchase.legacy.LegacyCustomerOrderRow;
import com.gsgd.generic_erp.service.purchase.legacy.LegacyErpProperties;
import com.gsgd.generic_erp.service.purchase.legacy.LegacyPoLinkRow;

import lombok.RequiredArgsConstructor;

/**
 * Copies customer orders from the old ERP into legacy_customer_order_tb and
 * tells PMC about new ones.
 * <ol>
 * <li>Every run reads the orders dated in the last
 * {@code erp.legacy.customer-order.lookback-days} days. The old ERP leaves the
 * change time empty on about a quarter of its orders, so a "changed since"
 * filter would miss some; about 35 orders a month makes a full re-read
 * cheap.</li>
 * <li>Rows are matched on the old ERP row id (ERI). An id not seen before is a
 * new order; a known id is updated (order number, customer, dates, closed
 * flag).</li>
 * <li>New orders that are still open produce ONE notification per run to the
 * users with {@code notify-roles} (default pmc, the PMC department role), listing the
 * order numbers. The very first run only imports: the backlog of the last 60
 * days shows on the list page without a notification storm.</li>
 * <li>Purchase orders of the last {@code po-link-lookback-days} days are
 * linked to the customer order lines they buy for (legacy_po_customer_link_tb);
 * placement tasks close from these links.</li>
 * </ol>
 * One run is one transaction; a failure leaves nothing half-written and the
 * next run simply reads the same window again.
 */
@Service
@RequiredArgsConstructor
public class CustomerOrderSyncService {

    public static final String SYNC_KEY = "CUSTOMER_ORDER";
    static final String BIZ_TYPE = "CUSTOMER_ORDER";
    /** How many order numbers a notification lists before "and N more". */
    static final int LIST_LIMIT = 10;

    private final ObjectProvider<LegacyCustomerOrderReader> readerProvider;
    private final LegacyErpProperties props;
    private final CustomerOrderRepository orderRepository;
    private final PoCustomerLinkRepository linkRepository;
    private final LegacySyncStateRepository syncStateRepository;
    private final NotificationRecipientResolver recipientResolver;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /** What one run did. {@code status} is OK or DISABLED. */
    public record SyncResult(String status, int rowsRead, int created, int updated, int newOpen,
            boolean notified, int poLinksAdded, String message) {
        static SyncResult disabled() {
            return new SyncResult("DISABLED", 0, 0, 0, 0, false, 0, null);
        }
    }

    @Transactional
    public SyncResult syncNow() {
        LegacyCustomerOrderReader reader = readerProvider.getIfAvailable();
        if (reader == null) {
            return SyncResult.disabled();
        }
        LegacyErpProperties.CustomerOrder cfg = props.getCustomerOrder();
        LocalDateTime now = LocalDateTime.now(clock);
        Optional<LegacySyncState> previous = syncStateRepository.findById(SYNC_KEY);
        boolean firstRun = previous.isEmpty() || previous.get().getLastStatus() == null;
        LegacySyncState state = previous.orElseGet(() -> LegacySyncState.builder().syncKey(SYNC_KEY).build());

        LocalDate since = now.toLocalDate().minusDays(cfg.getLookbackDays());
        List<LegacyCustomerOrderRow> rows = reader.fetchDatedSince(since, cfg.getBatchLimit()).stream()
                .filter(r -> r.legacyId() != null && !r.legacyId().isBlank())
                .toList();
        List<String> skipped = new ArrayList<>();

        Map<String, CustomerOrder> known = orderRepository
                .findByLegacyIdIn(rows.stream().map(LegacyCustomerOrderRow::legacyId).toList()).stream()
                .collect(Collectors.toMap(CustomerOrder::getLegacyId, Function.identity()));

        int created = 0;
        int updated = 0;
        List<CustomerOrder> newOpen = new ArrayList<>();
        List<CustomerOrder> toSave = new ArrayList<>();
        for (LegacyCustomerOrderRow row : rows) {
            if (row.orderNo() == null || row.orderNo().isBlank() || row.orderDate() == null) {
                skipped.add(row.legacyId());
                continue;
            }
            CustomerOrder existing = known.get(row.legacyId());
            if (existing == null) {
                CustomerOrder order = CustomerOrder.builder().legacyId(row.legacyId()).firstSeenAt(now).build();
                copy(row, order);
                order.setLastSyncedAt(now);
                toSave.add(order);
                created++;
                if (!order.isLegacyClosed()) {
                    newOpen.add(order);
                }
            } else if (copy(row, existing)) {
                existing.setLastSyncedAt(now);
                toSave.add(existing);
                updated++;
            }
        }
        orderRepository.saveAll(toSave);

        boolean notified = false;
        if (!firstRun && !newOpen.isEmpty()) {
            Set<Long> recipients = recipientResolver.usersWithRoles(cfg.getNotifyRoles());
            if (!recipients.isEmpty()) {
                events.publishEvent(NotificationRequestedEvent.localized(
                        NotificationType.CUSTOMER_ORDER_NEW, NotifactionPriority.HIGH,
                        LocalizedText.of("customerOrder.notify.newTitle", newOpen.size()),
                        LocalizedText.of("customerOrder.notify.newContent", summary(newOpen)),
                        BIZ_TYPE, newOpen.size() == 1 ? newOpen.get(0).getId() : null, recipients));
                notified = true;
            }
        }

        int linksAdded = syncPoLinks(reader, cfg, now);

        String message = skipped.isEmpty() ? null
                : I18n.t(I18n.defaultLocale(), "customerOrder.sync.skippedRows", String.join(", ", skipped));
        state.setLastRunAt(now);
        state.setLastStatus("OK");
        state.setLastRowCount(rows.size());
        state.setLastMessage(message == null ? null : truncate(message, 1000));
        syncStateRepository.save(state);
        return new SyncResult("OK", rows.size(), created, updated, newOpen.size(), notified, linksAdded, message);
    }

    /**
     * Stores links from purchase orders of the last {@code po-link-lookback-days}
     * days to the customer order lines they buy for. Links are only added: a
     * PO that once existed for an order keeps counting as placed.
     */
    int syncPoLinks(LegacyCustomerOrderReader reader, LegacyErpProperties.CustomerOrder cfg, LocalDateTime now) {
        LocalDate since = now.toLocalDate().minusDays(cfg.getPoLinkLookbackDays());
        List<LegacyPoLinkRow> rows = reader.fetchPoLinksSince(since, cfg.getPoLinkBatchLimit()).stream()
                .filter(r -> notBlank(r.customerOrderId()) && notBlank(r.customerOrderNo())
                        && notBlank(r.customerLineId()) && notBlank(r.poNo()))
                .toList();
        if (rows.isEmpty()) {
            return 0;
        }
        Set<String> known = linkRepository
                .findByCustomerLineLegacyIdIn(rows.stream().map(LegacyPoLinkRow::customerLineId).distinct().toList())
                .stream().map(l -> l.getCustomerLineLegacyId() + "|" + l.getPoNo())
                .collect(Collectors.toSet());
        List<PoCustomerLink> added = new ArrayList<>();
        for (LegacyPoLinkRow r : rows) {
            if (known.add(r.customerLineId() + "|" + r.poNo())) {
                added.add(PoCustomerLink.builder()
                        .customerOrderLegacyId(truncate(r.customerOrderId(), 32))
                        .customerOrderNo(truncate(r.customerOrderNo(), 50))
                        .customerLineLegacyId(truncate(r.customerLineId(), 32))
                        .poNo(truncate(r.poNo(), 50))
                        .poDate(r.poDate())
                        .firstSeenAt(now)
                        .build());
            }
        }
        linkRepository.saveAll(added);
        return added.size();
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** Records a failed run; the next run reads the same window again. */
    @Transactional
    public void recordFailure(Exception e) {
        LegacySyncState state = syncStateRepository.findById(SYNC_KEY)
                .orElseGet(() -> LegacySyncState.builder().syncKey(SYNC_KEY).build());
        state.setLastRunAt(LocalDateTime.now(clock));
        state.setLastStatus("FAILED");
        state.setLastMessage(truncate(e.getClass().getSimpleName() + ": " + e.getMessage(), 1000));
        syncStateRepository.save(state);
    }

    public Optional<LegacySyncState> lastState() {
        return syncStateRepository.findById(SYNC_KEY);
    }

    /** Copies the old-ERP values; true when anything changed. */
    static boolean copy(LegacyCustomerOrderRow row, CustomerOrder o) {
        boolean changed = !Objects.equals(o.getOrderNo(), row.orderNo())
                || !Objects.equals(o.getCustomerCode(), row.customerCode())
                || !Objects.equals(o.getCustomerName(), row.customerName())
                || !Objects.equals(o.getOrderDate(), row.orderDate())
                || !Objects.equals(o.getDeliveryDate(), row.deliveryDate())
                || !Objects.equals(o.getSalesCode(), row.salesCode())
                || !Objects.equals(o.getLineCount(), row.lineCount())
                || !sameNumber(o.getTotalQty(), row.totalQty())
                || o.isLegacyAudited() != row.legacyAudited()
                || o.isLegacyClosed() != row.closed();
        o.setOrderNo(truncate(row.orderNo(), 50));
        o.setCustomerCode(row.customerCode() == null ? null : truncate(row.customerCode(), 50));
        o.setCustomerName(row.customerName() == null ? null : truncate(row.customerName(), 200));
        o.setOrderDate(row.orderDate());
        o.setDeliveryDate(row.deliveryDate());
        o.setSalesCode(row.salesCode() == null ? null : truncate(row.salesCode(), 50));
        o.setLineCount(row.lineCount());
        o.setTotalQty(row.totalQty());
        o.setLegacyAudited(row.legacyAudited());
        o.setLegacyClosed(row.closed());
        return changed;
    }

    /** "SO-1 (Acme), SO-2 (Beta) and 3 more", as keys so each reader sees their language. */
    static LocalizedText summary(List<CustomerOrder> orders) {
        LocalizedText shown = LocalizedText.join(orders.stream().limit(LIST_LIMIT)
                .map(o -> o.getCustomerName() == null || o.getCustomerName().isBlank()
                        ? LocalizedText.of("customerOrder.notify.itemNoCustomer", o.getOrderNo())
                        : LocalizedText.of("customerOrder.notify.item", o.getOrderNo(), o.getCustomerName()))
                .toList());
        int more = orders.size() - LIST_LIMIT;
        return more > 0 ? LocalizedText.of("customerOrder.notify.more", shown, more) : shown;
    }

    private static boolean sameNumber(java.math.BigDecimal a, java.math.BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
