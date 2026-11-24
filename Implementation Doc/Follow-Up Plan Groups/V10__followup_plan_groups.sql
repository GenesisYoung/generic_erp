-- =============================================================================
--  Follow-up plan groups (跟进计划分组). Purchase order lines are synced from
--  the old ERP with a plan type. Plan types are put into plan groups: types in
--  one group count as the same plan. A group marked "own task" (is_separate)
--  gets its own follow-up task series in each order that has open lines of
--  it; every other line stays in the order's main follow-up task.
--  Runs after V9__followup_templates.sql. Re-runnable.
--
--  Hibernate (ddl-auto: update) also creates these tables and columns from the
--  entities (PurchaseOrderLine, FollowUpPlanType, FollowUpPlanGroup,
--  FollowUpTrack, FollowUpCheckpoint, FollowUpRecord, PurchaseOrder); this
--  script is for environments where ddl-auto is off.
--  No new permissions: the plan groups page uses menu.followupTemplate.read /
--  .update (V9). Section 7 adds its sidebar entry.
--  Nothing changes for existing orders until plan groups are set up and
--  "Apply to open orders" is pressed (or the next sync reads their lines).
-- =============================================================================

SET @db := DATABASE();

-- 1. Purchase order lines from the old ERP (ORDSPHS2). ---------------------------
CREATE TABLE IF NOT EXISTS purchase_order_line_tb (
    id                 BIGINT        NOT NULL AUTO_INCREMENT,
    purchase_order_id  BIGINT        NOT NULL,   -- purchase_order_tb.id (logical)
    line_no            INT           NOT NULL,   -- ORDSPHS2.SERIAL
    material_code      VARCHAR(60)   NULL,
    material_name      VARCHAR(255)  NULL,
    plan_type_code     VARCHAR(50)   NULL,       -- decides the follow-up task; null = unknown
    qty                DOUBLE        NULL,
    received_qty       DOUBLE        NULL,
    synced_at          DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_po_line (purchase_order_id, line_no),
    KEY idx_po_line_plan_type (plan_type_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 2. Plan groups. -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS followup_plan_group_tb (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    group_name        VARCHAR(100)  NOT NULL,
    is_separate       BIT(1)        NOT NULL,   -- 1 = own follow-up task, 0 = stays in the main task
    followup_rule_id  BIGINT        NULL,       -- template of its own task; null = the default template
    note              VARCHAR(200)  NULL,
    is_active         BIT(1)        NOT NULL,
    created_at        DATETIME(6)   NOT NULL,
    updated_at        DATETIME(6)   NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 3. Plan types (seen by the sync or added by hand) and their group. -----------
CREATE TABLE IF NOT EXISTS followup_plan_type_tb (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    type_code     VARCHAR(50)   NOT NULL,
    type_name     VARCHAR(100)  NULL,
    group_id      BIGINT        NULL,       -- followup_plan_group_tb.id; null = main task
    last_seen_at  DATETIME(6)   NULL,
    created_at    DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_plan_type_code (type_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 4. One plan group's own follow-up task series inside one order. --------------
CREATE TABLE IF NOT EXISTS po_followup_track_tb (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    purchase_order_id  BIGINT       NOT NULL,
    plan_group_id      BIGINT       NOT NULL,
    followup_rule_id   BIGINT       NULL,
    is_active          BIT(1)       NOT NULL,
    created_at         DATETIME(6)  NOT NULL,
    updated_at         DATETIME(6)  NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_track_po_group (purchase_order_id, plan_group_id),
    KEY idx_track_group (plan_group_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 5. Which series a task / record belongs to (null = main task). ---------------
SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'po_followup_checkpoint_tb' AND COLUMN_NAME = 'plan_group_id') = 0,
               'ALTER TABLE po_followup_checkpoint_tb ADD COLUMN plan_group_id BIGINT NULL AFTER purchase_order_id',
               'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'po_followup_checkpoint_tb' AND INDEX_NAME = 'idx_checkpoint_po_group') = 0,
               'CREATE INDEX idx_checkpoint_po_group ON po_followup_checkpoint_tb (purchase_order_id, plan_group_id)',
               'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'po_followup_record_tb' AND COLUMN_NAME = 'plan_group_id') = 0,
               'ALTER TABLE po_followup_record_tb ADD COLUMN plan_group_id BIGINT NULL AFTER purchase_order_id',
               'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 6. Main task switch on the order (null / 1 = needed, 0 = every open line is in
--    a plan group's own task). ---------------------------------------------------
SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'purchase_order_tb' AND COLUMN_NAME = 'main_followup_active') = 0,
               'ALTER TABLE purchase_order_tb ADD COLUMN main_followup_active BIT(1) NULL AFTER followup_rule_id',
               'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 7. Sidebar entry (frontend route /purchase/followup-plan-groups, i18n key
--    followUpPlanGroups) for everyone who already sees the follow-up templates entry.
INSERT INTO navigation_menu_tb
    (parent_id, title_key, icon, route, color, permission_id, is_enabled, create_time)
SELECT NULL, 'followUpPlanGroups', 'mdi-shape-outline', '/purchase/followup-plan-groups', 'teal', p.id, 1, NOW()
FROM permission_tb p
WHERE p.permission_code = 'menu.followupTemplate.read'
  AND NOT EXISTS (SELECT 1 FROM navigation_menu_tb m WHERE m.title_key = 'followUpPlanGroups')
LIMIT 1;

INSERT INTO user_nav_menu (nav_id, permission_id, user_id, is_enabled, create_date)
SELECT DISTINCT g.id, NULL, x.user_id, 1, NOW()
FROM navigation_menu_tb g
JOIN navigation_menu_tb t ON t.title_key = 'followUpTemplates'
JOIN user_nav_menu x      ON x.nav_id = t.id AND x.is_enabled = 1
WHERE g.title_key = 'followUpPlanGroups'
  AND NOT EXISTS (SELECT 1 FROM user_nav_menu y WHERE y.nav_id = g.id AND y.user_id = x.user_id);
