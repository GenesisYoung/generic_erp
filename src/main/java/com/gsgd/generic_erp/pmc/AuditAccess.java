package com.gsgd.generic_erp.pmc;

import static com.gsgd.generic_erp.pmc.AuditException.require;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import com.gsgd.generic_erp.configuration.security.PermissionChecker;
import com.gsgd.generic_erp.pmc.AuditTypes.*;

@Component
public class AuditAccess {
    public static final String PREFIX = "menu.order-audit-task.";
    private final JdbcTemplate jdbc;
    private final PermissionChecker permissions;

    public AuditAccess(JdbcTemplate jdbc, PermissionChecker permissions) {
        this.jdbc = jdbc;
        this.permissions = permissions;
    }

    public record Actor(String id, String name, Set<String> departments, boolean all, boolean team) {
    }

    public boolean can(String action) {
        return permissions.has(PREFIX + action);
    }

    public boolean canEvidence(String action) {
        return permissions.has("menu.evidence." + action);
    }

    public void needEvidence(String action) {
        require(canEvidence("read") && canEvidence(action), 403, "FORBIDDEN", "audit.error.noEvidencePermission");
    }

    public boolean linkedEvidence(String ref, Task t) {
        return t != null && (t.materials.stream().anyMatch(m -> ref.equals(m.evidenceRef()))
                || t.submissions.stream()
                        .anyMatch(v -> ref.equals(v.evidenceRef)
                                || (v.materialsSnapshot != null
                                        && v.materialsSnapshot.stream().anyMatch(m -> ref.equals(m.evidenceRef())))
                                || v.verificationRecords.stream().anyMatch(vr -> ref.equals(vr.evidenceRef()))
                                || v.businessConfirmations.stream().anyMatch(c -> ref.equals(c.evidenceRef())))
                || t.changeRequests.stream().anyMatch(c -> ref.equals(c.evidenceRef) || ref.equals(c.decisionEvidence)
                        || (c.proposedChanges != null && c.proposedChanges.materials() != null
                                && c.proposedChanges.materials().stream().anyMatch(m -> ref.equals(m.evidenceRef())))));
    }

    public void need(String action) {
        require(can("read") && can(action), 403, "FORBIDDEN", "audit.error.noActionPermission");
    }

