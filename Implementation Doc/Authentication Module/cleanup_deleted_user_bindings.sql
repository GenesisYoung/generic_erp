-- =============================================================================
-- One-off cleanup: unbind access data left behind by users deleted before
-- UserManagementService.deleteUser started removing it.
--
-- "Deleted" = user_tb.is_enabled = 0 (soft delete). Root (id 1) is never touched.
-- Kept on purpose: user_tb row, user_info_tb profile, login logs (audit history).
-- Refresh tokens were already revoked at delete time.
--
-- Run step 1 first and check the counts, then run step 2.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Step 1: preview - how many rows each table will lose.
-- -----------------------------------------------------------------------------
SELECT 'user_roles_tb' AS table_name, COUNT(*) AS rows_to_delete
FROM user_roles_tb t JOIN user_tb u ON u.id = t.user_id
WHERE u.is_enabled = 0 AND u.id <> 1
UNION ALL
SELECT 'user_departments_tb', COUNT(*)
FROM user_departments_tb t JOIN user_tb u ON u.id = t.user_id
WHERE u.is_enabled = 0 AND u.id <> 1
UNION ALL
SELECT 'user_nav_menu', COUNT(*)
FROM user_nav_menu t JOIN user_tb u ON u.id = t.user_id
WHERE u.is_enabled = 0 AND u.id <> 1
UNION ALL
SELECT 'page_permission_approvals_record', COUNT(*)
FROM page_permission_approvals_record t JOIN user_tb u ON u.id = t.user_id
WHERE u.is_enabled = 0 AND u.id <> 1;

-- -----------------------------------------------------------------------------
-- Step 2: delete. All-or-nothing; use ROLLBACK instead of COMMIT to back out.
-- -----------------------------------------------------------------------------
START TRANSACTION;

DELETE t FROM user_roles_tb t
JOIN user_tb u ON u.id = t.user_id
WHERE u.is_enabled = 0 AND u.id <> 1;

DELETE t FROM user_departments_tb t
JOIN user_tb u ON u.id = t.user_id
WHERE u.is_enabled = 0 AND u.id <> 1;

DELETE t FROM user_nav_menu t
JOIN user_tb u ON u.id = t.user_id
WHERE u.is_enabled = 0 AND u.id <> 1;

DELETE t FROM page_permission_approvals_record t
JOIN user_tb u ON u.id = t.user_id
WHERE u.is_enabled = 0 AND u.id <> 1;

COMMIT;
