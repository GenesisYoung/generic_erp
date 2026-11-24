package com.gsgd.generic_erp.service.purchase;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gsgd.generic_erp.configuration.i18n.I18n;
import com.gsgd.generic_erp.dto.purchase.PlanGroupOverview;
import com.gsgd.generic_erp.dto.purchase.PlanGroupRequest;
import com.gsgd.generic_erp.dto.purchase.PlanTypeRequest;
import com.gsgd.generic_erp.entity.purchase.FollowUpPlanGroup;
import com.gsgd.generic_erp.entity.purchase.FollowUpPlanType;
import com.gsgd.generic_erp.entity.purchase.FollowUpRule;
import com.gsgd.generic_erp.repository.purchase.FollowUpPlanGroupRepository;
import com.gsgd.generic_erp.repository.purchase.FollowUpPlanTypeRepository;
import com.gsgd.generic_erp.repository.purchase.FollowUpRuleRepository;
import com.gsgd.generic_erp.repository.purchase.FollowUpTrackRepository;
import com.gsgd.generic_erp.repository.purchase.PurchaseOrderLineRepository;
import com.gsgd.generic_erp.service.purchase.FollowUpRecordService.InvalidFollowUpException;
import com.gsgd.generic_erp.service.purchase.FollowUpTrackService.RefreshResult;
import com.gsgd.generic_erp.service.purchase.legacy.LegacyPurchaseOrderLineReader;

import lombok.RequiredArgsConstructor;

/**
 * Plan groups (跟进计划分组): which purchase plan types count as the same plan,
 * and whether a group is followed up as its own task. Changes apply to new
 * and changed orders on the next sync; {@link #applyToOpenOrders()} applies
 * them to every open order now.
 */
@Service
@RequiredArgsConstructor
public class FollowUpPlanGroupService {

    static final int NAME_MAX = 100;
    static final int NOTE_MAX = 200;
    static final int CODE_MAX = 50;
    static final int TYPE_NAME_MAX = 100;

    private final FollowUpPlanGroupRepository groupRepository;
    private final FollowUpPlanTypeRepository typeRepository;
    private final FollowUpRuleRepository ruleRepository;
    private final FollowUpTrackRepository trackRepository;
    private final PurchaseOrderLineRepository lineRepository;
    private final FollowUpTrackService trackService;
    private final ObjectProvider<LegacyPurchaseOrderLineReader> lineReader;
    private final Clock clock;

    public PlanGroupOverview overview() {
        List<FollowUpPlanGroup> groups = groupRepository.findByActiveTrueOrderByGroupNameAsc();
        List<FollowUpPlanType> types = typeRepository.findAllByOrderByTypeCodeAsc();
        Map<Long, String> templates = new HashMap<>();
        ruleRepository.findAllById(groups.stream().map(FollowUpPlanGroup::getFollowupRuleId)
                .filter(Objects::nonNull).distinct().toList())
                .stream().filter(FollowUpRule::isActive).forEach(r -> templates.put(r.getId(), r.getRuleName()));
        Map<Long, Long> open = new HashMap<>();
        for (Object[] r : trackRepository.countActiveByGroup(FollowUpTrackService.ACTIVE)) {
            open.put((Long) r[0], (Long) r[1]);
        }
        Map<String, Long> lines = new HashMap<>();
        for (Object[] r : lineRepository.countByPlanType()) {
            lines.put((String) r[0], (Long) r[1]);
        }
        Map<Long, List<String>> codesByGroup = types.stream().filter(t -> t.getGroupId() != null)
                .collect(Collectors.groupingBy(FollowUpPlanType::getGroupId,
                        Collectors.mapping(FollowUpPlanType::getTypeCode, Collectors.toList())));
        return new PlanGroupOverview(
                groups.stream().map(g -> {
                    Long templateId = templates.containsKey(g.getFollowupRuleId()) ? g.getFollowupRuleId() : null;
                    return new PlanGroupOverview.Group(g.getId(), g.getGroupName(), g.isSeparate(), templateId,
                            templateId == null ? null : templates.get(templateId), g.getNote(),
                            codesByGroup.getOrDefault(g.getId(), List.of()), open.getOrDefault(g.getId(), 0L));
                }).toList(),
                types.stream().map(t -> new PlanGroupOverview.Type(t.getId(), t.getTypeCode(), t.getTypeName(),
                        t.getGroupId(), lines.getOrDefault(t.getTypeCode(), 0L), t.getLastSeenAt())).toList(),
                lineReader.getIfAvailable() != null);
    }

    @Transactional
    public PlanGroupOverview createGroup(PlanGroupRequest req) {
        Valid v = validate(req, null);
        LocalDateTime now = LocalDateTime.now(clock);
        FollowUpPlanGroup g = groupRepository.save(FollowUpPlanGroup.builder()
                .groupName(v.name())
                .separate(v.separate())
                .followupRuleId(v.templateId())
                .note(v.note())
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build());
        assignTypes(g.getId(), req.typeCodes(), now);
        return overview();
    }

