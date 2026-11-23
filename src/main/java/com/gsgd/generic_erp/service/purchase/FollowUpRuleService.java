package com.gsgd.generic_erp.service.purchase;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gsgd.generic_erp.configuration.i18n.I18n;
import com.gsgd.generic_erp.dto.purchase.FollowUpTemplateRequest;
import com.gsgd.generic_erp.dto.purchase.FollowUpTemplateView;
import com.gsgd.generic_erp.entity.purchase.FollowUpCheckpoint;
import com.gsgd.generic_erp.entity.purchase.FollowUpRule;
import com.gsgd.generic_erp.entity.purchase.FollowUpRuleDay;
import com.gsgd.generic_erp.entity.purchase.FollowUpRuleStage;
import com.gsgd.generic_erp.entity.purchase.PurchaseOrder;
import com.gsgd.generic_erp.enums.CheckpointStatus;
import com.gsgd.generic_erp.enums.PurchaseOrderStatus;
import com.gsgd.generic_erp.repository.purchase.FollowUpCheckpointRepository;
import com.gsgd.generic_erp.repository.purchase.FollowUpRuleDayRepository;
import com.gsgd.generic_erp.repository.purchase.FollowUpRuleRepository;
import com.gsgd.generic_erp.repository.purchase.FollowUpRuleStageRepository;
import com.gsgd.generic_erp.service.purchase.FollowUpPlanBuilder.PlannedCheckpoint;
import com.gsgd.generic_erp.service.purchase.FollowUpPlanBuilder.StageDef;
import com.gsgd.generic_erp.service.purchase.FollowUpRecordService.InvalidFollowUpException;

import lombok.RequiredArgsConstructor;

/**
 * Follow-up rules, shown to users as follow-up templates.
 * <ul>
 * <li>A <b>stage-based</b> rule (the built-in default from the 2026-11-20
 * business rules) cuts the time after the order into stages with a check
 * frequency each.</li>
 * <li>A <b>day-based</b> template has a production period and the days on
 * which to follow up; every day becomes its own follow-up task.</li>
 * </ul>
 * New purchase orders get the default template; a user can switch an order to
 * another template, which re-plans its open tasks (decided 2026-11-23).
 * If the database has no default rule yet, the built-in one is stored on
 * first use, so a fresh database works without seed SQL.
 */
@Service
@RequiredArgsConstructor
public class FollowUpRuleService {

    static final int NAME_MAX = 100;
    static final int NOTE_MAX = 200;
    static final int PRODUCTION_MAX = 365;
    static final int DAYS_MAX = 60;
    static final int OVERDUE_MAX = 30;
    private static final List<PurchaseOrderStatus> ACTIVE = List.of(PurchaseOrderStatus.PLACED,
            PurchaseOrderStatus.PARTIALLY_RECEIVED);

    private final FollowUpRuleRepository ruleRepository;
    private final FollowUpRuleStageRepository stageRepository;
    private final FollowUpRuleDayRepository dayRepository;
    private final FollowUpCheckpointRepository checkpointRepository;
    private final PurchaseOrderQueryService queryService;
    private final com.gsgd.generic_erp.repository.purchase.PurchaseOrderRepository purchaseOrderRepository;
    private final Clock clock;

    // ----- planning -----------------------------------------------------------

    /** Id of the active default rule, created if missing. */
    @Transactional
    public Long defaultRuleId() {
        return ruleRepository.findFirstByDefaultRuleTrueAndActiveTrueOrderByIdAsc()
                .map(FollowUpRule::getId)
                .orElseGet(this::createDefaultRule);
    }

    /** Stages of a rule; the built-in default when the rule has no stages. */
    public List<StageDef> stagesOf(Long ruleId) {
        List<StageDef> stages = ruleId == null ? List.of()
                : stageRepository.findByRuleIdOrderBySeqAsc(ruleId).stream()
                        .map(s -> new StageDef(s.getStage(), s.getStartDay(), s.getEndDay(), s.getPeriodDays(),
                                s.getChecksPerPeriod()))
                        .toList();
        return stages.isEmpty() ? FollowUpPlanBuilder.defaultStages() : stages;
    }

    /** The follow-up tasks a rule plans for an order placed on {@code orderDate}. */
    public List<PlannedCheckpoint> plan(Long ruleId, LocalDate orderDate) {
        List<Integer> days = daysOf(ruleId);
        if (!days.isEmpty()) {
            return FollowUpPlanBuilder.buildFromDays(orderDate, days, productionDaysOf(ruleId));
        }
        return FollowUpPlanBuilder.build(orderDate, stagesOf(ruleId));
    }

    /** Last planned day of a rule; OVERDUE follow-up starts after it. */
    public int lastDay(Long ruleId) {
        List<Integer> days = daysOf(ruleId);
        return days.isEmpty() ? FollowUpPlanBuilder.lastDay(stagesOf(ruleId))
                : FollowUpPlanBuilder.lastDay(days, productionDaysOf(ruleId));
    }

