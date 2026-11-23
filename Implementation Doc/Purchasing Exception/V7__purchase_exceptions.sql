-- =============================================================================
--  Purchasing exceptions (采购异常): a purchaser reports a supplier problem on
--  a purchase order; PMC / Planning / Operations handle it; the reporter or
--  the purchasing manager closes it.
--  Runs after V6__placement_tasks.sql. Re-runnable.
--
--  Hibernate (ddl-auto: update) also creates both tables from the entities
--  (PurchaseException, PurchaseExceptionLog); sections 1 and 2 are for
--  environments where ddl-auto is off.
--  The permission codes menu.purchaseException(.read/.create/.update) and
--  data.purchaseException.all already exist (V1).
-- =============================================================================

-- 1. Exceptions. ----------------------------------------------------------------
CREATE TABLE IF NOT EXISTS purchase_exception_tb (
    id                 BIGINT         NOT NULL AUTO_INCREMENT,
    exception_no       VARCHAR(32)    NULL,       -- EX-yyMMdd-0001, set right after insert
    purchase_order_id  BIGINT         NOT NULL,   -- purchase_order_tb.id
    po_no              VARCHAR(50)    NOT NULL,
    supplier_code      VARCHAR(50)    NULL,
    supplier_name      VARCHAR(200)   NULL,
    reporter_id        BIGINT         NOT NULL,
    category           VARCHAR(16)    NOT NULL,   -- DELAY SHORTAGE QUALITY PRICE CAPACITY OTHER
    severity           VARCHAR(16)    NOT NULL,   -- NORMAL HIGH URGENT
    description        VARCHAR(2000)  NOT NULL,
    impact_date        DATE           NULL,
    status             VARCHAR(16)    NOT NULL,   -- REPORTED ACKNOWLEDGED IN_PROGRESS RESOLVED CLOSED
    handler_id         BIGINT         NULL,
    resolution         VARCHAR(2000)  NULL,
    resolved_at        DATETIME(6)    NULL,
    closed_at          DATETIME(6)    NULL,
    reported_at        DATETIME(6)    NOT NULL,
    updated_at         DATETIME(6)    NOT NULL,
    lock_version       BIGINT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_pexc_reporter_status (reporter_id, status),
    KEY idx_pexc_status (status, reported_at),
    KEY idx_pexc_po (purchase_order_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 2. History: every status change and comment. ----------------------------------
CREATE TABLE IF NOT EXISTS purchase_exception_log_tb (
    id            BIGINT         NOT NULL AUTO_INCREMENT,
    exception_id  BIGINT         NOT NULL,
    action        VARCHAR(16)    NOT NULL,   -- REPORT ACKNOWLEDGE START COMMENT RESOLVE CLOSE REOPEN
    from_status   VARCHAR(16)    NULL,
    to_status     VARCHAR(16)    NOT NULL,
    operator_id   BIGINT         NOT NULL,
    comment       VARCHAR(2000)  NULL,
    created_at    DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    KEY idx_pexc_log_exception (exception_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 3. Grants so PMC can handle exceptions (V1 gave pmc_manager read only).
--    Handling = menu.purchaseException.update + data.purchaseException.all.
--    Planning and Operations users: grant the same two codes to their roles
--    in the admin screens, and list their department ids in
--    ERP_EXCEPTION_NOTIFY_DEPT_IDS so they are alerted.
INSERT INTO role_permission_tb (role_id, permission_id, menu_id, created_by, create_time, create_date)
SELECT r.id, p.id, NULL, u.id, NOW(6), CURDATE()
FROM role_tb r
JOIN permission_tb p ON (
       (r.role_name = 'pmc_manager' AND p.permission_code = 'menu.purchaseException.update')
    OR (r.role_name = 'pmc_clerk'   AND p.permission_code IN (
            'menu.purchaseException.read', 'menu.purchaseException.update', 'data.purchaseException.all')))
CROSS JOIN user_tb u
WHERE u.username = 'root'
  AND p.is_enabled = 1
  AND NOT EXISTS (
      SELECT 1 FROM role_permission_tb rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- 4. Sidebar entry (frontend route /purchase/exceptions, i18n key
--    purchaseExceptions) for purchaser, purchaser_manager, pmc_manager and
--    pmc_clerk users.
INSERT INTO navigation_menu_tb
    (parent_id, title_key, icon, route, color, permission_id, is_enabled, create_time)
SELECT NULL, 'purchaseExceptions', 'mdi-alert-octagon-outline', '/purchase/exceptions', 'red', p.id, 1, NOW()
FROM permission_tb p
WHERE p.permission_code = 'menu.purchaseException.read'
  AND NOT EXISTS (SELECT 1 FROM navigation_menu_tb m WHERE m.title_key = 'purchaseExceptions')
LIMIT 1;

INSERT INTO user_nav_menu (nav_id, permission_id, user_id, is_enabled, create_date)
SELECT DISTINCT m.id, NULL, ur.user_id, 1, NOW()
FROM navigation_menu_tb m
JOIN role_tb r        ON r.role_name IN ('purchaser', 'purchaser_manager', 'pmc_manager', 'pmc_clerk')
JOIN user_roles_tb ur ON ur.role_id = r.id AND ur.user_enabled = 1
WHERE m.title_key = 'purchaseExceptions'
  AND NOT EXISTS (SELECT 1 FROM user_nav_menu x WHERE x.nav_id = m.id AND x.user_id = ur.user_id);
