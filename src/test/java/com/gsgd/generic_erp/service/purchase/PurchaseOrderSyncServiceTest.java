package com.gsgd.generic_erp.service.purchase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

import com.gsgd.generic_erp.entity.auth.User;
import com.gsgd.generic_erp.entity.purchase.FollowUpCheckpoint;
import com.gsgd.generic_erp.entity.purchase.LegacySyncState;
import com.gsgd.generic_erp.entity.purchase.LegacyUserMap;
import com.gsgd.generic_erp.entity.purchase.PurchaseOrder;
import com.gsgd.generic_erp.enums.CheckpointStatus;
import com.gsgd.generic_erp.enums.PurchaseOrderStatus;
import com.gsgd.generic_erp.repository.auth.UserRepository;
import com.gsgd.generic_erp.repository.purchase.FollowUpCheckpointRepository;
import com.gsgd.generic_erp.repository.purchase.LegacySyncStateRepository;
import com.gsgd.generic_erp.repository.purchase.LegacyUserMapRepository;
import com.gsgd.generic_erp.repository.purchase.PurchaseOrderRepository;
import com.gsgd.generic_erp.service.purchase.legacy.LegacyErpProperties;
import com.gsgd.generic_erp.service.purchase.legacy.LegacyPurchaseOrderReader;
import com.gsgd.generic_erp.service.purchase.legacy.LegacyPurchaseOrderRow;

class PurchaseOrderSyncServiceTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    // 2026-11-30 10:00 in Shanghai
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-30T02:00:00Z"), ZONE);

    private LegacyPurchaseOrderReader reader;
    private ObjectProvider<LegacyPurchaseOrderReader> provider;
    private LegacyErpProperties props;
    private PurchaseOrderRepository poRepo;
    private FollowUpCheckpointRepository cpRepo;
    private LegacySyncStateRepository stateRepo;
    private LegacyUserMapRepository mapRepo;
    private UserRepository userRepo;
    private FollowUpRuleService ruleService;
    private PurchaseOrderSyncService service;
    private final List<FollowUpCheckpoint> savedCheckpoints = new ArrayList<>();

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        reader = mock(LegacyPurchaseOrderReader.class);
        provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(reader);
        props = new LegacyErpProperties();
        props.getPurchaseOrder().setStatusMap(Map.of(
                "已入库", PurchaseOrderStatus.RECEIVED,
                "作废", PurchaseOrderStatus.CANCELLED));
        poRepo = mock(PurchaseOrderRepository.class);
        cpRepo = mock(FollowUpCheckpointRepository.class);
        stateRepo = mock(LegacySyncStateRepository.class);
        mapRepo = mock(LegacyUserMapRepository.class);
        userRepo = mock(UserRepository.class);
        ruleService = mock(FollowUpRuleService.class);
        when(ruleService.defaultRuleId()).thenReturn(1L);
        when(ruleService.plan(any(), any())).thenAnswer(inv -> FollowUpPlanBuilder.build(inv.getArgument(1),
                FollowUpPlanBuilder.defaultStages()));
        when(poRepo.save(any())).thenAnswer(inv -> {
            PurchaseOrder po = inv.getArgument(0);
            if (po.getId() == null) {
                po.setId(42L);
            }
            return po;
        });
        when(cpRepo.saveAll(anyIterable())).thenAnswer(inv -> {
            inv.<Iterable<FollowUpCheckpoint>>getArgument(0).forEach(savedCheckpoints::add);
            return savedCheckpoints;
        });
        when(stateRepo.findById(anyString())).thenReturn(Optional.empty());
        when(mapRepo.findBySourceSystemAndLegacyCode(anyString(), anyString())).thenReturn(Optional.empty());
        when(userRepo.findByUsername(anyString())).thenReturn(Optional.empty());
        ObjectProvider<com.gsgd.generic_erp.service.purchase.legacy.LegacyPurchaseOrderLineReader> noLines = mock(
                ObjectProvider.class);
        service = new PurchaseOrderSyncService(provider, noLines, mock(FollowUpTrackService.class), props, poRepo,
                cpRepo, stateRepo, mapRepo, userRepo, ruleService, CLOCK);
    }

    private static LegacyPurchaseOrderRow row(String no, LocalDate orderDate, String status) {
        return new LegacyPurchaseOrderRow(no, "S001", "Supplier A", "buyer1", orderDate,
                orderDate.plusDays(28), status, LocalDateTime.of(2026, 11, 30, 9, 0));
    }

    @Test
    void disabledWhenNoReader() {
        when(provider.getIfAvailable()).thenReturn(null);
        assertEquals("DISABLED", service.syncNow().status());
        verify(poRepo, never()).save(any());
    }

    @Test
    void newOrderPlacedTodayGetsAllFifteenCheckpoints() {
        when(reader.fetchChangedSince(any(), anyInt())).thenReturn(List.of(row("PO-1", LocalDate.of(2026, 11, 30), "审核")));
        when(poRepo.findBySourceSystemAndExternalPoNo("LEGACY_ERP", "PO-1")).thenReturn(Optional.empty());

        var result = service.syncNow();

        assertEquals(1, result.created());
        assertEquals(15, savedCheckpoints.size());
        assertTrue(savedCheckpoints.stream().allMatch(c -> c.getStatus() == CheckpointStatus.OPEN));
    }

    @Test
    void orderFirstSeenMidwaySkipsWindowsThatAlreadyEnded() {
        // Ordered 2026-11-20; today is 2026-11-30 = day 10, inside window 8-10.
        when(reader.fetchChangedSince(any(), anyInt())).thenReturn(List.of(row("PO-2", LocalDate.of(2026, 11, 20), "审核")));
        when(poRepo.findBySourceSystemAndExternalPoNo("LEGACY_ERP", "PO-2")).thenReturn(Optional.empty());

        service.syncNow();

        assertEquals(13, savedCheckpoints.size()); // windows 1-3 and 4-7 are gone
        assertEquals(LocalDate.of(2026, 11, 28), savedCheckpoints.get(0).getWindowStart());
        assertEquals(3, savedCheckpoints.get(0).getSeq());
    }

    @Test
    void receivedOrderCancelsOpenCheckpoints() {
        PurchaseOrder existing = PurchaseOrder.builder().id(7L).sourceSystem("LEGACY_ERP").externalPoNo("PO-3")
                .status(PurchaseOrderStatus.PLACED).orderDate(LocalDate.of(2026, 11, 20)).buyerCode("buyer1")
                .purchaserId(5L).build();
        when(reader.fetchChangedSince(any(), anyInt())).thenReturn(List.of(row("PO-3", LocalDate.of(2026, 11, 20), "已入库")));
        when(poRepo.findBySourceSystemAndExternalPoNo("LEGACY_ERP", "PO-3")).thenReturn(Optional.of(existing));

        var result = service.syncNow();

        assertEquals(1, result.closed());
        assertEquals(PurchaseOrderStatus.RECEIVED, existing.getStatus());
        verify(cpRepo).changeStatus(eq(7L), eq(List.of(CheckpointStatus.OPEN)), eq(CheckpointStatus.CANCELLED));
    }

    @Test
    void newCancelledOrderGetsNoCheckpoints() {
        when(reader.fetchChangedSince(any(), anyInt())).thenReturn(List.of(row("PO-4", LocalDate.of(2026, 11, 30), "作废")));
        when(poRepo.findBySourceSystemAndExternalPoNo("LEGACY_ERP", "PO-4")).thenReturn(Optional.empty());

        service.syncNow();

        assertTrue(savedCheckpoints.isEmpty());
    }

    @Test
    void firstRunLooksBackAndStateRemembersTheLatestChange() {
        when(reader.fetchChangedSince(any(), anyInt())).thenReturn(List.of(row("PO-5", LocalDate.of(2026, 11, 30), null)));
        when(poRepo.findBySourceSystemAndExternalPoNo(anyString(), anyString())).thenReturn(Optional.empty());

        service.syncNow();

        verify(reader).fetchChangedSince(LocalDateTime.of(2026, 10, 1, 10, 0), 5000);
        ArgumentCaptor<LegacySyncState> state = ArgumentCaptor.forClass(LegacySyncState.class);
        verify(stateRepo).save(state.capture());
        assertEquals(LocalDateTime.of(2026, 11, 30, 9, 0), state.getValue().getLastSourceUpdatedAt());
        assertEquals("OK", state.getValue().getLastStatus());
    }

    @Test
    void rowsWithoutNumberOrDateAreSkippedAndReported() {
        when(reader.fetchChangedSince(any(), anyInt())).thenReturn(List.of(row(" ", LocalDate.of(2026, 11, 30), null),
                new LegacyPurchaseOrderRow("PO-6", null, null, null, null, null, null, null)));

        var result = service.syncNow();

        assertEquals(0, result.created());
        assertTrue(result.message().contains("PO-6"));
    }

    @Test
    void purchaserComesFromTheMapThenFromTheUsername() {
        when(mapRepo.findBySourceSystemAndLegacyCode("LEGACY_ERP", "B01"))
                .thenReturn(Optional.of(LegacyUserMap.builder().userId(9L).build()));
        when(userRepo.findByUsername("zhang")).thenReturn(Optional.of(User.builder().id(3L).build()));

        assertEquals(9L, service.resolvePurchaser("B01"));
        assertEquals(3L, service.resolvePurchaser("zhang"));
        assertNull(service.resolvePurchaser("unknown"));
        assertNull(service.resolvePurchaser(null));
        verify(userRepo, never()).findByUsername("B01");
    }

    @Test
    void unlistedStatusCountsAsPlaced() {
        assertEquals(PurchaseOrderStatus.PLACED, service.mapStatus("审核"));
        assertEquals(PurchaseOrderStatus.RECEIVED, service.mapStatus(" 已入库 "));
        assertEquals(PurchaseOrderStatus.PLACED, service.mapStatus(null));
    }

    @Test
    void statusNamesFromTheDefaultQueryAreUnderstoodWithoutAMap() {
        assertEquals(PurchaseOrderStatus.CLOSED, service.mapStatus("CLOSED"));
        assertEquals(PurchaseOrderStatus.PARTIALLY_RECEIVED, service.mapStatus("PARTIALLY_RECEIVED"));
        assertEquals(PurchaseOrderStatus.RECEIVED, service.mapStatus("RECEIVED"));
        assertEquals(PurchaseOrderStatus.PLACED, service.mapStatus("something else"));
    }
}
