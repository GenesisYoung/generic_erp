-- =============================================================================
--  Customer order sync: new old-ERP customer orders -> PMC -> order audit.
--  Runs after V4__notification_i18n.sql. Re-runnable.
--
--  Hibernate (ddl-auto: update) also creates the table from the entity
--  (CustomerOrder); section 1 is for environments where ddl-auto is off.
-- =============================================================================

-- 1. Copy of old-ERP customer order headers (ORDCORD1). -----------------------
CREATE TABLE IF NOT EXISTS legacy_customer_order_tb (
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    legacy_id       VARCHAR(32)    NOT NULL,   -- ORDCORD1.ERI
    order_no        VARCHAR(50)    NOT NULL,   -- ORDCORD1.CODE, = pmc_order_audit_task.order_number
    customer_code   VARCHAR(50)    NULL,
    customer_name   VARCHAR(200)   NULL,
    order_date      DATE           NOT NULL,
    delivery_date   DATE           NULL,
    sales_code      VARCHAR(50)    NULL,       -- ORDCORD1.STAFF
    line_count      INT            NULL,
    total_qty       DECIMAL(18,4)  NULL,
    legacy_audited  BIT(1)         NOT NULL,   -- SCRUTINY filled (informational)
    legacy_closed   BIT(1)         NOT NULL,   -- CLOSETYPE <> 0
    first_seen_at   DATETIME(6)    NOT NULL,
    last_synced_at  DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_customer_order_legacy_id (legacy_id),
    KEY idx_customer_order_no (order_no),
    KEY idx_customer_order_date (order_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 2. Permissions. ---------------------------------------------------------------
--    menu.customerOrder        : menu root (shown as a selectable menu)
--    menu.customerOrder.read   : see the 待评审客户订单 list and sync status
--    menu.customerOrder.update : run a sync now
INSERT INTO permission_tb (permission_code, is_enabled, create_date, permission_name)
SELECT seed.code, 1, CURDATE(), seed.name
FROM (
    SELECT 'menu.customerOrder'        AS code, 'Customer Orders to Audit' AS name UNION ALL
    SELECT 'menu.customerOrder.read',           NULL                                UNION ALL
    SELECT 'menu.customerOrder.update',         NULL
) AS seed
WHERE NOT EXISTS (SELECT 1 FROM permission_tb p WHERE p.permission_code = seed.code);

-- 3. Default grants: pmc_manager everything, pmc_clerk read. --------------------
INSERT INTO role_permission_tb (role_id, permission_id, menu_id, created_by, create_time, create_date)
SELECT r.id, p.id, NULL, u.id, NOW(6), CURDATE()
FROM role_tb r
JOIN permission_tb p ON (
       (r.role_name = 'pmc_manager' AND p.permission_code = 'menu.customerOrder')
    OR (r.role_name = 'pmc_clerk'   AND p.permission_code = 'menu.customerOrder.read'))
CROSS JOIN user_tb u
WHERE u.username = 'root'
  AND p.is_enabled = 1
  AND NOT EXISTS (
      SELECT 1 FROM role_permission_tb rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- 4. Sidebar entry (frontend route /pmc/customer-orders, i18n key
--    customerOrderReview), granted to every pmc_manager and pmc_clerk user.
--    Users who get one of these roles later need the entry granted the usual way.
INSERT INTO navigation_menu_tb
    (parent_id, title_key, icon, route, color, permission_id, is_enabled, create_time)
SELECT NULL, 'customerOrderReview', 'mdi-cart-arrow-down', '/pmc/customer-orders', 'indigo', p.id, 1, NOW()
FROM permission_tb p
WHERE p.permission_code = 'menu.customerOrder.read'
  AND NOT EXISTS (SELECT 1 FROM navigation_menu_tb m WHERE m.title_key = 'customerOrderReview')
LIMIT 1;

INSERT INTO user_nav_menu (nav_id, permission_id, user_id, is_enabled, create_date)
SELECT DISTINCT m.id, NULL, ur.user_id, 1, NOW()
FROM navigation_menu_tb m
JOIN role_tb r        ON r.role_name IN ('pmc_manager', 'pmc_clerk')
JOIN user_roles_tb ur ON ur.role_id = r.id AND ur.user_enabled = 1
WHERE m.title_key = 'customerOrderReview'
  AND NOT EXISTS (SELECT 1 FROM user_nav_menu x WHERE x.nav_id = m.id AND x.user_id = ur.user_id);
