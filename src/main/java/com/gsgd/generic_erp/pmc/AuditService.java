package com.gsgd.generic_erp.pmc;

import static com.gsgd.generic_erp.pmc.AuditException.require;
import static com.gsgd.generic_erp.pmc.AuditTypes.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.gsgd.generic_erp.pmc.AuditAccess.Actor;

@Service
@Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
public class AuditService {
    private final AuditTaskRepository tasks;
    private final AuditOperationRepository operations;
    private final AuditRuleRepository rules;
    private final AuditAccess access;
    private final AuditJson json;
    static final Set<String> STATUSES = Set.of("DRAFT", "WAITING_DATA", "TODO", "IN_PROGRESS", "BLOCKED", "SUBMITTED",
            "RETURNED", "VERIFIED", "CANCELLED");
    static final Set<String> RESULTS = Set.of("PASS", "FAIL", "NEED_PROOF", "DISPUTED");

    public AuditService(AuditTaskRepository tasks, AuditOperationRepository operations, AuditRuleRepository rules,
            AuditAccess access, AuditJson json) {
        this.tasks = tasks;
        this.operations = operations;
        this.rules = rules;
        this.access = access;
        this.json = json;
    }

    static String text(String s, int max, boolean required, String label) {
        String v = s == null ? "" : s.trim();
        require((!required || !v.isEmpty()) && v.length() <= max, 422, "INVALID_FIELD", label + "不能为空或超长");
        return v;
    }

    static void version(Long requested, Task t) {
        require(requested != null && requested == t.lockVersion, 409, "VERSION_CONFLICT", "记录已更新，请重新打开后核对");
    }

    static boolean in(Task t, String... statuses) {
        return Arrays.asList(statuses).contains(t.status);
    }

    static void past(Instant value, String label) {
        require(value == null || !value.isAfter(Instant.now()), 422, "INVALID_TIME", label + "不能为未来时间");
    }

    private RuleState ruleState() {
        return rules.findById(1).map(r -> json.read(r.document, RuleState.class)).orElseGet(RuleState::new);
    }

    private Task decode(AuditTaskEntity e) {
        return json.read(e.document, Task.class);
    }

    private Task read(String id, Actor a, boolean lock) {
        var e = (lock ? tasks.lockById(id) : tasks.findById(id))
                .orElseThrow(() -> new AuditException(404, "NOT_FOUND", "评审任务不存在或无权访问"));
        Task t = decode(e);
        access.visibleOr404(t, a);
        return t;
    }

    private Task persist(Task t) {
        var e = tasks.findById(t.id).orElseGet(AuditTaskEntity::new);
        e.id = t.id;
        e.orderNumber = t.orderNumber;
        e.round = t.round;
        e.ownerUserId = t.ownerUserId;
        e.createdBy = t.createdBy;
        e.departmentId = t.departmentId;
        e.nextOwnerUserId = t.nextOwnerUserId;
        e.status = t.status;
        e.acceptedAt = t.acceptedAt;
        e.effectiveDueAt = t.effectiveDueAt;
        e.completedAt = t.completedAt;
        t.lockVersion++;
        t.allowedActions = new ArrayList<>();
        e.document = json.write(t);
        tasks.saveAndFlush(e);
        return t;
    }

    private void history(Task t, Actor a, String action, String note, Object before, Object after) {
        t.history.add(new History(Instant.now(), a.name(), action, note, a.id(), before, after));
    }

    private TaskValues values(Task t) {
        return new TaskValues(t.status, t.ownerUserId, t.effectiveDueAt, t.verificationResult);
    }

    public Metadata metadata() {
        access.need("read");
        Actor a = access.actor();
        Rule r = ruleState().approved;
        return new Metadata(access.people(a), access.can("create"), r == null ? null : r.timezone(),
                r == null ? null : r.acceptanceRule(),
                r == null ? null : ("DELIVERY".equals(r.completionNode()) ? "合格评审成果交付" : "主管业务确认通过"), a.id(),
                access.can("rules.configure"), access.can("rules.approve"), access.can("export"));
    }

    public Task detail(String id) {
        access.need("read");
        Actor a = access.actor();
        return view(read(id, a, false), a);
    }

    public Submission submission(String id, int v) {
        Task t = detail(id);
        return t.submissions.stream().filter(s -> s.version == v).findFirst()
                .orElseThrow(() -> new AuditException(404, "NOT_FOUND", "成果版本不存在"));
    }

