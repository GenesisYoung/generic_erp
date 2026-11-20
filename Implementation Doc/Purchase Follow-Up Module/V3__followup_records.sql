-- =============================================================================
--  Purchase order follow-up module - step 3: follow-up records.
--  Runs after V2__po_sync_and_role_alignment.sql. Re-runnable.
--
--  Hibernate (ddl-auto: update) also creates the table from the entity
--  (FollowUpRecord); this statement is for environments where ddl-auto is off.
--  No permission changes: saving a follow-up uses menu.purchaseOrder.update and
--  the to-do list uses menu.purchaseOrder.read, both granted in V1.
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
