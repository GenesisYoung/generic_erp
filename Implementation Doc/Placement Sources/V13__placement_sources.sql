-- =============================================================================
--  Placement sources (采购下单任务数据来源). Purchase placement tasks are now
--  made from the old ERP's confirmation status instead of the PMC order review:
--    - sample orders (order no. YP...)     <- purchase applications (采购申请)
--    - production orders (order no. P...)  <- purchase plans (采购计划)
--  A document that is not confirmed for purchase gets one task per order it
--  buys for; the task closes when the old ERP confirms it.
--  Runs after V12 (V11 = one role per department, V12 = follow-up plan groups).
--  Re-runnable.
--
--  RUN THIS BEFORE STARTING THE NEW BACKEND: Hibernate (ddl-auto: update) adds
--  the new columns itself, but it does not drop NOT NULL from audit_task_id,
--  audit_round and audit_verified_at, and new tasks leave those empty.
--  No new permissions or menu entries: the same page /purchase/placements now
--  has two tabs (Production orders / Sample orders).
-- =============================================================================

SET @db := DATABASE();

-- 1. Order-audit columns become optional (new tasks have no audit). ------------
ALTER TABLE po_placement_task_tb
    MODIFY COLUMN audit_task_id     VARCHAR(36) NULL,
    MODIFY COLUMN audit_round       INT         NULL,
    MODIFY COLUMN audit_verified_at DATETIME(6) NULL;

-- 2. New columns (added only when missing). ---------------------------------------
SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @db
                AND TABLE_NAME = 'po_placement_task_tb' AND COLUMN_NAME = 'order_type') = 0,
    'ALTER TABLE po_placement_task_tb ADD COLUMN order_type VARCHAR(16) NULL AFTER order_no', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @db
                AND TABLE_NAME = 'po_placement_task_tb' AND COLUMN_NAME = 'source') = 0,
    'ALTER TABLE po_placement_task_tb ADD COLUMN source VARCHAR(24) NULL AFTER order_type', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @db
                AND TABLE_NAME = 'po_placement_task_tb' AND COLUMN_NAME = 'source_no') = 0,
    'ALTER TABLE po_placement_task_tb ADD COLUMN source_no VARCHAR(50) NULL AFTER source', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = @db
                AND TABLE_NAME = 'po_placement_task_tb' AND COLUMN_NAME = 'started_at') = 0,
    'ALTER TABLE po_placement_task_tb ADD COLUMN started_at DATETIME(6) NULL AFTER source_no', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 3. Existing rows came from order audits. -------------------------------------
UPDATE po_placement_task_tb
SET source     = COALESCE(source, 'ORDER_AUDIT'),
    source_no  = COALESCE(source_no, audit_task_id),
    started_at = COALESCE(started_at, audit_verified_at),
    order_type = COALESCE(order_type,
                     CASE WHEN UPPER(TRIM(order_no)) LIKE 'YP%' THEN 'SAMPLE'
                          WHEN UPPER(TRIM(order_no)) LIKE 'P%'  THEN 'PRODUCTION'
                          ELSE 'OTHER' END);

-- 4. Indexes (added only when missing). -------------------------------------------
SET @sql := IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = @db
                AND TABLE_NAME = 'po_placement_task_tb' AND INDEX_NAME = 'uq_placement_source_doc') = 0,
    'ALTER TABLE po_placement_task_tb ADD UNIQUE KEY uq_placement_source_doc (source, source_no, order_no)',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = @db
                AND TABLE_NAME = 'po_placement_task_tb' AND INDEX_NAME = 'idx_placement_type_status') = 0,
    'ALTER TABLE po_placement_task_tb ADD KEY idx_placement_type_status (order_type, status)',
    'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

-- 5. Check: tasks per section and source. -----------------------------------------
SELECT order_type, source, status, COUNT(*) AS tasks
FROM po_placement_task_tb
GROUP BY order_type, source, status
ORDER BY order_type, source, status;
