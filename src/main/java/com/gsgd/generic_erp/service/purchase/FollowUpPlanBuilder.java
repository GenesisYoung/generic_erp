package com.gsgd.generic_erp.service.purchase;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.gsgd.generic_erp.enums.FollowUpStage;

/**
 * Turns a follow-up rule into concrete checkpoint windows. Pure Java: no
 * database, no clock, so every rule boundary can be unit tested.
 * <p>
 * Day numbering: day 1 is the calendar day after the order date. All days are
 * calendar days (decided 2026-11-20), so weekends are included in every stage.
 * <p>
 * Each stage is cut into periods of {@code periodDays}; each period is split
 * into {@code checksPerPeriod} windows as evenly as possible, shorter windows
 * first. The last period of a stage may be shorter than {@code periodDays}; it
 * then gets at most one window per day. With the default rule this gives:
 * <ul>
 * <li>INITIAL, days 1-14, 2 checks per 7 days: 1-3, 4-7, 8-10, 11-14</li>
 * <li>INTENSIFIED, days 15-21, 1 check per 2 days: 15-16, 17-18, 19-20, 21</li>
 * <li>DAILY, days 22-28, 1 check per day: 22, 23, ... 28</li>
 * </ul>
 */
public final class FollowUpPlanBuilder {

    private FollowUpPlanBuilder() {
    }

    /** One stage of a rule. Days are 1-based and inclusive. */
    public record StageDef(FollowUpStage stage, int startDay, int endDay, int periodDays, int checksPerPeriod) {
        public StageDef {
            if (startDay < 1 || endDay < startDay) {
                throw new IllegalArgumentException("Invalid stage days " + startDay + "-" + endDay);
            }
            if (periodDays < 1 || checksPerPeriod < 1) {
                throw new IllegalArgumentException("periodDays and checksPerPeriod must be at least 1");
            }
        }
    }

    /** One planned window. {@code seq} counts from 1 across the whole plan. */
    public record PlannedCheckpoint(FollowUpStage stage, int seq, LocalDate windowStart, LocalDate windowEnd) {
    }

    /** The built-in default rule (the business rules given on 2026-11-20). */
    public static List<StageDef> defaultStages() {
        return List.of(
                new StageDef(FollowUpStage.INITIAL, 1, 14, 7, 2),
                new StageDef(FollowUpStage.INTENSIFIED, 15, 21, 2, 1),
                new StageDef(FollowUpStage.DAILY, 22, 28, 1, 1));
    }

    /** All windows for an order placed on {@code orderDate}, in date order. */
    public static List<PlannedCheckpoint> build(LocalDate orderDate, List<StageDef> stages) {
        List<StageDef> sorted = stages.stream().sorted(Comparator.comparingInt(StageDef::startDay)).toList();
        List<PlannedCheckpoint> plan = new ArrayList<>();
        int lastEnd = 0;
        for (StageDef s : sorted) {
            if (s.startDay() <= lastEnd) {
                throw new IllegalArgumentException("Stages overlap at day " + s.startDay());
            }
            lastEnd = s.endDay();
            for (int periodStart = s.startDay(); periodStart <= s.endDay(); periodStart += s.periodDays()) {
                int length = Math.min(s.periodDays(), s.endDay() - periodStart + 1);
                int windows = Math.min(s.checksPerPeriod(), length);
                int from = periodStart;
                for (int i = 1; i <= windows; i++) {
                    // Boundary of window i inside the period; floor division keeps
                    // earlier windows shorter (7 days, 2 checks -> 3 + 4).
                    int to = periodStart + (length * i) / windows - 1;
                    plan.add(new PlannedCheckpoint(s.stage(), plan.size() + 1,
                            orderDate.plusDays(from), orderDate.plusDays(to)));
                    from = to + 1;
                }
            }
        }
        return plan;
    }

    /** Last planned day of the rule, used to know when OVERDUE starts. */
    public static int lastDay(List<StageDef> stages) {
        return stages.stream().mapToInt(StageDef::endDay).max().orElse(0);
    }
}
