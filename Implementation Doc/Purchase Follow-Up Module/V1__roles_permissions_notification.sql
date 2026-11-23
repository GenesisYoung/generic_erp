-- =============================================================================
--  Purchase order follow-up module - step 1: roles, permission codes and the
--  notification table.
--  Runs after the Authentication Module scripts (V1-V3). Re-runnable: every
--  insert is guarded and the table uses IF NOT EXISTS.
--
--  Design: "Purchase Order Follow-Up Module - Design" (project doc).
--  Decided 2026-11-20: the PMC clerk role is "pmc_clerk" (not "pic_clerk").
--
--  Visibility is controlled by permission codes, never by role names, because
--  role names can be edited on the Role Management screen:
--    menu.*  codes  -> which screens / endpoints a role can use
--    data.*.all     -> the role sees every row; without it, only its own rows
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. notification_tb - one stored notification per recipient.
--    Hibernate (ddl-auto: update) also creates it from the Notification entity;
--    this statement is for environments where ddl-auto is off.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS notification_tb (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    recipient_id  BIGINT        NOT NULL,                -- user_tb.id (logical)
    type          VARCHAR(32)   NOT NULL,                -- NotificationType
    priority      VARCHAR(16)   NOT NULL DEFAULT 'NORMAL',
    title         VARCHAR(200)  NOT NULL,
    content       VARCHAR(1000) NULL,
    biz_type      VARCHAR(50)   NULL,                    -- e.g. PURCHASE_EXCEPTION
    biz_id        BIGINT        NULL,
    read_at       DATETIME(6)   NULL,                    -- NULL = unread
    created_at    DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_notification_recipient_read (recipient_id, read_at),
    KEY idx_notification_biz (biz_type, biz_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- 2. Roles. val continues after the seeded ROOT(1) / ADMIN(2) / USER(3).
-- -----------------------------------------------------------------------------
INSERT INTO role_tb (role_name, val, create_date)
SELECT seed.role_name, seed.val, CURDATE()
FROM (
    SELECT 'pmc_manager'       AS role_name, 10 AS val UNION ALL
    SELECT 'pmc_clerk',                      11        UNION ALL
    SELECT 'purchaser_manager',              12        UNION ALL
    SELECT 'purchaser',                      13
) AS seed
WHERE NOT EXISTS (SELECT 1 FROM role_tb r WHERE r.role_name = seed.role_name);

-- -----------------------------------------------------------------------------
-- 3. Menu permission roots (shown as selectable menus because they match
--    'menu.%' but not 'menu.%.%').
-- -----------------------------------------------------------------------------
INSERT INTO permission_tb (permission_code, is_enabled, create_date)
SELECT seed.code, 1, CURDATE()
FROM (
    SELECT 'menu.pmcDashboard'       AS code, 'PMC Dashboard'        AS name UNION ALL
    SELECT 'menu.purchaseDashboard',          'Purchasing Dashboard'         UNION ALL
    SELECT 'menu.purchaseOrder',              'Purchase Order Follow-Up'     UNION ALL
    SELECT 'menu.purchaseException',          'Purchasing Exception'
) AS seed
WHERE NOT EXISTS (SELECT 1 FROM permission_tb p WHERE p.permission_code = seed.code);

-- Action children: <menu>.read / .create / .update
INSERT INTO permission_tb (permission_code, is_enabled, create_date)
SELECT CONCAT(r.code, '.', a.action), 1, CURDATE()
FROM (
    SELECT 'menu.purchaseOrder'    AS code UNION ALL
    SELECT 'menu.purchaseException'
) r
CROSS JOIN (
    SELECT 'read' AS action UNION ALL
    SELECT 'create'         UNION ALL
    SELECT 'update'
) a
WHERE NOT EXISTS (SELECT 1 FROM permission_tb p WHERE p.permission_code = CONCAT(r.code, '.', a.action));

-- Data-scope codes. Not menus (they do not start with 'menu.').
INSERT INTO permission_tb (permission_code, is_enabled, create_date)
SELECT seed.code, 1, CURDATE()
FROM (
    SELECT 'data.purchaseOrder.all'     AS code, 'See all purchase orders'      AS name UNION ALL
    SELECT 'data.purchaseException.all',         'See all purchasing exceptions'
) AS seed
WHERE NOT EXISTS (SELECT 1 FROM permission_tb p WHERE p.permission_code = seed.code);

-- -----------------------------------------------------------------------------
-- 4. Default grants (created_by = root user, as in the auth seeds).
--    pmc_manager       : PMC dashboard, sees all exceptions
--    pmc_clerk         : PMC dashboard
--    (order review codes come from the order review module; see V2)
--    purchaser_manager : purchasing dashboard, all purchase orders and exceptions
--    purchaser         : purchasing dashboard, own purchase orders, report and
--                        read own exceptions
--    Review against your real organisation before running in production.
-- -----------------------------------------------------------------------------
INSERT INTO role_permission_tb (role_id, permission_id, menu_id, created_by, create_time, create_date)
SELECT r.id, p.id, NULL, u.id, NOW(6), CURDATE()
FROM role_tb r
JOIN permission_tb p ON (
       (r.role_name = 'pmc_manager' AND p.permission_code IN (
            'menu.pmcDashboard',
            'menu.purchaseException.read', 'data.purchaseException.all'))
    OR (r.role_name = 'pmc_clerk' AND p.permission_code IN (
            'menu.pmcDashboard'))
    OR (r.role_name = 'purchaser_manager' AND p.permission_code IN (
            'menu.purchaseDashboard', 'menu.purchaseOrder', 'data.purchaseOrder.all',
            'menu.purchaseException', 'data.purchaseException.all'))
    OR (r.role_name = 'purchaser' AND p.permission_code IN (
            'menu.purchaseDashboard', 'menu.purchaseOrder.read', 'menu.purchaseOrder.update',
            'menu.purchaseException.read', 'menu.purchaseException.create')))
CROSS JOIN user_tb u
WHERE u.username = 'root'
  AND p.is_enabled = 1
  AND NOT EXISTS (
      SELECT 1 FROM role_permission_tb rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
