-- =============================================================================
--  Purchase order follow-up module - step 2: purchase orders synced from the
--  old ERP, follow-up rules and checkpoints, and role alignment with the
--  order review module (pmc_order_audit_task).
--  Runs after V1__roles_permissions_notification.sql and after
--  src/main/resources/db/order_audit_v1.sql. Re-runnable.
--
--  Hibernate (ddl-auto: update) also creates the tables from the entities;
--  these statements are for environments where ddl-auto is off.
--  The default follow-up rule is also created by the application on first use
--  if it is missing (FollowUpRuleService).
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Follow-up rules. One active default; an order may point to another rule.
--    Days are calendar days after the order date (decided 2026-11-20).
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS followup_rule_tb (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    rule_name            VARCHAR(100) NOT NULL,
    is_default           BIT(1)       NOT NULL,
    is_active            BIT(1)       NOT NULL,
    overdue_period_days  INT          NULL,      -- NULL = no OVERDUE checkpoints
    created_at           DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS followup_rule_stage_tb (
    id                 BIGINT      NOT NULL AUTO_INCREMENT,
    rule_id            BIGINT      NOT NULL,     -- followup_rule_tb.id (logical)
    seq                INT         NOT NULL,
    stage              VARCHAR(16) NOT NULL,     -- INITIAL / INTENSIFIED / DAILY
    start_day          INT         NOT NULL,
    end_day            INT         NOT NULL,
    period_days        INT         NOT NULL,
    checks_per_period  INT         NOT NULL,
    PRIMARY KEY (id),
    KEY idx_rule_stage_rule (rule_id, seq)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO followup_rule_tb (rule_name, is_default, is_active, overdue_period_days, created_at)
SELECT 'Default: 2x/week weeks 1-2, every 2 days week 3, daily week 4', b'1', b'1', 1, NOW(6)
WHERE NOT EXISTS (SELECT 1 FROM followup_rule_tb WHERE is_default = b'1' AND is_active = b'1');

INSERT INTO followup_rule_stage_tb (rule_id, seq, stage, start_day, end_day, period_days, checks_per_period)
SELECT r.id, s.seq, s.stage, s.start_day, s.end_day, s.period_days, s.checks_per_period
FROM followup_rule_tb r
CROSS JOIN (
    SELECT 1 AS seq, 'INITIAL' AS stage, 1 AS start_day, 14 AS end_day, 7 AS period_days, 2 AS checks_per_period UNION ALL
    SELECT 2,        'INTENSIFIED',      15,              21,            2,                1                      UNION ALL
    SELECT 3,        'DAILY',            22,              28,            1,                1
) s
WHERE r.is_default = b'1' AND r.is_active = b'1'
  AND NOT EXISTS (SELECT 1 FROM followup_rule_stage_tb x WHERE x.rule_id = r.id);

-- -----------------------------------------------------------------------------
-- 2. Purchase orders (copied from the old ERP) and their checkpoints.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS purchase_order_tb (
    id                      BIGINT       NOT NULL AUTO_INCREMENT,
    source_system           VARCHAR(30)  NOT NULL,
    external_po_no          VARCHAR(50)  NOT NULL,
    supplier_code           VARCHAR(50)  NULL,
    supplier_name           VARCHAR(255) NULL,
    buyer_code              VARCHAR(50)  NULL,
    purchaser_id            BIGINT       NULL,     -- user_tb.id; NULL until the buyer code is mapped
    status                  VARCHAR(24)  NOT NULL,
    source_status           VARCHAR(50)  NULL,
    order_date              DATE         NOT NULL,
    expected_delivery_date  DATE         NULL,
    followup_rule_id        BIGINT       NULL,
    source_updated_at       DATETIME(6)  NULL,
    synced_at               DATETIME(6)  NULL,
    created_at              DATETIME(6)  NOT NULL,
    updated_at              DATETIME(6)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_po_source_no (source_system, external_po_no),
    KEY idx_po_purchaser_status (purchaser_id, status),
    KEY idx_po_status_order_date (status, order_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS po_followup_checkpoint_tb (
    id                 BIGINT      NOT NULL AUTO_INCREMENT,
    purchase_order_id  BIGINT      NOT NULL,
    stage              VARCHAR(16) NOT NULL,
    seq                INT         NOT NULL,
    window_start       DATE        NOT NULL,
    window_end         DATE        NOT NULL,
    status             VARCHAR(16) NOT NULL,     -- OPEN / DONE / MISSED / CANCELLED
    done_record_id     BIGINT      NULL,
    reminded_at        DATETIME(6) NULL,
    created_at         DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_checkpoint_po (purchase_order_id, seq),
    KEY idx_checkpoint_status_end (status, window_end)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS legacy_user_map_tb (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    source_system  VARCHAR(30) NOT NULL,
    legacy_code    VARCHAR(50) NOT NULL,     -- e.g. buyer code in the old ERP
    user_id        BIGINT      NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_legacy_user_code (source_system, legacy_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS legacy_sync_state_tb (
    sync_key                VARCHAR(50)   NOT NULL,
    last_source_updated_at  DATETIME(6)   NULL,
    last_run_at             DATETIME(6)   NULL,
    last_status             VARCHAR(16)   NULL,
    last_message            VARCHAR(1000) NULL,
    last_row_count          INT           NULL,
    PRIMARY KEY (sync_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- 3. Role alignment with the order review module.
--    The order review module (pmc_order_audit_task) already has its own codes,
--    so the duplicates seeded in V1 (menu.orderReview*, data.orderReview.all)
--    are removed. No code uses them.
-- -----------------------------------------------------------------------------
DELETE rp FROM role_permission_tb rp
JOIN permission_tb p ON p.id = rp.permission_id
WHERE p.permission_code IN ('menu.orderReview', 'menu.orderReview.read', 'menu.orderReview.create',
                            'menu.orderReview.update', 'data.orderReview.all');

DELETE FROM permission_tb
WHERE permission_code IN ('menu.orderReview', 'menu.orderReview.read', 'menu.orderReview.create',
                          'menu.orderReview.update', 'data.orderReview.all');

--    pmc_manager : every order review action, all departments
--    pmc_clerk   : works on own review tasks (create, update, execute, submit,
--                  request changes); no accept / verify / approve
--    Review against your real organisation before running in production.
INSERT INTO role_permission_tb (role_id, permission_id, menu_id, created_by, create_time, create_date)
SELECT r.id, p.id, NULL, u.id, NOW(6), CURDATE()
FROM role_tb r
JOIN permission_tb p ON (
       (r.role_name = 'pmc_manager' AND p.permission_code IN (
            'menu.order-audit-task.read', 'menu.order-audit-task.create', 'menu.order-audit-task.update',
            'menu.order-audit-task.accept', 'menu.order-audit-task.execute', 'menu.order-audit-task.submit',
            'menu.order-audit-task.verify', 'menu.order-audit-task.confirm',
            'menu.order-audit-task.change.request', 'menu.order-audit-task.approve',
            'menu.order-audit-task.export', 'pmc.order-audit.scope.all'))
    OR (r.role_name = 'pmc_clerk' AND p.permission_code IN (
            'menu.order-audit-task.read', 'menu.order-audit-task.create', 'menu.order-audit-task.update',
            'menu.order-audit-task.execute', 'menu.order-audit-task.submit',
            'menu.order-audit-task.change.request')))
CROSS JOIN user_tb u
WHERE u.username = 'root'
  AND p.is_enabled = 1
  AND NOT EXISTS (
      SELECT 1 FROM role_permission_tb rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
