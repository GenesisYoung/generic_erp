package com.gsgd.generic_erp.service.purchase;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.gsgd.generic_erp.enums.FollowUpStage;
import com.gsgd.generic_erp.service.purchase.FollowUpPlanBuilder.PlannedCheckpoint;
import com.gsgd.generic_erp.service.purchase.FollowUpPlanBuilder.StageDef;

class FollowUpPlanBuilderTest {

    private static final LocalDate ORDER_DATE = LocalDate.of(2026, 11, 20); // a Friday

    private static String days(List<PlannedCheckpoint> plan, FollowUpStage stage) {
        return plan.stream().filter(c -> c.stage() == stage)
                .map(c -> (ORDER_DATE.until(c.windowStart()).getDays()) + "-"
                        + (ORDER_DATE.until(c.windowEnd()).getDays()))
                .toList().toString();
    }

    @Test
    void defaultRuleGivesFifteenCheckpoints() {
        List<PlannedCheckpoint> plan = FollowUpPlanBuilder.build(ORDER_DATE, FollowUpPlanBuilder.defaultStages());
        assertEquals(15, plan.size());
        assertEquals(1, plan.get(0).seq());
        assertEquals(15, plan.get(14).seq());
    }

    @Test
    void weeksOneAndTwoAreTwiceAWeek() {
        List<PlannedCheckpoint> plan = FollowUpPlanBuilder.build(ORDER_DATE, FollowUpPlanBuilder.defaultStages());
        assertEquals("[1-3, 4-7, 8-10, 11-14]", days(plan, FollowUpStage.INITIAL));
    }

    @Test
    void weekThreeIsEveryTwoDaysIncludingWeekends() {
        List<PlannedCheckpoint> plan = FollowUpPlanBuilder.build(ORDER_DATE, FollowUpPlanBuilder.defaultStages());
        assertEquals("[15-16, 17-18, 19-20, 21-21]", days(plan, FollowUpStage.INTENSIFIED));
    }

    @Test
    void weekFourIsDaily() {
        List<PlannedCheckpoint> plan = FollowUpPlanBuilder.build(ORDER_DATE, FollowUpPlanBuilder.defaultStages());
        assertEquals("[22-22, 23-23, 24-24, 25-25, 26-26, 27-27, 28-28]", days(plan, FollowUpStage.DAILY));
    }

    @Test
    void dayOneIsTheDayAfterTheOrderDate() {
        PlannedCheckpoint first = FollowUpPlanBuilder.build(ORDER_DATE, FollowUpPlanBuilder.defaultStages()).get(0);
        assertEquals(LocalDate.of(2026, 11, 21), first.windowStart());
        assertEquals(LocalDate.of(2026, 11, 23), first.windowEnd());
    }

    @Test
    void windowsCoverEveryDayWithoutGapsOrOverlap() {
        List<PlannedCheckpoint> plan = FollowUpPlanBuilder.build(ORDER_DATE, FollowUpPlanBuilder.defaultStages());
        for (int i = 1; i < plan.size(); i++) {
            assertEquals(plan.get(i - 1).windowEnd().plusDays(1), plan.get(i).windowStart());
        }
        assertEquals(ORDER_DATE.plusDays(28), plan.get(plan.size() - 1).windowEnd());
    }

    @Test
    void customRuleForAShortOrder() {
        // e.g. a 10-day order: every 2 days for a week, then daily.
        List<StageDef> rule = List.of(
                new StageDef(FollowUpStage.INTENSIFIED, 1, 7, 2, 1),
                new StageDef(FollowUpStage.DAILY, 8, 10, 1, 1));
        List<PlannedCheckpoint> plan = FollowUpPlanBuilder.build(ORDER_DATE, rule);
        assertEquals("[1-2, 3-4, 5-6, 7-7]", days(plan, FollowUpStage.INTENSIFIED));
        assertEquals("[8-8, 9-9, 10-10]", days(plan, FollowUpStage.DAILY));
        assertEquals(10, FollowUpPlanBuilder.lastDay(rule));
    }

    @Test
    void rejectsOverlappingOrInvalidStages() {
        assertThrows(IllegalArgumentException.class, () -> FollowUpPlanBuilder.build(ORDER_DATE, List.of(
                new StageDef(FollowUpStage.INITIAL, 1, 14, 7, 2),
                new StageDef(FollowUpStage.DAILY, 14, 20, 1, 1))));
        assertThrows(IllegalArgumentException.class, () -> new StageDef(FollowUpStage.DAILY, 5, 4, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new StageDef(FollowUpStage.DAILY, 1, 4, 0, 1));
    }

    // ----- day-based templates -----

    @Test
    void eachNotificationDayIsOneTaskRunningUntilTheNextDay() {
        LocalDate order = LocalDate.of(2026, 11, 1);
        List<PlannedCheckpoint> plan = FollowUpPlanBuilder.buildFromDays(order, List.of(10, 3, 20, 3), 30);
        assertEquals(3, plan.size());
        assertEquals(new PlannedCheckpoint(FollowUpStage.NOTIFY, 1, order.plusDays(3), order.plusDays(9)),
                plan.get(0));
        assertEquals(new PlannedCheckpoint(FollowUpStage.NOTIFY, 2, order.plusDays(10), order.plusDays(19)),
                plan.get(1));
        // The last task runs to the end of the production period.
        assertEquals(new PlannedCheckpoint(FollowUpStage.NOTIFY, 3, order.plusDays(20), order.plusDays(30)),
                plan.get(2));
    }

    @Test
    void theLastTaskIsJustItsDayWhenThePeriodIsShorterOrMissing() {
        LocalDate order = LocalDate.of(2026, 11, 1);
        List<PlannedCheckpoint> plan = FollowUpPlanBuilder.buildFromDays(order, List.of(5), null);
        assertEquals(order.plusDays(5), plan.get(0).windowStart());
        assertEquals(order.plusDays(5), plan.get(0).windowEnd());
        assertEquals(30, FollowUpPlanBuilder.lastDay(List.of(5, 12), 30));
        assertEquals(12, FollowUpPlanBuilder.lastDay(List.of(5, 12), null));
    }

    @Test
    void dayBasedPlansNeedAtLeastOneDayFromDayOne() {
        LocalDate order = LocalDate.of(2026, 11, 1);
        assertThrows(IllegalArgumentException.class, () -> FollowUpPlanBuilder.buildFromDays(order, List.of(), 10));
        assertThrows(IllegalArgumentException.class, () -> FollowUpPlanBuilder.buildFromDays(order, List.of(0), 10));
    }
}