    @Transactional
    public PlanGroupOverview updateGroup(Long id, PlanGroupRequest req) {
        FollowUpPlanGroup g = activeGroup(id);
        Valid v = validate(req, id);
        LocalDateTime now = LocalDateTime.now(clock);
        g.setGroupName(v.name());
        g.setSeparate(v.separate());
        g.setFollowupRuleId(v.templateId());
        g.setNote(v.note());
        g.setUpdatedAt(now);
        groupRepository.save(g);
        if (req.typeCodes() != null) {
            typeRepository.ungroup(id);
            assignTypes(id, req.typeCodes(), now);
        }
        return overview();
    }

    /** Removes a group; its plan types go back to the main task. Open orders change on the next apply. */
    @Transactional
    public PlanGroupOverview removeGroup(Long id) {
        FollowUpPlanGroup g = activeGroup(id);
        g.setActive(false);
        g.setUpdatedAt(LocalDateTime.now(clock));
        groupRepository.save(g);
        typeRepository.ungroup(id);
        return overview();
    }

    /** Adds a plan type by hand, before the sync has seen it. */
    @Transactional
    public PlanGroupOverview addType(PlanTypeRequest req) {
        String code = req == null || req.code() == null ? "" : req.code().strip();
        if (code.isEmpty() || code.length() > CODE_MAX) {
            throw bad("followup.group.error.codeRequired", CODE_MAX);
        }
        if (typeRepository.findByTypeCode(code).isPresent()) {
            throw new InvalidFollowUpException(409, I18n.t("followup.group.error.codeExists", code));
        }
        typeRepository.save(FollowUpPlanType.builder()
                .typeCode(code)
                .typeName(typeName(req.name()))
                .groupId(groupIdOrNull(req.groupId()))
                .createdAt(LocalDateTime.now(clock))
                .build());
        return overview();
    }

    /** Renames a plan type or moves it to another group (or to none). */
    @Transactional
    public PlanGroupOverview updateType(Long id, PlanTypeRequest req) {
        FollowUpPlanType t = typeRepository.findById(id == null ? -1L : id)
                .orElseThrow(() -> new InvalidFollowUpException(404, I18n.t("followup.group.error.typeNotFound")));
        t.setTypeName(typeName(req == null ? null : req.name()));
        t.setGroupId(req == null ? null : groupIdOrNull(req.groupId()));
        typeRepository.save(t);
        return overview();
    }

    /** Re-splits every open order with the current groups. */
    @Transactional
    public RefreshResult applyToOpenOrders() {
        return trackService.refreshAllOpen();
    }

    // ----- helpers ------------------------------------------------------------

    private record Valid(String name, boolean separate, Long templateId, String note) {
    }

    private Valid validate(PlanGroupRequest req, Long selfId) {
        String name = req == null || req.name() == null ? "" : req.name().strip();
        if (name.isEmpty() || name.length() > NAME_MAX) {
            throw bad("followup.group.error.nameRequired", NAME_MAX);
        }
        boolean taken = groupRepository.findByActiveTrueOrderByGroupNameAsc().stream()
                .anyMatch(g -> g.getGroupName().equalsIgnoreCase(name) && !g.getId().equals(selfId));
        if (taken) {
            throw new InvalidFollowUpException(409, I18n.t("followup.group.error.nameExists", name));
        }
        String note = req.note() == null || req.note().isBlank() ? null : req.note().strip();
        if (note != null && note.length() > NOTE_MAX) {
            throw bad("followup.group.error.noteTooLong", NOTE_MAX);
        }
        Long templateId = req.templateId();
        if (templateId != null && ruleRepository.findById(templateId).filter(FollowUpRule::isActive).isEmpty()) {
            throw new InvalidFollowUpException(404, I18n.t("followup.template.error.notFound"));
        }
        return new Valid(name, Boolean.TRUE.equals(req.separate()), templateId, note);
    }

    /** Puts the listed types in the group; a code not known yet is added. */
    private void assignTypes(Long groupId, List<String> codes, LocalDateTime now) {
        if (codes == null) {
            return;
        }
        List<String> clean = codes.stream().filter(Objects::nonNull).map(String::strip).filter(c -> !c.isEmpty())
                .distinct().toList();
        for (String c : clean) {
            if (c.length() > CODE_MAX) {
                throw bad("followup.group.error.codeRequired", CODE_MAX);
            }
        }
        Map<String, FollowUpPlanType> known = typeRepository.findByTypeCodeIn(clean).stream()
                .collect(Collectors.toMap(FollowUpPlanType::getTypeCode, t -> t));
        typeRepository.saveAll(clean.stream().map(c -> {
            FollowUpPlanType t = known.getOrDefault(c,
                    FollowUpPlanType.builder().typeCode(c).createdAt(now).build());
            t.setGroupId(groupId);
            return t;
        }).toList());
    }

    private Long groupIdOrNull(Long groupId) {
        return groupId == null ? null : activeGroup(groupId).getId();
    }

    private FollowUpPlanGroup activeGroup(Long id) {
        return groupRepository.findById(id == null ? -1L : id).filter(FollowUpPlanGroup::isActive)
                .orElseThrow(() -> new InvalidFollowUpException(404, I18n.t("followup.group.error.notFound")));
    }

    private static String typeName(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String n = name.strip();
        if (n.length() > TYPE_NAME_MAX) {
            throw bad("followup.group.error.typeNameTooLong", TYPE_NAME_MAX);
        }
        return n;
    }

    private static InvalidFollowUpException bad(String key, Object... args) {
        return new InvalidFollowUpException(400, I18n.t(key, args));
    }
}