    private List<Integer> daysOf(Long ruleId) {
        return ruleId == null ? List.of()
                : dayRepository.findByRuleIdOrderByDayNoAsc(ruleId).stream().map(FollowUpRuleDay::getDayNo).toList();
    }

    private Integer productionDaysOf(Long ruleId) {
        return ruleRepository.findById(ruleId).map(FollowUpRule::getProductionDays).orElse(null);
    }

    // ----- template management ------------------------------------------------

    /** Active templates, the default first. */
    @Transactional
    public List<FollowUpTemplateView> templates() {
        defaultRuleId();
        List<FollowUpRule> rules = ruleRepository.findByActiveTrueOrderByDefaultRuleDescRuleNameAsc();
        List<Long> ids = rules.stream().map(FollowUpRule::getId).toList();
        Map<Long, List<Integer>> days = dayRepository.findByRuleIdIn(ids).stream()
                .collect(Collectors.groupingBy(FollowUpRuleDay::getRuleId,
                        Collectors.mapping(FollowUpRuleDay::getDayNo, Collectors.toList())));
        Map<Long, Long> used = new HashMap<>();
        for (Object[] r : ruleRepository.countOrdersByRule(ACTIVE)) {
            used.put((Long) r[0], (Long) r[1]);
        }
        return rules.stream().map(r -> view(r, days.getOrDefault(r.getId(), List.of()), used)).toList();
    }

    @Transactional
    public FollowUpTemplateView create(FollowUpTemplateRequest req) {
        Valid v = validate(req);
        LocalDateTime now = LocalDateTime.now(clock);
        FollowUpRule rule = ruleRepository.save(FollowUpRule.builder()
                .ruleName(v.name())
                .materialNote(v.note())
                .productionDays(v.productionDays())
                .overduePeriodDays(v.overdue())
                .defaultRule(false)
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build());
        saveDays(rule.getId(), v.days());
        return view(rule, v.days(), Map.of());
    }

    /**
     * Changes a template. Orders that already use it keep their planned tasks;
     * apply the template to an order again to re-plan it.
     */
    @Transactional
    public FollowUpTemplateView update(Long id, FollowUpTemplateRequest req) {
        FollowUpRule rule = activeRule(id);
        Valid v = validate(req);
        rule.setRuleName(v.name());
        rule.setMaterialNote(v.note());
        rule.setProductionDays(v.productionDays());
        rule.setOverduePeriodDays(v.overdue());
        rule.setUpdatedAt(LocalDateTime.now(clock));
        ruleRepository.save(rule);
        dayRepository.deleteByRuleId(id);
        saveDays(id, v.days());
        return view(rule, v.days(), Map.of());
    }

    /** Makes the template the one new purchase orders get. */
    @Transactional
    public void makeDefault(Long id) {
        FollowUpRule rule = activeRule(id);
        ruleRepository.findAll().stream().filter(FollowUpRule::isDefaultRule)
                .filter(r -> !r.getId().equals(id))
                .forEach(r -> {
                    r.setDefaultRule(false);
                    ruleRepository.save(r);
                });
        rule.setDefaultRule(true);
        ruleRepository.save(rule);
    }

    /** Hides a template from the list. The default one cannot be removed. */
    @Transactional
    public void remove(Long id) {
        FollowUpRule rule = activeRule(id);
        if (rule.isDefaultRule()) {
            throw new InvalidFollowUpException(409, I18n.t("followup.template.error.removeDefault"));
        }
        rule.setActive(false);
        rule.setUpdatedAt(LocalDateTime.now(clock));
        ruleRepository.save(rule);
    }

    /**
     * Switches an order to a template and re-plans it: its OPEN tasks are
     * removed and the template's tasks that have not ended yet are added.
     * DONE and MISSED tasks stay as history.
     *
     * @return the number of tasks the order now has open; empty when the order is not visible
     */
    @Transactional
    public java.util.Optional<Integer> applyToOrder(Long userId, Long purchaseOrderId, Long templateId) {
        java.util.Optional<PurchaseOrder> found = queryService.find(userId, purchaseOrderId);
        if (found.isEmpty()) {
            return java.util.Optional.empty();
        }
        PurchaseOrder po = found.get();
        if (!po.getStatus().isActive()) {
            throw new InvalidFollowUpException(409, I18n.t("followup.error.orderInactive", po.getExternalPoNo(),
                    I18n.ref("purchase.status." + po.getStatus().name())));
        }
        FollowUpRule rule = activeRule(templateId);
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        po.setFollowupRuleId(rule.getId());
        po.setUpdatedAt(now);
        purchaseOrderRepository.save(po);
        checkpointRepository.deleteByPurchaseOrderIdAndStatus(po.getId(), CheckpointStatus.OPEN);
        int seq = checkpointRepository.maxSeq(po.getId());
        List<FollowUpCheckpoint> rows = new ArrayList<>();
        for (PlannedCheckpoint p : plan(rule.getId(), po.getOrderDate())) {
            if (p.windowEnd().isBefore(today)) {
                continue;
            }
            rows.add(FollowUpCheckpoint.builder()
                    .purchaseOrderId(po.getId())
                    .stage(p.stage())
                    .seq(++seq)
                    .windowStart(p.windowStart().isBefore(today) ? today : p.windowStart())
                    .windowEnd(p.windowEnd())
                    .status(CheckpointStatus.OPEN)
                    .createdAt(now)
                    .build());
        }
        checkpointRepository.saveAll(rows);
        return java.util.Optional.of(rows.size());
    }

