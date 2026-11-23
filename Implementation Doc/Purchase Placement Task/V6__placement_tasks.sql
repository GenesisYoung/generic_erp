-- =============================================================================
--  Purchase placement tasks (采购下单任务): place the purchase orders of a
--  customer order within 2 days of its order audit passing.
--  Runs after V5__customer_orders.sql. Re-runnable.
--
--  Hibernate (ddl-auto: update) also creates both tables from the entities
--  (PoCustomerLink, PlacementTask); sections 1 and 2 are for environments
--  where ddl-auto is off.
--  No new permissions: the page uses menu.purchaseOrder.read; assigning needs
--  menu.purchaseOrder.update plus data.purchaseOrder.all (purchasing managers).
-- =============================================================================

-- 1. PO -> customer order links read from the old ERP. Add-only. -------------
--    Chain: ORDSPHS2.PLANERI -> PLANORDER2.ERI, PLANORDER2.FROMORDERI ->
--    ORDCORD2.ERI, ORDCORD2.MASTERI -> ORDCORD1.ERI.
CREATE TABLE IF NOT EXISTS legacy_po_customer_link_tb (
    id                        BIGINT       NOT NULL AUTO_INCREMENT,
    customer_order_legacy_id  VARCHAR(32)  NOT NULL,   -- ORDCORD1.ERI
    customer_order_no         VARCHAR(50)  NOT NULL,   -- ORDCORD1.CODE
    customer_line_legacy_id   VARCHAR(32)  NOT NULL,   -- ORDCORD2.ERI
    po_no                     VARCHAR(50)  NOT NULL,   -- ORDSPHS1.CODE
    po_date                   DATE         NULL,       -- ORDSPHS1.SDATE
    first_seen_at             DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_po_customer_link (customer_line_legacy_id, po_no),
    KEY idx_po_customer_link_order (customer_order_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 2. One row per VERIFIED order audit round. ------------------------------------
--    status OPEN    : audit PASS, purchase orders not placed yet
--           DONE    : first linked PO found (done_source LINKED_PO) or a PO
--                     number typed by hand (MANUAL)
--           SKIPPED : audit result not PASS, or the order already had a task
CREATE TABLE IF NOT EXISTS po_placement_task_tb (
    id                   BIGINT       NOT NULL AUTO_INCREMENT,
    audit_task_id        VARCHAR(36)  NOT NULL,   -- pmc_order_audit_task.id
    audit_round          INT          NOT NULL,
    order_no             VARCHAR(50)  NOT NULL,
    audit_result         VARCHAR(16)  NULL,
    audit_verified_at    DATETIME(6)  NOT NULL,   -- business time zone (erp.purchase.zone)
    deadline_at          DATETIME(6)  NOT NULL,   -- audit_verified_at + deadline-days
    status               VARCHAR(16)  NOT NULL,
    assignee_id          BIGINT       NULL,
    assigned_by          BIGINT       NULL,
    assigned_at          DATETIME(6)  NULL,
    done_at              DATETIME(6)  NULL,
    done_source          VARCHAR(16)  NULL,
    done_po_no           VARCHAR(50)  NULL,
    done_by              BIGINT       NULL,
    reminded_at          DATETIME(6)  NULL,
    overdue_notified_at  DATETIME(6)  NULL,
    created_at           DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_placement_audit_task (audit_task_id),
    KEY idx_placement_status_deadline (status, deadline_at),
    KEY idx_placement_assignee (assignee_id, status),
    KEY idx_placement_order_no (order_no)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 3. Sidebar entry (frontend route /purchase/placements, i18n key
--    placementTasks), granted to every purchaser and purchaser_manager user.
INSERT INTO navigation_menu_tb
    (parent_id, title_key, icon, route, color, permission_id, is_enabled, create_time)
SELECT NULL, 'placementTasks', 'mdi-cart-clock', '/purchase/placements', 'deep-orange', p.id, 1, NOW()
FROM permission_tb p
WHERE p.permission_code = 'menu.purchaseOrder.read'
  AND NOT EXISTS (SELECT 1 FROM navigation_menu_tb m WHERE m.title_key = 'placementTasks')
LIMIT 1;

INSERT INTO user_nav_menu (nav_id, permission_id, user_id, is_enabled, create_date)
SELECT DISTINCT m.id, NULL, ur.user_id, 1, NOW()
FROM navigation_menu_tb m
JOIN role_tb r        ON r.role_name IN ('purchaser', 'purchaser_manager')
JOIN user_roles_tb ur ON ur.role_id = r.id AND ur.user_enabled = 1
WHERE m.title_key = 'placementTasks'
  AND NOT EXISTS (SELECT 1 FROM user_nav_menu x WHERE x.nav_id = m.id AND x.user_id = ur.user_id);