    public Actor actor() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        require(auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName()), 401,
                "UNAUTHENTICATED", "audit.error.loginRequired");
        var users = jdbc.query(
                "select id, coalesce(display_name,username) as name from user_tb where username=? and status=true and is_enabled=1",
                (rs, row) -> new Person(rs.getString("id"), rs.getString("name")), auth.getName());
        require(users.size() == 1, 401, "UNAUTHENTICATED", "audit.error.accountUnavailable");
        var u = users.get(0);
        return new Actor(u.id(), u.name(),
                new HashSet<>(jdbc.queryForList("select dept_id from user_departments_tb where user_id=?", String.class,
                        u.id())),
                permissions.has("pmc.order-audit.scope.all"), permissions.has("pmc.order-audit.scope.team"));
    }

    public void lockActor(Actor a) {
        jdbc.queryForObject("select id from user_tb where id=? for update", Long.class, a.id());
    }

    public boolean visible(Task t, Actor a) {
        return a.all() || a.id().equals(t.ownerUserId) || a.id().equals(t.createdBy) || a.id().equals(t.nextOwnerUserId)
                || (a.team() && t.departmentId != null && a.departments().contains(t.departmentId));
    }

    public void visibleOr404(Task t, Actor a) {
        require(visible(t, a), 404, "NOT_FOUND", "audit.error.taskNotFound");
    }

    public boolean manages(Task t, Actor a) {
        return a.all() || (a.team() && t.departmentId != null && a.departments().contains(t.departmentId));
    }

    public boolean executes(Task t, Actor a) {
        return manages(t, a) || a.id().equals(t.ownerUserId) || a.id().equals(t.nextOwnerUserId);
    }

    public List<Person> people(Actor a) {
        return jdbc.query(
                "select u.id, coalesce(u.display_name,u.username) as name from user_tb u where u.status=true and u.is_enabled=1 and (u.id=? or ?=true or (?=true and exists (select 1 from user_departments_tb target join user_departments_tb caller on target.dept_id=caller.dept_id where target.user_id=u.id and caller.user_id=?))) order by u.id",
                (rs, row) -> new Person(rs.getString("id"), rs.getString("name")), a.id(), a.all(), a.team(), a.id());
    }

    public Person person(String id, Actor a) {
        require(id != null && id.matches("[1-9][0-9]{0,18}"), 400, "INVALID_USER", "audit.error.invalidUserId");
        return people(a).stream().filter(p -> p.id().equals(id)).findFirst()
                .orElseThrow(() -> new AuditException(403, "USER_OUT_OF_SCOPE", "audit.error.ownerOutOfScope"));
    }

    public String department(String owner, Actor a) {
        if (owner == null || owner.equals(a.id()))
            return a.departments().stream().sorted().findFirst().orElse(null);
        var ids = jdbc.queryForList("select dept_id from user_departments_tb where user_id=? order by dept_id",
                String.class, owner);
        return ids.stream().filter(id -> a.all() || a.departments().contains(id)).findFirst().orElse(null);
    }

    /**
     * Only active, owned existing file IDs or immutable task-bound audit evidence
     * are accepted.
     */
    public void evidence(String ref, Task t, Actor a) {
        require(ref != null && ref.length() <= 100, 422, "INVALID_EVIDENCE", "audit.error.invalidEvidenceRef");
        if (ref.startsWith("evidence:")) {
            var owners = jdbc.queryForList("select created_by from pmc_personal_evidence where id=?", String.class,
                    ref.substring(9));
            require(owners.size() == 1 && (owners.get(0).equals(a.id()) || linkedEvidence(ref, t)), 403,
                    "INVALID_EVIDENCE", "audit.error.evidenceNotOwnedOrLinked");
            return;
        }
        if (ref.startsWith("audit:")) {
            String id = ref.substring(6);
            var files = jdbc.query("select task_id,created_by from pmc_order_audit_evidence where id=?",
                    (rs, n) -> new String[] { rs.getString(1), rs.getString(2) }, id);
            require(files.size() == 1 && (t != null ? files.get(0)[0].equals(t.id) : files.get(0)[1].equals(a.id())),
                    403, "INVALID_EVIDENCE", "audit.error.evidenceNotInTask");
            return;
        }
        require(ref.matches("appendix:[1-9][0-9]{0,18}"), 422, "INVALID_EVIDENCE",
                "audit.error.evidenceRefFormat");
        String id = ref.substring(9);
        boolean linked = t != null && (t.materials.stream().anyMatch(m -> ref.equals(m.evidenceRef()))
                || t.submissions.stream()
                        .anyMatch(s -> ref.equals(s.evidenceRef)
                                || s.verificationRecords.stream().anyMatch(v -> ref.equals(v.evidenceRef()))
                                || s.businessConfirmations.stream().anyMatch(c -> ref.equals(c.evidenceRef())))
                || t.changeRequests.stream().anyMatch(c -> ref.equals(c.evidenceRef) || ref.equals(c.decisionEvidence)
                        || (c.proposedChanges != null && c.proposedChanges.materials() != null
                                && c.proposedChanges.materials().stream().anyMatch(m -> ref.equals(m.evidenceRef())))));
        Integer n = jdbc.queryForObject(
                "select count(*) from appendix_tb where appendix_id=? and active=true and (created_by=? or ?=true)",
                Integer.class, id, a.id(), linked);
        require(n != null && n == 1, 403, "INVALID_EVIDENCE", "audit.error.evidenceUnavailable");
    }
}