    // ----- helpers ------------------------------------------------------------

    private record Valid(String name, String note, int productionDays, List<Integer> days, Integer overdue) {
    }

    private Valid validate(FollowUpTemplateRequest req) {
        if (req == null) {
            throw bad("followup.template.error.nameRequired");
        }
        String name = req.name() == null ? "" : req.name().strip();
        if (name.isEmpty() || name.length() > NAME_MAX) {
            throw bad("followup.template.error.nameRequired", NAME_MAX);
        }
        String note = req.materialNote() == null || req.materialNote().isBlank() ? null : req.materialNote().strip();
        if (note != null && note.length() > NOTE_MAX) {
            throw bad("followup.template.error.noteTooLong", NOTE_MAX);
        }
        Integer production = req.productionDays();
        if (production == null || production < 1 || production > PRODUCTION_MAX) {
            throw bad("followup.template.error.productionDays", PRODUCTION_MAX);
        }
        List<Integer> days = req.notifyDays() == null ? List.of()
                : req.notifyDays().stream().filter(Objects::nonNull).distinct().sorted().toList();
        if (days.isEmpty() || days.size() > DAYS_MAX) {
            throw bad("followup.template.error.daysCount", DAYS_MAX);
        }
        if (days.get(0) < 1 || days.get(days.size() - 1) > production) {
            throw bad("followup.template.error.dayOutOfRange", production);
        }
        Integer overdue = req.overduePeriodDays() == null ? 1 : req.overduePeriodDays();
        if (overdue < 0 || overdue > OVERDUE_MAX) {
            throw bad("followup.template.error.overdue", OVERDUE_MAX);
        }
        return new Valid(name, note, production, days, overdue == 0 ? null : overdue);
    }

    private static InvalidFollowUpException bad(String key, Object... args) {
        return new InvalidFollowUpException(400, I18n.t(key, args));
    }

    private FollowUpRule activeRule(Long id) {
        return ruleRepository.findById(id == null ? -1L : id).filter(FollowUpRule::isActive)
                .orElseThrow(() -> new InvalidFollowUpException(404, I18n.t("followup.template.error.notFound")));
    }

    private void saveDays(Long ruleId, List<Integer> days) {
        dayRepository.saveAll(days.stream()
                .map(d -> FollowUpRuleDay.builder().ruleId(ruleId).dayNo(d).build()).toList());
    }

    private FollowUpTemplateView view(FollowUpRule r, List<Integer> days, Map<Long, Long> used) {
        List<Integer> sorted = days.stream().sorted().toList();
        boolean dayBased = !sorted.isEmpty();
        List<FollowUpTemplateView.Stage> stages = dayBased ? List.of()
                : stagesOf(r.getId()).stream()
                        .map(s -> new FollowUpTemplateView.Stage(s.stage(), s.startDay(), s.endDay(), s.periodDays(),
                                s.checksPerPeriod()))
                        .toList();
        Integer production = dayBased ? r.getProductionDays() : (Integer) FollowUpPlanBuilder.lastDay(stagesOf(r.getId()));
        return new FollowUpTemplateView(r.getId(), r.getRuleName(), r.getMaterialNote(), production, sorted,
                r.getOverduePeriodDays() == null ? 0 : r.getOverduePeriodDays(), r.isDefaultRule(), dayBased, stages,
                used.getOrDefault(r.getId(), 0L));
    }

    private Long createDefaultRule() {
        FollowUpRule rule = ruleRepository.save(FollowUpRule.builder()
                .ruleName("Default: 2x/week weeks 1-2, every 2 days week 3, daily week 4")
                .defaultRule(true)
                .active(true)
                .overduePeriodDays(1)
                .createdAt(LocalDateTime.now(clock))
                .build());
        List<StageDef> defaults = FollowUpPlanBuilder.defaultStages();
        for (int i = 0; i < defaults.size(); i++) {
            StageDef d = defaults.get(i);
            stageRepository.save(FollowUpRuleStage.builder()
                    .ruleId(rule.getId())
                    .seq(i + 1)
                    .stage(d.stage())
                    .startDay(d.startDay())
                    .endDay(d.endDay())
                    .periodDays(d.periodDays())
                    .checksPerPeriod(d.checksPerPeriod())
                    .build());
        }
        return rule.getId();
    }
}
