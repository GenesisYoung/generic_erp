package com.gsgd.generic_erp.pmc;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Public API contract; no legacy database fields are silently inferred. */
public final class AuditTypes {
    private AuditTypes() {
    }

    public record Material(String id, String name, Boolean required, Integer status, String missingParts,
            String ownerUserId, Instant expectedAt, Instant actualAt, String evidenceRef) {
    }

    public record Draft(String orderNumber, Integer round, String title, String ownerUserId, Instant receivedAt,
            Boolean checklistConfirmed, List<Material> materials, Long lockVersion) {
    }

    public record ActionInput(Long lockVersion, Instant dueAt, String basis, String reason, String evidenceRef,
            String nextAction, String nextOwnerUserId, Instant nextFeedbackAt, String conclusion, Instant completedAt,
            String result) {
    }

    public record Query(String keyword, String ownerUserId, String status, Instant dueFrom, Instant dueTo,
            String timing, int page, int size) {
    }

    public record Person(String id, String name) {
    }

    public record Metadata(List<Person> users, boolean canCreate, String timezone, String acceptanceRule,
            String completionRule, String currentUserId, boolean canConfigureRules, boolean canApproveRules,
            boolean canExport) {
    }

    public record Page(List<Task> items, long total, boolean sourceChecked, Instant asOf) {
    }

    public record History(Instant at, String actor, String action, String note, String actorId, Object before,
            Object after) {
    }

    public record Verification(String result, String reason, String evidenceRef, String verifiedBy,
            Instant verifiedAt) {
    }

    public record BusinessConfirmation(String decision, String reason, String evidenceRef, String confirmedBy,
            Instant confirmedAt) {
    }

    public static class Submission {
        public int version;
        public String conclusion, evidenceRef, submittedBy;
        public Instant completedAt, reportedCompletedAt, submittedAt;
        public List<Material> materialsSnapshot;
        public Rule ruleSnapshot;
        public List<Verification> verificationRecords = new ArrayList<>();
        public List<BusinessConfirmation> businessConfirmations = new ArrayList<>();
    }

    public record Rule(String ruleId, Long ruleVersion, String timezone, String acceptanceRule, String completionNode,
            List<String> requiredMaterials, String reason, String evidenceRef) {
    }

    public record RuleDecision(Long ruleVersion, String decision, String reason, String evidenceRef) {
    }

    public static class RuleState {
        public long version;
        public String requestedBy, approvedBy;
        public Instant approvedAt;
        public Rule draft, approved;
        public List<String> evidenceRefs = new ArrayList<>();
        public List<History> history = new ArrayList<>();
    }

    public record ChangeInput(Long lockVersion, String type, String reason, String evidenceRef, Instant proposedDueAt,
            Draft proposedChanges, Integer affectedSubmissionVersion) {
    }

    public record DecisionInput(Long lockVersion, Long requestVersion, String decision, String reason,
            String evidenceRef) {
    }

    public static class Change {
        public String requestId, type, reason, evidenceRef, status, requestedBy, decidedBy, decisionReason,
                decisionEvidence;
        public Instant requestedAt, decidedAt, proposedDueAt;
        public long requestVersion;
        public Draft proposedChanges;
        public TaskValues originalValues;
        public Long affectedSubmissionVersion;
    }

    public record TaskValues(String status, String ownerUserId, Instant effectiveDueAt, String verificationResult) {
    }

    public static class Task {
        public String id, taskNumber, orderNumber, title, ownerUserId, ownerName, status;
        public int round;
        public long lockVersion;
        public Instant receivedAt, acceptedAt, originalDueAt, effectiveDueAt, completedAt;
        public boolean checklistConfirmed;
        public List<Material> materials = new ArrayList<>();
        public String verificationResult, nextAction, nextOwnerUserId;
        public Instant nextFeedbackAt;
        public String createdBy, departmentId, declinedReason, reviewParentTaskId, reviewChildTaskId;
        public Rule acceptanceRuleSnapshot;
        public List<Submission> submissions = new ArrayList<>();
        public List<History> history = new ArrayList<>();
        public List<Change> changeRequests = new ArrayList<>();
        public List<String> allowedActions = new ArrayList<>();
    }
}
