-- MySQL 8: order-audit module, manual deployment script, NOT executed by this change.
-- Back up first. DDL auto-commits in MySQL. No legacy order_assessment_task data is rewritten.
-- Datetimes store UTC; use hibernate.jdbc.time_zone=UTC and a UTC JDBC session.
CREATE TABLE IF NOT EXISTS pmc_order_audit_task (
  id VARCHAR(36) NOT NULL PRIMARY KEY,
  order_number VARCHAR(50) NOT NULL,
  review_round INT NOT NULL,
  owner_user_id VARCHAR(20) NULL,
  created_by VARCHAR(20) NOT NULL,
  department_id VARCHAR(20) NULL,
  next_owner_user_id VARCHAR(20) NULL,
  status VARCHAR(24) NOT NULL,
  accepted_at DATETIME(6) NULL,
  effective_due_at DATETIME(6) NULL,
  completed_at DATETIME(6) NULL,
  row_version BIGINT NOT NULL DEFAULT 0,
  document LONGTEXT NOT NULL,
  CONSTRAINT uq_pmc_order_round UNIQUE(order_number,review_round),
  INDEX ix_pmc_audit_due(status,effective_due_at),
  INDEX ix_pmc_audit_owner(owner_user_id),
  INDEX ix_pmc_audit_dept(department_id),
  INDEX ix_pmc_audit_creator(created_by),
  INDEX ix_pmc_audit_next_owner(next_owner_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS pmc_order_audit_rule (
  id INT NOT NULL PRIMARY KEY,
  row_version BIGINT NOT NULL DEFAULT 0,
  document LONGTEXT NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO pmc_order_audit_rule(id,row_version,document)
SELECT 1,0,'{"version":0,"requestedBy":null,"approvedBy":null,"approvedAt":null,"draft":null,"approved":null,"history":[]}'
WHERE NOT EXISTS(SELECT 1 FROM pmc_order_audit_rule WHERE id=1);

CREATE TABLE IF NOT EXISTS pmc_order_audit_operation (
  id VARCHAR(160) NOT NULL PRIMARY KEY,
  request_hash VARCHAR(64) NOT NULL,
  task_id VARCHAR(36) NULL,
  response LONGTEXT NOT NULL,
  CONSTRAINT fk_pmc_operation_task FOREIGN KEY(task_id) REFERENCES pmc_order_audit_task(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE IF NOT EXISTS pmc_order_audit_evidence (
  id VARCHAR(36) NOT NULL PRIMARY KEY,
  task_id VARCHAR(36) NOT NULL,
  created_by VARCHAR(20) NOT NULL,
  file_name VARCHAR(128) NOT NULL,
  storage_key VARCHAR(64) NOT NULL,
  request_hash VARCHAR(64) NOT NULL,
  byte_size BIGINT NOT NULL,
  purpose VARCHAR(1000) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  INDEX ix_pmc_evidence_task(task_id),
  CONSTRAINT fk_pmc_evidence_task FOREIGN KEY(task_id) REFERENCES pmc_order_audit_task(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- Register permissions only. No grants, user bindings, or production business rules are inserted.
INSERT INTO permission_tb(permission_code,is_enabled,create_date)
SELECT p.code,1,CURDATE()
FROM (
  SELECT 'menu.order-audit-task.read' AS code UNION ALL
  SELECT 'menu.order-audit-task.create' UNION ALL
  SELECT 'menu.order-audit-task.update' UNION ALL
  SELECT 'menu.order-audit-task.accept' UNION ALL
  SELECT 'menu.order-audit-task.execute' UNION ALL
  SELECT 'menu.order-audit-task.submit' UNION ALL
  SELECT 'menu.order-audit-task.verify' UNION ALL
  SELECT 'menu.order-audit-task.confirm' UNION ALL
  SELECT 'menu.order-audit-task.change.request' UNION ALL
  SELECT 'menu.order-audit-task.approve' UNION ALL
  SELECT 'menu.order-audit-task.export' UNION ALL
  SELECT 'menu.order-audit-task.rules.configure' UNION ALL
  SELECT 'menu.order-audit-task.rules.approve' UNION ALL
  SELECT 'pmc.order-audit.scope.team' UNION ALL
  SELECT 'pmc.order-audit.scope.all'
) p
WHERE NOT EXISTS(SELECT 1 FROM permission_tb e WHERE e.permission_code=p.code);