    private Task view(Task t, Actor a) {
        t.ownerName = t.ownerUserId == null ? null
                : access.people(a).stream().filter(p -> p.id().equals(t.ownerUserId)).map(Person::name).findFirst()
                        .orElse(t.ownerName);
        List<String> actions = new ArrayList<>();
        boolean manager = access.manages(t, a), executor = access.executes(t, a);
        if (in(t, "DRAFT", "WAITING_DATA") && access.can("update")
                && (manager || a.id().equals(t.createdBy) || a.id().equals(t.ownerUserId))) {
            actions.add("edit");
            actions.add("check-data");
        }
        if (in(t, "DRAFT", "WAITING_DATA") && manager && access.can("accept"))
            actions.add("accept");
        if (executor && access.can("execute")) {
            if (in(t, "TODO"))
                actions.add("start");
            if (in(t, "TODO", "IN_PROGRESS"))
                actions.add("block");
            if (in(t, "BLOCKED"))
                actions.add("resume");
        }
        if (executor && access.can("submit") && in(t, "TODO", "IN_PROGRESS", "BLOCKED", "RETURNED"))
            actions.add("submit");
        if (in(t, "SUBMITTED") && access.can("verify") && manager && !t.submissions.isEmpty()
                && !a.id().equals(t.submissions.get(t.submissions.size() - 1).submittedBy))
            actions.add("verify");
        if (in(t, "SUBMITTED") && manager && access.can("confirm") && !t.submissions.isEmpty()) {
            Submission latest = t.submissions.get(t.submissions.size() - 1);
            if (!a.id().equals(latest.submittedBy)
                    && latest.businessConfirmations.stream().noneMatch(c -> "APPROVED".equals(c.decision())))
                actions.add("confirm-business");
        }
        if (!in(t, "CANCELLED") && access.can("change.request") && (executor || a.id().equals(t.createdBy)))
            actions.add("request-change");
        if (manager && access.can("approve")
                && t.changeRequests.stream().anyMatch(c -> "PENDING".equals(c.status) && !a.id().equals(c.requestedBy)))
            actions.add("approve-change");
        if ((executor || a.id().equals(t.createdBy)) && (access.can("update") || access.can("submit")
                || access.can("verify") || access.can("rules.configure") || access.can("rules.approve")
                || access.can("approve") || access.can("confirm") || access.can("change.request")))
            actions.add("upload-evidence");
        t.allowedActions = actions;
        return t;
    }

