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
import com.gsgd.generic_erp.configuration.i18n.I18n;
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
        require((!required || !v.isEmpty()) && v.length() <= max, 422, "INVALID_FIELD", "audit.error.fieldInvalid",
                I18n.ref(label));
        return v;
    }

    static void version(Long requested, Task t) {
        require(requested != null && requested == t.lockVersion, 409, "VERSION_CONFLICT", "audit.error.versionConflict");
    }

    static boolean in(Task t, String... statuses) {
        return Arrays.asList(statuses).contains(t.status);
    }

    static void past(Instant value, String label) {
        require(value == null || !value.isAfter(Instant.now()), 422, "INVALID_TIME", "audit.error.fieldInFuture",
                I18n.ref(label));
    }

    private RuleState ruleState() {
        return rules.findById(1).map(r -> json.read(r.document, RuleState.class)).orElseGet(RuleState::new);
    }

    private Task decode(AuditTaskEntity e) {
        return json.read(e.document, Task.class);
    }

    private Task read(String id, Actor a, boolean lock) {
        var e = (lock ? tasks.lockById(id) : tasks.findById(id))
                .orElseThrow(() -> new AuditException(404, "NOT_FOUND", "audit.error.taskNotFound"));
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
                r == null ? null : I18n.t("DELIVERY".equals(r.completionNode()) ? "audit.rule.completion.delivery"
                        : "audit.rule.completion.businessApproval"), a.id(),
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
                .orElseThrow(() -> new AuditException(404, "NOT_FOUND", "audit.error.submissionNotFound"));
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
        require(q.page() >= 1 && Set.of(20, 50, 100).contains(q.size()), 400, "INVALID_PAGE", "audit.error.invalidPage");
        require(q.keyword() == null || q.keyword().length() <= 100, 400, "INVALID_QUERY", "audit.error.keywordTooLong");
        require(q.status() == null || q.status().isBlank() || STATUSES.contains(q.status()), 400, "INVALID_STATUS",
                "audit.error.invalidStatus");
        require(q.dueFrom() == null || q.dueTo() == null || !q.dueFrom().isAfter(q.dueTo()), 400, "INVALID_RANGE",
                "audit.error.invalidRange");
        require(q.timing() == null || q.timing().isBlank()
                || Set.of("未受理", "逾期未结", "期限内未结", "逾期已交付", "按期已交付").contains(q.timing()), 400, "INVALID_TIMING",
                "audit.error.invalidTiming");
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
                "audit.error.writeKeyRequired");
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
            require(op.requestHash.equals(hash), 409, "IDEMPOTENCY_CONFLICT", "audit.error.keyReused");
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
        require(d != null, 400, "INVALID_BODY", "audit.error.draftRequired");
        text(d.orderNumber(), 50, true, "audit.field.orderNumber");
        text(d.title(), 1000, false, "audit.field.deliveryRequirement");
        require(d.round() != null && d.round() >= 1 && d.round() <= 1000, 422, "INVALID_ROUND", "audit.error.invalidRound");
        require(d.checklistConfirmed() != null && d.materials() != null && d.materials().size() <= 100, 422,
                "INVALID_CHECKLIST", "audit.error.checklistRequired");
        if (d.ownerUserId() != null)
            access.person(d.ownerUserId(), a);
        past(d.receivedAt(), "audit.field.receivedAt");
        Set<String> ids = new HashSet<>(), names = new HashSet<>();
        for (Material m : d.materials()) {
            require(m != null, 422, "INVALID_MATERIAL", "audit.error.materialRequired");
            text(m.id(), 100, true, "audit.field.materialId");
            text(m.name(), 200, true, "audit.field.materialName");
            require(ids.add(m.id()) && names.add(m.name().trim()), 422, "DUPLICATE_MATERIAL", "audit.error.duplicateMaterial");
            require(m.required() != null && m.status() != null && Set.of(1, 2, 3).contains(m.status()), 422,
                    "INVALID_MATERIAL", "audit.error.invalidMaterialStatus");
            text(m.missingParts(), 4000, false, "audit.field.missingParts");
            past(m.actualAt(), "audit.field.actualAt");
            if (m.ownerUserId() != null)
                access.person(m.ownerUserId(), a);
            require(existing != null || m.evidenceRef() == null || !m.evidenceRef().startsWith("audit:"), 422,
                    "EVIDENCE_TASK_REQUIRED", "audit.error.evidenceTaskRequired");
            if (m.status() != 1) {
                text(m.missingParts(), 4000, true, "audit.field.missingPartsReason");
                require(m.ownerUserId() != null && m.expectedAt() != null, 422, "MISSING_FOLLOWUP",
                        "audit.error.missingOwnerAndEta");
            } else {
                require(m.actualAt() != null, 422, "MISSING_ACTUAL_TIME", "audit.error.actualTimeRequired");
                access.evidence(m.evidenceRef(), existing, a);
            }
            if (m.evidenceRef() != null && !m.evidenceRef().isBlank() && m.status() != 1)
                access.evidence(m.evidenceRef(), existing, a);
        }
    }

    private void apply(Task t, Draft d, Actor a) {
        t.orderNumber = d.orderNumber().trim();
        t.round = d.round();
        t.title = text(d.title(), 1000, false, "audit.field.deliveryRequirement");
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
            require(d.lockVersion() == null, 400, "INVALID_BODY", "audit.error.newWithVersion");
            require(d.round() == 1, 422, "REVIEW_APPROVAL_REQUIRED", "audit.error.firstRoundOnly");
            require(!tasks.existsByOrderNumberAndRound(d.orderNumber().trim(), d.round()), 409, "DUPLICATE_ROUND",
                    "audit.error.roundExists");
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
            require(d != null, 400, "INVALID_BODY", "audit.error.draftRequired");
            version(d.lockVersion(), t);
            require(view(t, a).allowedActions.contains("edit"), 409, "ILLEGAL_STATE", "audit.error.editNotAllowed");
            require(t.orderNumber.equals(d.orderNumber()) && Objects.equals(t.round, d.round()), 422,
                    "IMMUTABLE_SOURCE", "audit.error.immutableSource");
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
        default -> throw new AuditException(400, "INVALID_ACTION", "audit.error.unsupportedAction");
        });
        Actor a = access.actor();
        checkActionFields(action, p);
        return once(a, key, "action/" + id + "/" + action, p, id, Task.class, () -> {
            Task t = read(id, a, true);
            require(p != null, 400, "INVALID_BODY", "audit.error.actionBodyRequired");
            version(p.lockVersion(), t);
            if ("verify".equals(action) && !t.submissions.isEmpty())
                require(!a.id().equals(t.submissions.get(t.submissions.size() - 1).submittedBy), 403, "SELF_VERIFY",
                        "audit.error.selfVerify");
            require(view(t, a).allowedActions.contains(action), 409, "ILLEGAL_STATE", "audit.error.actionNotAllowed");
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
                require(rule != null, 422, "RULE_NOT_APPROVED", "audit.error.ruleNotApproved");
                require(t.checklistConfirmed && !t.materials.isEmpty(), 422, "CHECKLIST_NOT_CONFIRMED", "audit.error.checklistNotConfirmed");
                for (String name : rule.requiredMaterials())
                    require(t.materials.stream().anyMatch(
                            m -> name.equals(m.name()) && Boolean.TRUE.equals(m.required()) && m.status() == 1), 422,
                            "REQUIRED_MATERIAL_MISSING", "audit.error.requiredMaterialMissing", name);
                for (Material m : t.materials)
                    if (Boolean.TRUE.equals(m.required())) {
                        require(m.status() == 1 && m.actualAt() != null, 422, "REQUIRED_MATERIAL_MISSING",
                                "audit.error.requiredMaterialIncomplete", m.name());
                        access.evidence(m.evidenceRef(), t, a);
                    }
                require(t.ownerUserId != null && t.receivedAt != null && !t.title.isBlank() && p.dueAt() != null, 422,
                        "ACCEPTANCE_INCOMPLETE", "audit.error.acceptanceIncomplete");
                require(!p.dueAt().isBefore(now), 422, "INVALID_DEADLINE", "audit.error.deadlineInPast");
                text(p.basis(), 4000, true, "audit.field.deadlineBasis");
                t.acceptedAt = now;
                t.originalDueAt = p.dueAt();
                t.effectiveDueAt = p.dueAt();
                t.acceptanceRuleSnapshot = rule;
                t.status = "TODO";
            }
            case "start" -> t.status = "IN_PROGRESS";
            case "block" -> {
                text(p.reason(), 4000, true, "audit.field.blockReason");
                t.nextAction = text(p.nextAction(), 1000, true, "audit.field.nextAction");
                access.person(p.nextOwnerUserId(), a);
                require(p.nextFeedbackAt() != null && p.nextFeedbackAt().isAfter(now), 422, "INVALID_FEEDBACK_TIME",
                        "audit.error.feedbackInFuture");
                t.nextOwnerUserId = p.nextOwnerUserId();
                t.nextFeedbackAt = p.nextFeedbackAt();
                t.status = "BLOCKED";
            }
            case "resume" -> {
                text(p.reason(), 4000, true, "audit.field.resolution");
                access.evidence(p.evidenceRef(), t, a);
                t.status = "IN_PROGRESS";
                t.nextAction = null;
                t.nextOwnerUserId = null;
                t.nextFeedbackAt = null;
            }
            case "submit" -> {
                text(p.conclusion(), 10000, true, "audit.field.conclusion");
                access.evidence(p.evidenceRef(), t, a);
                require(p.completedAt() != null && !p.completedAt().isBefore(t.acceptedAt), 422,
                        "INVALID_COMPLETION_TIME", "audit.error.completedBeforeAccepted");
                past(p.completedAt(), "audit.field.completedAt");
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
                require(p.result() != null && RESULTS.contains(p.result()), 422, "INVALID_RESULT", "audit.error.invalidResult");
                text(p.reason(), 4000, true, "audit.field.verificationBasis");
                access.evidence(p.evidenceRef(), t, a);
                Submission s = t.submissions.get(t.submissions.size() - 1);
                if ("PASS".equals(p.result()) && "BUSINESS_APPROVAL".equals(t.acceptanceRuleSnapshot.completionNode()))
                    require(t.completedAt != null, 422, "BUSINESS_CONFIRMATION_REQUIRED", "audit.error.businessConfirmationRequired");
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
        require(p != null, 400, "INVALID_BODY", "audit.error.actionBodyRequired");
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
            require(value == null || allowed.contains(c.getName()), 400, "INVALID_BODY", "audit.error.fieldNotAccepted",
                    c.getName());
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
                    "audit.error.defaultRuleOnly");
            AuditRuleEntity e = rules.lockCurrent().orElseGet(() -> {
                var n = new AuditRuleEntity();
                n.id = 1;
                n.document = json.write(new RuleState());
                return rules.saveAndFlush(n);
            });
            RuleState state = json.read(e.document, RuleState.class);
            require(input.ruleVersion() != null && input.ruleVersion() == state.version, 409, "VERSION_CONFLICT",
                    "audit.error.ruleVersionChanged");
            text(input.acceptanceRule(), 4000, true, "audit.field.acceptanceRule");
            text(input.reason(), 4000, true, "audit.field.changeBasis");
            try {
                ZoneId.of(input.timezone());
            } catch (Exception ex) {
                throw new AuditException(422, "INVALID_TIMEZONE", "audit.error.invalidTimezone");
            }
            require(input.completionNode() != null
                    && Set.of("DELIVERY", "BUSINESS_APPROVAL").contains(input.completionNode()), 422,
                    "INVALID_COMPLETION_NODE", "audit.error.invalidCompletionNode");
            require(input.requiredMaterials() != null && !input.requiredMaterials().isEmpty()
                    && input.requiredMaterials().size() <= 100
                    && new HashSet<>(input.requiredMaterials()).size() == input.requiredMaterials().size(), 422,
                    "INVALID_RULE", "audit.error.requiredMaterialsList");
            input.requiredMaterials().forEach(s -> text(s, 200, true, "audit.field.requiredMaterialName"));
            require(input.requiredMaterials().stream().map(String::trim).distinct().count() == input.requiredMaterials()
                    .size(), 422, "INVALID_RULE", "audit.error.duplicateRequiredMaterial");
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
            require("default".equals(ruleId), 400, "INVALID_RULE", "audit.error.ruleNotFound");
            var e = rules.lockCurrent().orElseThrow(() -> new AuditException(404, "NOT_FOUND", "audit.error.noRuleDraftFound"));
            var state = json.read(e.document, RuleState.class);
            require(input != null && input.ruleVersion() != null && input.ruleVersion() == state.version, 409,
                    "VERSION_CONFLICT", "audit.error.ruleVersionChanged");
            require(state.draft != null, 409, "NO_RULE_DRAFT", "audit.error.noRuleDraft");
            require(!a.id().equals(state.requestedBy), 403, "SELF_APPROVAL", "audit.error.ruleSelfApproval");
            require(input.decision() != null && Set.of("APPROVED", "REJECTED").contains(input.decision()), 422,
                    "INVALID_DECISION", "audit.error.invalidApproval");
            text(input.reason(), 4000, true, "audit.field.approvalBasis");
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
            require(access.manages(t, a) && in(t, "SUBMITTED"), 403, "FORBIDDEN", "audit.error.managerOnlyConfirm");
            require(!t.submissions.isEmpty()
                    && t.submissions.get(t.submissions.size() - 1).version == submissionVersion, 409,
                    "VERSION_CONFLICT", "audit.error.currentVersionOnly");
            Submission s = t.submissions.get(t.submissions.size() - 1);
            require(s.businessConfirmations.stream().noneMatch(c -> "APPROVED".equals(c.decision())), 409,
                    "BUSINESS_ALREADY_CONFIRMED", "audit.error.alreadyConfirmed");
            require(!a.id().equals(s.submittedBy), 403, "SELF_APPROVAL", "audit.error.selfConfirm");
            require("APPROVED".equals(decision) || "RETURNED".equals(decision), 422, "INVALID_DECISION", "audit.error.invalidConfirmation");
            text(reason, 4000, true, "audit.field.confirmationBasis");
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
            require(p != null, 400, "INVALID_BODY", "audit.error.requestRequired");
            version(p.lockVersion(), t);
            require(access.executes(t, a) || a.id().equals(t.createdBy), 403, "FORBIDDEN", "audit.error.cannotRequestChange");
            require(p.type() != null
                    && Set.of("DEADLINE", "REOPEN", "CANCEL", "MATERIAL_CORRECTION", "DECLINE", "REVIEW_AGAIN")
                            .contains(p.type()),
                    422, "INVALID_CHANGE_TYPE", "audit.error.invalidChangeType");
            require(!in(t, "CANCELLED"), 409, "ILLEGAL_STATE", "audit.error.cancelledNoChange");
            text(p.reason(), 4000, true, "audit.field.requestReason");
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
                        "VERSION_CONFLICT", "audit.error.submissionChanged");
            t.changeRequests.add(c);
            history(t, a, "change-request", json.write(c), null, c.originalValues);
            persist(t);
            return view(t, a);
        });
    }

    private void checkChangeState(Task t, String type, Instant due, Draft d, Actor a) {
        require(!in(t, "CANCELLED"), 409, "ILLEGAL_STATE", "audit.error.cancelledNoApproval");
        switch (type) {
        case "DEADLINE" -> require(t.acceptedAt != null && !in(t, "VERIFIED", "CANCELLED") && due != null, 422,
                "INVALID_CHANGE", "audit.error.invalidReschedule");
        case "REOPEN" -> require(in(t, "VERIFIED"), 409, "ILLEGAL_STATE", "audit.error.reopenVerifiedOnly");
        case "DECLINE" -> require(t.acceptedAt == null, 409, "ILLEGAL_STATE", "audit.error.declineUnacceptedOnly");
        case "REVIEW_AGAIN" -> require(in(t, "VERIFIED"), 409, "ILLEGAL_STATE", "audit.error.reviewAgainVerifiedOnly");
        case "MATERIAL_CORRECTION" -> {
            require(t.acceptedAt != null && !in(t, "CANCELLED", "VERIFIED"), 409, "ILLEGAL_STATE", "audit.error.correctionOpenOnly");
            require(d != null && t.orderNumber.equals(d.orderNumber()) && Objects.equals(t.round, d.round())
                    && Objects.equals(t.receivedAt, d.receivedAt()), 422, "IMMUTABLE_SOURCE", "audit.error.correctionImmutable");
            validate(d, t, a);
        }
        }
    }

    public Task decideChange(String id, String requestId, DecisionInput p, String key) {
        access.need("approve");
        Actor a = access.actor();
        return once(a, key, "change-decision/" + id + "/" + requestId, p, id, Task.class, () -> {
            Task t = read(id, a, true);
            require(p != null, 400, "INVALID_BODY", "audit.error.decisionRequired");
            version(p.lockVersion(), t);
            require(access.manages(t, a), 403, "FORBIDDEN", "audit.error.cannotApprove");
            Change c = t.changeRequests.stream().filter(r -> r.requestId.equals(requestId)).findFirst()
                    .orElseThrow(() -> new AuditException(404, "NOT_FOUND", "audit.error.requestNotFound"));
            require("PENDING".equals(c.status) && p.requestVersion() != null && c.requestVersion == p.requestVersion(),
                    409, "VERSION_CONFLICT", "audit.error.requestHandled");
            require(!a.id().equals(c.requestedBy), 403, "SELF_APPROVAL", "audit.error.requestSelfApproval");
            require("APPROVED".equals(p.decision()) || "REJECTED".equals(p.decision()), 422, "INVALID_DECISION",
                    "audit.error.invalidDecision");
            text(p.reason(), 4000, true, "audit.field.decisionBasis");
            access.evidence(p.evidenceRef(), t, a);
            TaskValues before = values(t);
            Task next = null;
            if ("APPROVED".equals(p.decision())) {
                checkChangeState(t, c.type, c.proposedDueAt, c.proposedChanges, a);
                if (c.affectedSubmissionVersion != null)
                    require(!t.submissions.isEmpty()
                            && c.affectedSubmissionVersion == t.submissions.get(t.submissions.size() - 1).version, 409,
                            "VERSION_CONFLICT", "audit.error.requestSubmissionChanged");
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
                            "audit.error.nextRoundExists");
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
        require(tasks.count(spec) <= 10000, 422, "EXPORT_TOO_LARGE", "audit.error.exportTooLarge");
        StringBuilder csv = new StringBuilder("\uFEFF" + I18n.t("audit.export.header") + "\r\n");
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
