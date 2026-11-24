-- =============================================================================
--  PMC approval for purchasing exceptions (采购异常 PMC 审批).
--  A handler's "resolve" now only proposes a resolution: the exception goes to
--  PENDING_APPROVAL and a PMC approval task is created. It becomes RESOLVED
--  only when a member of the PMC department approves; a rejection (with a
--  reason) sends it back to IN_PROGRESS, and the next proposal creates a new
--  task.
--  Runs after V9__followup_templates.sql. Re-runnable.
--
--  Who approves: users in the department whose name contains "PMC" (ignoring
--  case, sub-departments included). Change it with ERP_EXCEPTION_APPROVER_DEPT_NAMES
--  or ERP_EXCEPTION_APPROVER_DEPT_IDS (department_tb.id, comma separated).
--
--  Hibernate (ddl-auto: update) also creates the table from the entity
--  (PurchaseExceptionApproval); section 1 is for environments where ddl-auto
--  is off. Exceptions already RESOLVED or CLOSED stay as they are.
-- =============================================================================

-- 1. Approval tasks. -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS purchase_exception_approval_tb (
    id                   BIGINT         NOT NULL AUTO_INCREMENT,
    exception_id         BIGINT         NOT NULL,   -- purchase_exception_tb.id
    status               VARCHAR(16)    NOT NULL,   -- PENDING APPROVED REJECTED
    proposed_resolution  VARCHAR(2000)  NOT NULL,
    requested_by         BIGINT         NOT NULL,   -- the handler who proposed it
    requested_at         DATETIME(6)    NOT NULL,
    decided_by           BIGINT         NULL,       -- the PMC member who answered
    decided_at           DATETIME(6)    NULL,
    decision_comment     VARCHAR(2000)  NULL,       -- optional note / reason for rejecting
    PRIMARY KEY (id),
    KEY idx_pexc_appr_exception (exception_id, requested_at),
    KEY idx_pexc_appr_status (status, requested_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 2. New values: status PENDING_APPROVAL, actions APPROVE / REJECT, notification
--    type EXCEPTION_APPROVAL. If Hibernate created these columns as MySQL ENUM
--    types, the new values would be refused; make them plain VARCHAR (no-op
--    when they already are).
ALTER TABLE purchase_exception_tb     MODIFY status      VARCHAR(16) NOT NULL;
ALTER TABLE purchase_exception_log_tb MODIFY action      VARCHAR(16) NOT NULL;
ALTER TABLE purchase_exception_log_tb MODIFY from_status VARCHAR(16) NULL;
ALTER TABLE purchase_exception_log_tb MODIFY to_status   VARCHAR(16) NOT NULL;
ALTER TABLE notification_tb           MODIFY type        VARCHAR(32) NOT NULL;

-- 3. Check: who will approve (should list the PMC department's users).
SELECT d.id AS dept_id, d.dept_name, u.id AS user_id, u.username
FROM department_tb d
JOIN user_departments_tb ud ON ud.dept_id = d.id
JOIN user_tb u              ON u.id = ud.user_id
WHERE LOWER(d.dept_name) LIKE '%pmc%';