    private Specification<AuditTaskEntity> filter(Query q, Actor a, Instant now) {
        require(q.page() >= 1 && Set.of(20, 50, 100).contains(q.size()), 400, "INVALID_PAGE", "页码从1开始，每页20、50或100条");
        require(q.keyword() == null || q.keyword().length() <= 100, 400, "INVALID_QUERY", "查询词过长");
        require(q.status() == null || q.status().isBlank() || STATUSES.contains(q.status()), 400, "INVALID_STATUS",
                "任务状态无效");
        require(q.dueFrom() == null || q.dueTo() == null || !q.dueFrom().isAfter(q.dueTo()), 400, "INVALID_RANGE",
                "时间区间无效");
        require(q.timing() == null || q.timing().isBlank()
                || Set.of("未受理", "逾期未结", "期限内未结", "逾期已交付", "按期已交付").contains(q.timing()), 400, "INVALID_TIMING",
                "期限筛选无效");
        return (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (!a.all()) {
                List<Predicate> scopes = new ArrayList<>(List.of(cb.equal(root.get("ownerUserId"), a.id()),
                        cb.equal(root.get("createdBy"), a.id()), cb.equal(root.get("nextOwnerUserId"), a.id())));
                if (a.team() && !a.departments().isEmpty())
                    scopes.add(root.get("departmentId").in(a.departments()));
                p.add(cb.or(scopes.toArray(Predicate[]::new)));
            }
            if (q.keyword() != null && !q.keyword().isBlank()) {
                String k = "%" + q.keyword().trim().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%")
                        .replace("_", "\\_") + "%";
                // Task number uses the same immutable UUID as the primary key.
                p.add(cb.or(cb.like(cb.lower(root.get("orderNumber")), k, '\\'),
                        cb.like(cb.lower(cb.concat("A1-", root.get("id"))), k, '\\')));
            }
            if (q.ownerUserId() != null && !q.ownerUserId().isBlank())
                p.add(cb.equal(root.get("ownerUserId"), q.ownerUserId()));
            if (q.status() != null && !q.status().isBlank())
                p.add(cb.equal(root.get("status"), q.status()));
            if (q.dueFrom() != null)
                p.add(cb.greaterThanOrEqualTo(root.get("effectiveDueAt"), q.dueFrom()));
            if (q.dueTo() != null)
                p.add(cb.lessThanOrEqualTo(root.get("effectiveDueAt"), q.dueTo()));
            if (q.timing() != null && !q.timing().isBlank()) {
                var due = root.<Instant>get("effectiveDueAt");
                var done = root.<Instant>get("completedAt");
                if (q.timing().equals("未受理"))
                    p.add(cb.isNull(root.get("acceptedAt")));
                else {
                    p.add(cb.isNotNull(root.get("acceptedAt")));
                    p.add(cb.isNotNull(due));
                    p.add(cb.notEqual(root.get("status"), "CANCELLED"));
                    switch (q.timing()) {
                    case "逾期未结" -> {
                        p.add(cb.isNull(done));
                        p.add(cb.lessThan(due, now));
                    }
                    case "期限内未结" -> {
                        p.add(cb.isNull(done));
                        p.add(cb.greaterThanOrEqualTo(due, now));
                    }
                    case "逾期已交付" -> {
                        p.add(cb.isNotNull(done));
                        p.add(cb.greaterThan(done, due));
                    }
                    case "按期已交付" -> {
                        p.add(cb.isNotNull(done));
                        p.add(cb.lessThanOrEqualTo(done, due));
                    }
                    }
                }
            }
            if (query.getResultType() != Long.class && query.getResultType() != long.class) {
                var open = cb.and(cb.isNotNull(root.get("acceptedAt")), cb.isNull(root.get("completedAt")),
                        cb.notEqual(root.get("status"), "CANCELLED"));
                query.orderBy(cb.asc(cb.selectCase().when(open, 0).otherwise(1)),
                        cb.asc(cb.selectCase().when(cb.isNull(root.get("effectiveDueAt")), 1).otherwise(0)),
                        cb.asc(root.get("effectiveDueAt")), cb.asc(root.get("id")));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
    }

    public Page list(Query q) {
        access.need("read");
        Actor a = access.actor();
        Instant now = Instant.now();
        var page = tasks.findAll(filter(q, a, now), PageRequest.of(q.page() - 1, q.size()));
        return new Page(page.getContent().stream().map(this::decode).map(t -> view(t, a)).toList(),
                page.getTotalElements(), false, now);
    }

    private <T> T once(Actor a, String key, String route, Object input, String taskId, Class<T> type,
            Supplier<T> work) {
        require(key != null && key.matches("[a-zA-Z0-9-]{16,100}"), 400, "IDEMPOTENCY_KEY_REQUIRED",
                "写请求须携带16至100位幂等键");
        access.lockActor(a);
        String hash;
        try {
            hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest((route + "\n" + json.write(input)).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        String id = a.id() + ":" + key;
        var existing = operations.findById(id);
        if (existing.isPresent()) {
            var op = existing.get();
            require(op.requestHash.equals(hash), 409, "IDEMPOTENCY_CONFLICT", "同一幂等键不可用于不同请求");
            if (op.taskId != null)
                read(op.taskId, a, false);
            return json.read(op.response, type);
        }
        T result = work.get();
        var op = new AuditOperationEntity();
        op.id = id;
        op.requestHash = hash;
        op.taskId = result instanceof Task t ? t.id : taskId;
        op.response = json.write(result);
        operations.saveAndFlush(op);
        return result;
    }

    private void validate(Draft d, Task existing, Actor a) {
        require(d != null, 400, "INVALID_BODY", "请提供草稿");
        text(d.orderNumber(), 50, true, "订单号");
        text(d.title(), 1000, false, "交付要求");
        require(d.round() != null && d.round() >= 1 && d.round() <= 1000, 422, "INVALID_ROUND", "评审轮次无效");
        require(d.checklistConfirmed() != null && d.materials() != null && d.materials().size() <= 100, 422,
                "INVALID_CHECKLIST", "必须提供资料清单，最多100项");
        if (d.ownerUserId() != null)
            access.person(d.ownerUserId(), a);
        past(d.receivedAt(), "资料接收时间");
        Set<String> ids = new HashSet<>(), names = new HashSet<>();
        for (Material m : d.materials()) {
            require(m != null, 422, "INVALID_MATERIAL", "资料项不能为空");
            text(m.id(), 100, true, "资料标识");
            text(m.name(), 200, true, "资料名称");
            require(ids.add(m.id()) && names.add(m.name().trim()), 422, "DUPLICATE_MATERIAL", "资料标识和名称不可重复");
            require(m.required() != null && m.status() != null && Set.of(1, 2, 3).contains(m.status()), 422,
                    "INVALID_MATERIAL", "资料状态无效");
            text(m.missingParts(), 4000, false, "缺项说明");
            past(m.actualAt(), "实际补齐时间");
            if (m.ownerUserId() != null)
                access.person(m.ownerUserId(), a);
            require(existing != null || m.evidenceRef() == null || !m.evidenceRef().startsWith("audit:"), 422,
                    "EVIDENCE_TASK_REQUIRED", "任务专属凭证须先建立草稿再上传，不能跨任务复用");
            if (m.status() != 1) {
                text(m.missingParts(), 4000, true, "缺项及原因");
                require(m.ownerUserId() != null && m.expectedAt() != null, 422, "MISSING_FOLLOWUP",
                        "缺项须填写补交责任人及预计补齐时间");
            } else {
                require(m.actualAt() != null, 422, "MISSING_ACTUAL_TIME", "已提交资料须填写实际补齐时间");
                access.evidence(m.evidenceRef(), existing, a);
            }
            if (m.evidenceRef() != null && !m.evidenceRef().isBlank() && m.status() != 1)
                access.evidence(m.evidenceRef(), existing, a);
        }
    }

    private void apply(Task t, Draft d, Actor a) {
        t.orderNumber = d.orderNumber().trim();
        t.round = d.round();
        t.title = text(d.title(), 1000, false, "交付要求");
        t.ownerUserId = d.ownerUserId();
        t.ownerName = d.ownerUserId() == null ? null : access.person(d.ownerUserId(), a).name();
        t.receivedAt = d.receivedAt();
        t.checklistConfirmed = Boolean.TRUE.equals(d.checklistConfirmed());
        t.materials = new ArrayList<>(d.materials().stream()
                .map(m -> new Material(m.id().trim(), m.name().trim(), m.required(), m.status(),
                        m.missingParts() == null ? "" : m.missingParts().trim(), m.ownerUserId(), m.expectedAt(),
                        m.actualAt(), m.evidenceRef() == null ? "" : m.evidenceRef().trim()))
                .toList());
        t.departmentId = access.department(t.ownerUserId, a);
    }

    public Task create(Draft d, String key) {
        access.need("create");
        Actor a = access.actor();
        return once(a, key, "create", d, null, Task.class, () -> {
            validate(d, null, a);
            require(d.lockVersion() == null, 400, "INVALID_BODY", "新建不能指定服务端版本");
            require(d.round() == 1, 422, "REVIEW_APPROVAL_REQUIRED", "新建仅允许首轮；后续轮次须由获批重评申请生成");
            require(!tasks.existsByOrderNumberAndRound(d.orderNumber().trim(), d.round()), 409, "DUPLICATE_ROUND",
                    "订单和评审轮次已存在");
            Task t = new Task();
            t.id = UUID.randomUUID().toString();
            t.taskNumber = "A1-" + t.id;
            t.createdBy = a.id();
            t.status = "DRAFT";
            apply(t, d, a);
            history(t, a, "create", "建立手工来源草稿；老ERP来源尚未核对", null, values(t));
            persist(t);
            return view(t, a);
        });
    }

    public Task update(String id, Draft d, String key) {
        access.need("update");
        Actor a = access.actor();
        return once(a, key, "update/" + id, d, id, Task.class, () -> {
            Task t = read(id, a, true);
            require(d != null, 400, "INVALID_BODY", "请提供草稿");
            version(d.lockVersion(), t);
            require(view(t, a).allowedActions.contains("edit"), 409, "ILLEGAL_STATE", "当前状态或职责不允许编辑");
            require(t.orderNumber.equals(d.orderNumber()) && Objects.equals(t.round, d.round()), 422,
                    "IMMUTABLE_SOURCE", "不能变更订单号或轮次");
            validate(d, t, a);
            Draft before = new Draft(t.orderNumber, t.round, t.title, t.ownerUserId, t.receivedAt, t.checklistConfirmed,
                    t.materials, t.lockVersion);
            apply(t, d, a);
            history(t, a, "edit", "更新评审草稿", before, d);
            persist(t);
            return view(t, a);
        });
    }

    public Task act(String id, String action, ActionInput p, String key) {
        access.need(switch (action) {
        case "check-data" -> "update";
        case "accept" -> "accept";
        case "start", "block", "resume" -> "execute";
        case "submit" -> "submit";
        case "verify" -> "verify";
        default -> throw new AuditException(400, "INVALID_ACTION", "不支持的操作");
        });
        Actor a = access.actor();
        checkActionFields(action, p);
        return once(a, key, "action/" + id + "/" + action, p, id, Task.class, () -> {
            Task t = read(id, a, true);
            require(p != null, 400, "INVALID_BODY", "请提供操作内容");
            version(p.lockVersion(), t);
            if ("verify".equals(action) && !t.submissions.isEmpty())
                require(!a.id().equals(t.submissions.get(t.submissions.size() - 1).submittedBy), 403, "SELF_VERIFY",
                        "本人不可核验本人提交的成果");
            require(view(t, a).allowedActions.contains(action), 409, "ILLEGAL_STATE", "状态或职责不允许该动作（本人不可核验本人提交的成果）");
            TaskValues before = values(t);
            Instant now = Instant.now();
            switch (action) {
            case "check-data" -> {
                validate(new Draft(t.orderNumber, t.round, t.title, t.ownerUserId, t.receivedAt, t.checklistConfirmed,
                        t.materials, t.lockVersion), t, a);
                t.status = "WAITING_DATA";
            }
            case "accept" -> {
                Rule rule = ruleState().approved;
                require(rule != null, 422, "RULE_NOT_APPROVED", "必备资料、业务时区和完成条件尚未获批");
                require(t.checklistConfirmed && !t.materials.isEmpty(), 422, "CHECKLIST_NOT_CONFIRMED", "完整资料清单尚未确认");
                for (String name : rule.requiredMaterials())
                    require(t.materials.stream().anyMatch(
                            m -> name.equals(m.name()) && Boolean.TRUE.equals(m.required()) && m.status() == 1), 422,
                            "REQUIRED_MATERIAL_MISSING", "缺少获批必备资料：" + name);
                for (Material m : t.materials)
                    if (Boolean.TRUE.equals(m.required())) {
                        require(m.status() == 1 && m.actualAt() != null, 422, "REQUIRED_MATERIAL_MISSING",
                                "必备资料未齐：" + m.name());
                        access.evidence(m.evidenceRef(), t, a);
                    }
                require(t.ownerUserId != null && t.receivedAt != null && !t.title.isBlank() && p.dueAt() != null, 422,
                        "ACCEPTANCE_INCOMPLETE", "受理须填写责任人、交付要求、接收时间及期限");
                require(!p.dueAt().isBefore(now), 422, "INVALID_DEADLINE", "首版禁止追溯受理，请批准未来的截止期限");
                text(p.basis(), 4000, true, "批准时限依据");
                t.acceptedAt = now;
                t.originalDueAt = p.dueAt();
                t.effectiveDueAt = p.dueAt();
                t.acceptanceRuleSnapshot = rule;
                t.status = "TODO";
            }
            case "start" -> t.status = "IN_PROGRESS";
            case "block" -> {
                text(p.reason(), 4000, true, "阻塞事实");
                t.nextAction = text(p.nextAction(), 1000, true, "下一动作");
                access.person(p.nextOwnerUserId(), a);
                require(p.nextFeedbackAt() != null && p.nextFeedbackAt().isAfter(now), 422, "INVALID_FEEDBACK_TIME",
                        "请填写未来的反馈时间");
                t.nextOwnerUserId = p.nextOwnerUserId();
                t.nextFeedbackAt = p.nextFeedbackAt();
                t.status = "BLOCKED";
            }
            case "resume" -> {
                text(p.reason(), 4000, true, "解决说明");
                access.evidence(p.evidenceRef(), t, a);
                t.status = "IN_PROGRESS";
                t.nextAction = null;
                t.nextOwnerUserId = null;
                t.nextFeedbackAt = null;
            }
            case "submit" -> {
                text(p.conclusion(), 10000, true, "资料与交期核验结论");
                access.evidence(p.evidenceRef(), t, a);
                require(p.completedAt() != null && !p.completedAt().isBefore(t.acceptedAt), 422,
                        "INVALID_COMPLETION_TIME", "实际完成时间不得早于受理");
                past(p.completedAt(), "实际完成时间");
                Submission s = new Submission();
                s.version = t.submissions.size() + 1;
                s.conclusion = p.conclusion().trim();
                s.evidenceRef = p.evidenceRef();
                s.reportedCompletedAt = p.completedAt();
                s.completedAt = "BUSINESS_APPROVAL".equals(t.acceptanceRuleSnapshot.completionNode()) ? null
                        : p.completedAt();
                s.submittedAt = now;
                s.submittedBy = a.id();
                s.materialsSnapshot = List.copyOf(t.materials);
                s.ruleSnapshot = t.acceptanceRuleSnapshot;
                t.submissions.add(s);
                t.completedAt = "BUSINESS_APPROVAL".equals(t.acceptanceRuleSnapshot.completionNode()) ? null
                        : p.completedAt();
                t.status = "SUBMITTED";
                t.verificationResult = null;
            }
            case "verify" -> {
                require(p.result() != null && RESULTS.contains(p.result()), 422, "INVALID_RESULT", "核验结果无效");
                text(p.reason(), 4000, true, "核验依据");
                access.evidence(p.evidenceRef(), t, a);
                Submission s = t.submissions.get(t.submissions.size() - 1);
                if ("PASS".equals(p.result()) && "BUSINESS_APPROVAL".equals(t.acceptanceRuleSnapshot.completionNode()))
                    require(t.completedAt != null, 422, "BUSINESS_CONFIRMATION_REQUIRED", "完成节点要求主管业务确认通过");
                s.verificationRecords.add(new Verification(p.result(), p.reason(), p.evidenceRef(), a.id(), now));
                t.verificationResult = p.result();
                t.status = switch (p.result()) {
                case "PASS", "FAIL" -> "VERIFIED";
                case "NEED_PROOF" -> "RETURNED";
                default -> "SUBMITTED";
                };
            }
            }
            history(t, a, action, json.write(p), before, values(t));
            persist(t);
            return view(t, a);
        });
    }

    private void checkActionFields(String action, ActionInput p) {
        require(p != null, 400, "INVALID_BODY", "请提供操作内容");
        Set<String> allowed = new HashSet<>(Set.of("lockVersion"));
        allowed.addAll(switch (action) {
        case "accept" -> Set.of("dueAt", "basis");
        case "block" -> Set.of("reason", "nextAction", "nextOwnerUserId", "nextFeedbackAt");
        case "resume" -> Set.of("reason", "evidenceRef");
        case "submit" -> Set.of("conclusion", "completedAt", "evidenceRef");
        case "verify" -> Set.of("result", "reason", "evidenceRef");
        default -> Set.of();
        });
        for (var c : ActionInput.class.getRecordComponents()) {
            Object value;
            try {
                value = c.getAccessor().invoke(p);
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
            require(value == null || allowed.contains(c.getName()), 400, "INVALID_BODY", "该动作不接受字段：" + c.getName());
        }
    }

    public RuleState getRules() {
        access.need("read");
        access.actor();
        return ruleState();
    }

    public RuleState saveRule(String ruleId, Rule input, String key) {
        access.need("rules.configure");
        Actor a = access.actor();
        return once(a, key, "rule-draft/" + ruleId, input, null, RuleState.class, () -> {
            require("default".equals(ruleId) && input != null && "default".equals(input.ruleId()), 400, "INVALID_RULE",
                    "首版仅支持default规则目录");
            AuditRuleEntity e = rules.lockCurrent().orElseGet(() -> {
                var n = new AuditRuleEntity();
                n.id = 1;
                n.document = json.write(new RuleState());
                return rules.saveAndFlush(n);
            });
            RuleState state = json.read(e.document, RuleState.class);
            require(input.ruleVersion() != null && input.ruleVersion() == state.version, 409, "VERSION_CONFLICT",
                    "规则版本已更新");
            text(input.acceptanceRule(), 4000, true, "受理条件");
            text(input.reason(), 4000, true, "变更依据");
            try {
                ZoneId.of(input.timezone());
            } catch (Exception ex) {
                throw new AuditException(422, "INVALID_TIMEZONE", "请配置有效业务时区");
            }
            require(input.completionNode() != null
                    && Set.of("DELIVERY", "BUSINESS_APPROVAL").contains(input.completionNode()), 422,
                    "INVALID_COMPLETION_NODE", "完成条件为DELIVERY或BUSINESS_APPROVAL");
            require(input.requiredMaterials() != null && !input.requiredMaterials().isEmpty()
                    && input.requiredMaterials().size() <= 100
                    && new HashSet<>(input.requiredMaterials()).size() == input.requiredMaterials().size(), 422,
                    "INVALID_RULE", "请提供不重复的必备资料清单");
            input.requiredMaterials().forEach(s -> text(s, 200, true, "必备资料名称"));
            require(input.requiredMaterials().stream().map(String::trim).distinct().count() == input.requiredMaterials()
                    .size(), 422, "INVALID_RULE", "必备资料名称不可重复");
            access.evidence(input.evidenceRef(), null, a);
            state.version++;
            state.draft = new Rule("default", state.version, input.timezone(), input.acceptanceRule().trim(),
                    input.completionNode(), input.requiredMaterials().stream().map(String::trim).toList(),
                    input.reason().trim(), input.evidenceRef());
            state.requestedBy = a.id();
            if (!state.evidenceRefs.contains(input.evidenceRef()))
                state.evidenceRefs.add(input.evidenceRef());
            state.history.add(new History(Instant.now(), a.name(), "rule-draft", input.reason(), a.id(), null, input));
            e.document = json.write(state);
            rules.saveAndFlush(e);
            return state;
        });
    }

    public RuleState approveRule(String ruleId, RuleDecision input, String key) {
        access.need("rules.approve");
        Actor a = access.actor();
        return once(a, key, "rule-approval/" + ruleId, input, null, RuleState.class, () -> {
            require("default".equals(ruleId), 400, "INVALID_RULE", "规则不存在");
            var e = rules.lockCurrent().orElseThrow(() -> new AuditException(404, "NOT_FOUND", "尚无规则草稿"));
            var state = json.read(e.document, RuleState.class);
            require(input != null && input.ruleVersion() != null && input.ruleVersion() == state.version, 409,
                    "VERSION_CONFLICT", "规则版本已更新");
            require(state.draft != null, 409, "NO_RULE_DRAFT", "尚无待批准规则");
            require(!a.id().equals(state.requestedBy), 403, "SELF_APPROVAL", "规则提交人不可自行批准");
            require(input.decision() != null && Set.of("APPROVED", "REJECTED").contains(input.decision()), 422,
                    "INVALID_DECISION", "批准结论无效");
            text(input.reason(), 4000, true, "批准依据");
            access.evidence(input.evidenceRef(), null, a);
            if ("APPROVED".equals(input.decision())) {
                Rule d = state.draft;
                state.approved = new Rule("default", state.version, d.timezone(), d.acceptanceRule(),
                        d.completionNode(), d.requiredMaterials(), d.reason(), d.evidenceRef());
                state.approvedBy = a.id();
                state.approvedAt = Instant.now();
            }
            state.history.add(new History(Instant.now(), a.name(), "rule-" + input.decision(), json.write(input),
                    a.id(), state.draft, state.approved));
            if (!state.evidenceRefs.contains(input.evidenceRef()))
                state.evidenceRefs.add(input.evidenceRef());
            state.draft = null;
            state.version++;
            e.document = json.write(state);
            rules.saveAndFlush(e);
            return state;
        });
    }

    public Task businessConfirmation(String id, int submissionVersion, String decision, String reason,
            String evidenceRef, Long lockVersion, String key) {
        access.need("confirm");
        Actor a = access.actor();
        var body = Arrays.asList(submissionVersion, decision, reason, evidenceRef, lockVersion);
        return once(a, key, "business-confirmation/" + id, body, id, Task.class, () -> {
            Task t = read(id, a, true);
            version(lockVersion, t);
            require(access.manages(t, a) && in(t, "SUBMITTED"), 403, "FORBIDDEN", "仅授权主管可确认待核验成果");
            require(!t.submissions.isEmpty()
                    && t.submissions.get(t.submissions.size() - 1).version == submissionVersion, 409,
                    "VERSION_CONFLICT", "只能确认当前成果版本");
            Submission s = t.submissions.get(t.submissions.size() - 1);
            require(s.businessConfirmations.stream().noneMatch(c -> "APPROVED".equals(c.decision())), 409,
                    "BUSINESS_ALREADY_CONFIRMED", "本版业务已确认通过，修正须先申请重开或提交新版");
            require(!a.id().equals(s.submittedBy), 403, "SELF_APPROVAL", "本人不可确认本人提交的成果");
            require("APPROVED".equals(decision) || "RETURNED".equals(decision), 422, "INVALID_DECISION", "业务确认结论无效");
            text(reason, 4000, true, "确认依据");
            access.evidence(evidenceRef, t, a);
            s.businessConfirmations.add(new BusinessConfirmation(decision, reason, evidenceRef, a.id(), Instant.now()));
            var before = values(t);
            if ("RETURNED".equals(decision)) {
                t.status = "RETURNED";
                t.verificationResult = null;
            } else if ("BUSINESS_APPROVAL".equals(t.acceptanceRuleSnapshot.completionNode())) {
                t.completedAt = Instant.now();
                s.completedAt = t.completedAt;
            }
            history(t, a, "business-" + decision, json.write(body), before, values(t));
            persist(t);
            return view(t, a);
        });
    }

    public List<Change> changes(String id) {
        return detail(id).changeRequests;
    }

    public Task requestChange(String id, ChangeInput p, String key) {
        access.need("change.request");
        Actor a = access.actor();
        return once(a, key, "request-change/" + id, p, id, Task.class, () -> {
            Task t = read(id, a, true);
            require(p != null, 400, "INVALID_BODY", "申请不能为空");
            version(p.lockVersion(), t);
            require(access.executes(t, a) || a.id().equals(t.createdBy), 403, "FORBIDDEN", "无权发起本任务变更");
            require(p.type() != null
                    && Set.of("DEADLINE", "REOPEN", "CANCEL", "MATERIAL_CORRECTION", "DECLINE", "REVIEW_AGAIN")
                            .contains(p.type()),
                    422, "INVALID_CHANGE_TYPE", "变更类型无效");
            require(!in(t, "CANCELLED"), 409, "ILLEGAL_STATE", "已撤销任务不可发起变更");
            text(p.reason(), 4000, true, "申请理由");
            access.evidence(p.evidenceRef(), t, a);
            checkChangeState(t, p.type(), p.proposedDueAt(), p.proposedChanges(), a);
            Change c = new Change();
            c.requestId = UUID.randomUUID().toString();
            c.type = p.type();
            c.reason = p.reason();
            c.evidenceRef = p.evidenceRef();
            c.requestedBy = a.id();
            c.requestedAt = Instant.now();
            c.status = "PENDING";
            c.requestVersion = 1;
            c.proposedDueAt = p.proposedDueAt();
            c.proposedChanges = p.proposedChanges();
            c.originalValues = values(t);
            c.affectedSubmissionVersion = p.affectedSubmissionVersion() == null ? null
                    : Long.valueOf(p.affectedSubmissionVersion());
            if (c.affectedSubmissionVersion != null)
                require(!t.submissions.isEmpty()
                        && c.affectedSubmissionVersion == t.submissions.get(t.submissions.size() - 1).version, 409,
                        "VERSION_CONFLICT", "成果版本已变化");
            t.changeRequests.add(c);
            history(t, a, "change-request", json.write(c), null, c.originalValues);
            persist(t);
            return view(t, a);
        });
    }

    private void checkChangeState(Task t, String type, Instant due, Draft d, Actor a) {
        require(!in(t, "CANCELLED"), 409, "ILLEGAL_STATE", "任务已撤销，不能再批准任务变更");
        switch (type) {
        case "DEADLINE" -> require(t.acceptedAt != null && !in(t, "VERIFIED", "CANCELLED") && due != null, 422,
                "INVALID_CHANGE", "仅生效未结任务可申请改期，须指定新期限");
        case "REOPEN" -> require(in(t, "VERIFIED"), 409, "ILLEGAL_STATE", "仅已核验任务可申请重开");
        case "DECLINE" -> require(t.acceptedAt == null, 409, "ILLEGAL_STATE", "仅未受理任务可不予承接");
        case "REVIEW_AGAIN" -> require(in(t, "VERIFIED"), 409, "ILLEGAL_STATE", "仅已核验任务可申请新轮次重评");
        case "MATERIAL_CORRECTION" -> {
            require(t.acceptedAt != null && !in(t, "CANCELLED", "VERIFIED"), 409, "ILLEGAL_STATE", "资料修正须针对受理后的未结任务");
            require(d != null && t.orderNumber.equals(d.orderNumber()) && Objects.equals(t.round, d.round())
                    && Objects.equals(t.receivedAt, d.receivedAt()), 422, "IMMUTABLE_SOURCE", "资料修正不能改变订单、轮次和原接收时间");
            validate(d, t, a);
        }
        }
    }

    public Task decideChange(String id, String requestId, DecisionInput p, String key) {
        access.need("approve");
        Actor a = access.actor();
        return once(a, key, "change-decision/" + id + "/" + requestId, p, id, Task.class, () -> {
            Task t = read(id, a, true);
            require(p != null, 400, "INVALID_BODY", "请提供审批内容");
            version(p.lockVersion(), t);
            require(access.manages(t, a), 403, "FORBIDDEN", "无权审批该任务");
            Change c = t.changeRequests.stream().filter(r -> r.requestId.equals(requestId)).findFirst()
                    .orElseThrow(() -> new AuditException(404, "NOT_FOUND", "申请不存在"));
            require("PENDING".equals(c.status) && p.requestVersion() != null && c.requestVersion == p.requestVersion(),
                    409, "VERSION_CONFLICT", "申请已处理或版本变化");
            require(!a.id().equals(c.requestedBy), 403, "SELF_APPROVAL", "申请人不可自行批准");
            require("APPROVED".equals(p.decision()) || "REJECTED".equals(p.decision()), 422, "INVALID_DECISION",
                    "审批结论无效");
            text(p.reason(), 4000, true, "审批依据");
            access.evidence(p.evidenceRef(), t, a);
            TaskValues before = values(t);
            Task next = null;
            if ("APPROVED".equals(p.decision())) {
                checkChangeState(t, c.type, c.proposedDueAt, c.proposedChanges, a);
                if (c.affectedSubmissionVersion != null)
                    require(!t.submissions.isEmpty()
                            && c.affectedSubmissionVersion == t.submissions.get(t.submissions.size() - 1).version, 409,
                            "VERSION_CONFLICT", "申请对应的成果已变化");
                switch (c.type) {
                case "DEADLINE" -> t.effectiveDueAt = c.proposedDueAt;
                case "REOPEN" -> {
                    t.status = "RETURNED";
                    t.verificationResult = null;
                }
                case "CANCEL", "DECLINE" -> {
                    t.status = "CANCELLED";
                    if (c.type.equals("DECLINE"))
                        t.declinedReason = c.reason;
                }
                case "MATERIAL_CORRECTION" -> {
                    apply(t, c.proposedChanges, a);
                    t.verificationResult = null;
                    t.status = "BLOCKED";
                    t.nextAction = "获批资料修正后重新核对并提交成果";
                    t.nextOwnerUserId = t.ownerUserId;
                    t.nextFeedbackAt = t.effectiveDueAt;
                }
                case "REVIEW_AGAIN" -> {
                    next = new Task();
                    next.id = UUID.randomUUID().toString();
                    next.taskNumber = "A1-" + next.id;
                    next.orderNumber = t.orderNumber;
                    next.round = t.round + 1;
                    require(!tasks.existsByOrderNumberAndRound(next.orderNumber, next.round), 409, "DUPLICATE_ROUND",
                            "下一轮评审已存在");
                    next.reviewParentTaskId = t.id;
                    t.reviewChildTaskId = next.id;
                    next.title = t.title;
                    next.ownerUserId = t.ownerUserId;
                    next.ownerName = t.ownerName;
                    next.createdBy = c.requestedBy;
                    next.departmentId = t.departmentId;
                    next.status = "DRAFT";
                    next.receivedAt = Instant.now();
                    history(next, a, "approved-review-again", "来源任务：" + t.taskNumber + "；批准申请：" + requestId, null,
                            values(next));
                    persist(next);
                }
                }
            }
            c.status = p.decision();
            c.decidedBy = a.id();
            c.decidedAt = Instant.now();
            c.decisionReason = p.reason();
            c.decisionEvidence = p.evidenceRef();
            c.requestVersion++;
            history(t, a, "change-" + c.status, json.write(c) + (next == null ? "" : "；新任务：" + next.taskNumber), before,
                    values(t));
            persist(t);
            return view(t, a);
        });
    }

    public String export(Query q) {
        access.need("export");
        Actor a = access.actor();
        Instant now = Instant.now();
        var spec = filter(
                new Query(q.keyword(), q.ownerUserId(), q.status(), q.dueFrom(), q.dueTo(), q.timing(), 1, 100), a,
                now);
        require(tasks.count(spec) <= 10000, 422, "EXPORT_TOO_LARGE", "导出最多10000条，请缩小筛选范围");
        StringBuilder csv = new StringBuilder("\uFEFF任务编号,订单号,轮次,责任人,状态,批准期限,实际完成,核验结果\r\n");
        for (var e : tasks.findAll(spec)) {
            Task t = decode(e);
            String[] fields = { t.taskNumber, t.orderNumber, String.valueOf(t.round), t.ownerName, t.status,
                    Objects.toString(t.effectiveDueAt, ""), Objects.toString(t.completedAt, ""), t.verificationResult };
            csv.append(Arrays.stream(fields).map(this::csv).reduce((x, y) -> x + "," + y).orElse("")).append("\r\n");
        }
        return csv.toString();
    }

    private String csv(String s) {
        if (s == null)
            s = "";
        if (s.stripLeading().matches("^[=+@\\-].*") || s.startsWith("\t") || s.startsWith("\r"))
            s = "'" + s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }
}
