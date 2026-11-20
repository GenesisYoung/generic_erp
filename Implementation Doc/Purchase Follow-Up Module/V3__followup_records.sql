-- =============================================================================
--  Purchase order follow-up module - step 3: follow-up records.
--  Runs after V2__po_sync_and_role_alignment.sql. Re-runnable.
--
--  Hibernate (ddl-auto: update) also creates the table from the entity
--  (FollowUpRecord); this statement is for environments where ddl-auto is off.
--  No permission changes: saving a follow-up uses menu.purchaseOrder.update and
--  the to-do list uses menu.purchaseOrder.read, both granted in V1. Section 2
--  adds the sidebar entry for the to-do page.
-- =============================================================================

CREATE TABLE IF NOT EXISTS po_followup_record_tb (
    id                      BIGINT       NOT NULL AUTO_INCREMENT,
    purchase_order_id       BIGINT       NOT NULL,   -- purchase_order_tb.id (logical)
    checkpoint_id           BIGINT       NULL,       -- po_followup_checkpoint_tb.id it completed; NULL = extra follow-up
    recorded_by             BIGINT       NOT NULL,   -- user_tb.id
    material_status         VARCHAR(16)  NOT NULL,   -- ON_TRACK / AT_RISK / DELAYED
    supplier_promised_date  DATE         NULL,
    missing_order_found     BIT(1)       NOT NULL,
    note                    VARCHAR(500) NULL,
    followed_up_at          DATETIME(6)  NOT NULL,   -- server time in the business zone
    PRIMARY KEY (id),
    KEY idx_followup_record_po (purchase_order_id, followed_up_at),
    KEY idx_followup_record_user (recorded_by, followed_up_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- Sidebar entry for the purchaser's follow-up to-do page (frontend route
-- /purchase/followups, i18n key followUpTodo). The sidebar is per user, so
-- the entry is granted to every user holding purchaser or purchaser_manager.
-- Users who get one of these roles later need the entry granted the usual way.
-- -----------------------------------------------------------------------------
INSERT INTO navigation_menu_tb
    (parent_id, title_key, icon, route, color, permission_id, is_enabled, create_time)
SELECT NULL, 'followUpTodo', 'mdi-phone-check-outline', '/purchase/followups', 'teal', p.id, 1, NOW()
FROM permission_tb p
WHERE p.permission_code = 'menu.purchaseOrder.read'
  AND NOT EXISTS (SELECT 1 FROM navigation_menu_tb m WHERE m.title_key = 'followUpTodo')
LIMIT 1;

INSERT INTO user_nav_menu (nav_id, permission_id, user_id, is_enabled, create_date)
SELECT DISTINCT m.id, NULL, ur.user_id, 1, NOW()
FROM navigation_menu_tb m
JOIN role_tb r       ON r.role_name IN ('purchaser', 'purchaser_manager')
JOIN user_roles_tb ur ON ur.role_id = r.id AND ur.user_enabled = 1
WHERE m.title_key = 'followUpTodo'
  AND NOT EXISTS (SELECT 1 FROM user_nav_menu x WHERE x.nav_id = m.id AND x.user_id = ur.user_id);
