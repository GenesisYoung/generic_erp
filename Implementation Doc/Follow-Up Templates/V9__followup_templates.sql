-- =============================================================================
--  Follow-up templates (跟进模板). A template sets a production period (days
--  from the order date), the notification days inside it, and how often to
--  remind after the last day. Each notification day becomes its own follow-up
--  task (po_followup_checkpoint_tb row, stage NOTIFY) that the purchaser marks
--  complete. New purchase orders get the default template; a user can switch
--  an order to another template by hand.
--  Runs after V8__dashboards.sql. Re-runnable.
--
--  Hibernate (ddl-auto: update) also adds the columns and the table from the
--  entities (FollowUpRule, FollowUpRuleDay); sections 1 and 2 are for
--  environments where ddl-auto is off. Existing templates (the V2 default
--  rule) keep their stages and keep working unchanged.
-- =============================================================================

SET @db := DATABASE();

-- 1. Template fields on followup_rule_tb. --------------------------------------
SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'followup_rule_tb' AND COLUMN_NAME = 'production_days') = 0,
               'ALTER TABLE followup_rule_tb ADD COLUMN production_days INT NULL AFTER overdue_period_days',
               'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'followup_rule_tb' AND COLUMN_NAME = 'material_note') = 0,
               'ALTER TABLE followup_rule_tb ADD COLUMN material_note VARCHAR(200) NULL AFTER production_days',
               'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'followup_rule_tb' AND COLUMN_NAME = 'updated_at') = 0,
               'ALTER TABLE followup_rule_tb ADD COLUMN updated_at DATETIME(6) NULL AFTER created_at',
               'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. Notification days of a template (day 1 = the day after the order date). ----
CREATE TABLE IF NOT EXISTS followup_rule_day_tb (
    id       BIGINT  NOT NULL AUTO_INCREMENT,
    rule_id  BIGINT  NOT NULL,   -- followup_rule_tb.id (logical)
    day_no   INT     NOT NULL,   -- 1 .. production_days; day N = order date + N days
    PRIMARY KEY (id),
    KEY idx_rule_day_rule (rule_id, day_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 3. Permissions: menu.followupTemplate with .read / .update. -------------------
INSERT INTO permission_tb (permission_code, is_enabled, create_date)
SELECT seed.code, 1, CURDATE()
FROM (
    SELECT 'menu.followupTemplate'        AS code UNION ALL
    SELECT 'menu.followupTemplate.read'           UNION ALL
    SELECT 'menu.followupTemplate.update'
) AS seed
WHERE NOT EXISTS (SELECT 1 FROM permission_tb p WHERE p.permission_code = seed.code);

-- 4. Grants: the purchasing manager manages templates; purchasers can view them.
INSERT INTO role_permission_tb (role_id, permission_id, menu_id, created_by, create_time, create_date)
SELECT r.id, p.id, NULL, u.id, NOW(6), CURDATE()
FROM role_tb r
JOIN permission_tb p ON (
       (r.role_name = 'purchaser_manager' AND p.permission_code = 'menu.followupTemplate')
    OR (r.role_name = 'purchaser'         AND p.permission_code = 'menu.followupTemplate.read'))
CROSS JOIN user_tb u
WHERE u.username = 'root'
  AND p.is_enabled = 1
  AND NOT EXISTS (
      SELECT 1 FROM role_permission_tb rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- 5. Sidebar entry (frontend route /purchase/followup-templates, i18n key
--    followUpTemplates) for purchaser and purchaser_manager users.
INSERT INTO navigation_menu_tb
    (parent_id, title_key, icon, route, color, permission_id, is_enabled, create_time)
SELECT NULL, 'followUpTemplates', 'mdi-calendar-clock-outline', '/purchase/followup-templates', 'teal', p.id, 1, NOW()
FROM permission_tb p
WHERE p.permission_code = 'menu.followupTemplate.read'
  AND NOT EXISTS (SELECT 1 FROM navigation_menu_tb m WHERE m.title_key = 'followUpTemplates')
LIMIT 1;

INSERT INTO user_nav_menu (nav_id, permission_id, user_id, is_enabled, create_date)
SELECT DISTINCT m.id, NULL, ur.user_id, 1, NOW()
FROM navigation_menu_tb m
JOIN role_tb r        ON r.role_name IN ('purchaser', 'purchaser_manager')
JOIN user_roles_tb ur ON ur.role_id = r.id AND ur.user_enabled = 1
WHERE m.title_key = 'followUpTemplates'
  AND NOT EXISTS (SELECT 1 FROM user_nav_menu x WHERE x.nav_id = m.id AND x.user_id = ur.user_id);
