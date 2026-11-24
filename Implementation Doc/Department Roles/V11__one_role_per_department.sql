-- =============================================================================
--  One role per department (每个部门一个角色).
--  Runs after V10 (exception approval). It does not depend on V10, so it can
--  also run right after V9__followup_templates.sql. Re-runnable.
--
--  Before: each department had a manager role and a clerk role
--          (pmc_manager + pmc_clerk, purchaser_manager + purchaser).
--  After : each department has ONE role. Its permissions are the default
--          permissions of every member of the department. The "By Permission"
--          screen can still override a single permission for one user on one
--          page (grant it, or take the default away).
--
--          pmc_manager + pmc_clerk         -> pmc
--          purchaser_manager + purchaser   -> purchaser
--
--  The merged role keeps the union of both roles' permissions, so nobody
--  loses anything they could do before. One exception: the order review
--  scope. Tasks are now assigned to yourself or a member of your own
--  department, so "pmc.order-audit.scope.all" (every department) is replaced
--  by "pmc.order-audit.scope.team" (your departments) on the pmc role.
--  Review the merged permissions on Role Management after running.
--
--  Steps:
--    1. Merge the PMC roles into "pmc".
--    2. Merge the purchasing roles into "purchaser".
--    3. Order review scope: team instead of all on "pmc".
--    4. Keep one role per department.
--    5. Page overrides: drop old "not granted" rows (permission_enabled = 0).
--       From now on 0 means "take the department default away".
--    6. Checks (read only).
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. PMC: pmc_manager + pmc_clerk -> pmc
-- -----------------------------------------------------------------------------
SET @keep := (SELECT id FROM role_tb WHERE role_name = 'pmc' LIMIT 1);
SET @mgr  := (SELECT id FROM role_tb WHERE role_name = 'pmc_manager' LIMIT 1);
-- First run: the manager role becomes "pmc" (it keeps its id and val).
UPDATE role_tb SET role_name = 'pmc' WHERE id = @mgr AND @keep IS NULL;
SET @keep := COALESCE(@keep, @mgr);
-- When neither role exists yet, create "pmc" with the next free val code.
INSERT INTO role_tb (role_name, val, create_date)
SELECT 'pmc', (SELECT COALESCE(MAX(v.val), 9) + 1 FROM (SELECT val FROM role_tb) v), CURDATE()
FROM DUAL WHERE @keep IS NULL;
SET @keep := (SELECT id FROM role_tb WHERE role_name = 'pmc' LIMIT 1);

-- Merge every other old PMC role into @keep.
SET @old := (SELECT id FROM role_tb WHERE role_name = 'pmc_clerk' LIMIT 1);

-- permissions: move what @keep does not have yet, drop the rest
UPDATE role_permission_tb SET role_id = @keep
WHERE role_id = @old AND @old <> @keep
  AND permission_id NOT IN (SELECT k.permission_id FROM (SELECT permission_id FROM role_permission_tb WHERE role_id = @keep) k);
DELETE FROM role_permission_tb WHERE role_id = @old AND @old <> @keep;
-- users bound directly to the old role
UPDATE user_roles_tb SET role_id = @keep
WHERE role_id = @old AND @old <> @keep
  AND user_id NOT IN (SELECT k.user_id FROM (SELECT user_id FROM user_roles_tb WHERE role_id = @keep) k);
DELETE FROM user_roles_tb WHERE role_id = @old AND @old <> @keep;
-- departments linked to the old role
UPDATE role_department_tb SET role_id = @keep
WHERE role_id = @old AND @old <> @keep
  AND department_id NOT IN (SELECT k.department_id FROM (SELECT department_id FROM role_department_tb WHERE role_id = @keep) k);
DELETE FROM role_department_tb WHERE role_id = @old AND @old <> @keep;
-- the old role itself
DELETE FROM role_tb WHERE id = @old AND @old <> @keep;

-- -----------------------------------------------------------------------------
-- 2. Purchasing: purchaser_manager + purchaser -> purchaser
-- -----------------------------------------------------------------------------
SET @keep := (SELECT id FROM role_tb WHERE role_name = 'purchaser' LIMIT 1);
SET @mgr  := (SELECT id FROM role_tb WHERE role_name = 'purchaser_manager' LIMIT 1);
UPDATE role_tb SET role_name = 'purchaser' WHERE id = @mgr AND @keep IS NULL;
SET @keep := COALESCE(@keep, @mgr);
INSERT INTO role_tb (role_name, val, create_date)
SELECT 'purchaser', (SELECT COALESCE(MAX(v.val), 9) + 1 FROM (SELECT val FROM role_tb) v), CURDATE()
FROM DUAL WHERE @keep IS NULL;
SET @keep := (SELECT id FROM role_tb WHERE role_name = 'purchaser' LIMIT 1);

SET @old := (SELECT id FROM role_tb WHERE role_name = 'purchaser_manager' LIMIT 1);

UPDATE role_permission_tb SET role_id = @keep
WHERE role_id = @old AND @old <> @keep
  AND permission_id NOT IN (SELECT k.permission_id FROM (SELECT permission_id FROM role_permission_tb WHERE role_id = @keep) k);
DELETE FROM role_permission_tb WHERE role_id = @old AND @old <> @keep;
UPDATE user_roles_tb SET role_id = @keep
WHERE role_id = @old AND @old <> @keep
  AND user_id NOT IN (SELECT k.user_id FROM (SELECT user_id FROM user_roles_tb WHERE role_id = @keep) k);
DELETE FROM user_roles_tb WHERE role_id = @old AND @old <> @keep;
UPDATE role_department_tb SET role_id = @keep
WHERE role_id = @old AND @old <> @keep
  AND department_id NOT IN (SELECT k.department_id FROM (SELECT department_id FROM role_department_tb WHERE role_id = @keep) k);
DELETE FROM role_department_tb WHERE role_id = @old AND @old <> @keep;
DELETE FROM role_tb WHERE id = @old AND @old <> @keep;

-- -----------------------------------------------------------------------------
-- 3. Order review scope on "pmc": your departments instead of every department.
-- -----------------------------------------------------------------------------
INSERT INTO role_permission_tb (role_id, permission_id, menu_id, created_by, create_time, create_date)
SELECT r.id, p.id, NULL, u.id, NOW(6), CURDATE()
FROM role_tb r
JOIN permission_tb p ON p.permission_code = 'pmc.order-audit.scope.team'
CROSS JOIN user_tb u
WHERE r.role_name = 'pmc' AND u.username = 'root'
  AND NOT EXISTS (SELECT 1 FROM role_permission_tb rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

DELETE rp FROM role_permission_tb rp
JOIN role_tb r ON r.id = rp.role_id
JOIN permission_tb p ON p.id = rp.permission_id
WHERE r.role_name = 'pmc' AND p.permission_code = 'pmc.order-audit.scope.all';

-- -----------------------------------------------------------------------------
-- 4. One role per department. When a department is still linked to more than
--    one role, keep the link with the lowest id and remove the others. The
--    Department Management screen now replaces the role instead of adding one.
-- -----------------------------------------------------------------------------
DELETE rd FROM role_department_tb rd
JOIN (SELECT department_id, MIN(id) AS keep_id FROM role_department_tb GROUP BY department_id) k
  ON k.department_id = rd.department_id
WHERE rd.id <> k.keep_id;

-- -----------------------------------------------------------------------------
-- 5. Page overrides. Until now a row with permission_enabled = 0 meant
--    "not granted". From now on it means "take the department default away",
--    so old 0 rows are removed to keep everyone's access unchanged.
-- -----------------------------------------------------------------------------
DELETE FROM page_permission_approvals_record WHERE permission_enabled = 0;

-- -----------------------------------------------------------------------------
-- 6. Checks (read only). Run and review the results.
-- -----------------------------------------------------------------------------
-- 6a. Each department and its one role (NULL = no role yet: link one on the
--     Department Management screen, or members get no default permissions).
SELECT d.id AS dept_id, d.dept_name, r.role_name
FROM department_tb d
LEFT JOIN role_department_tb rd ON rd.department_id = d.id
LEFT JOIN role_tb r ON r.id = rd.role_id
ORDER BY d.id;

-- 6b. Users still bound directly to a business role. They keep it, but the
--     department role is now the normal source. Remove the direct binding on
--     User Management when the user's department already gives the same role.
SELECT u.username, r.role_name
FROM user_roles_tb ur
JOIN user_tb u ON u.id = ur.user_id
JOIN role_tb r ON r.id = ur.role_id
WHERE r.role_name NOT IN ('ROOT', 'ADMIN', 'USER')
ORDER BY u.username;

-- 6c. Business users without a department (they get no default permissions
--     and cannot be assigned tasks by colleagues).
SELECT u.id, u.username
FROM user_tb u
WHERE u.is_enabled = 1
  AND NOT EXISTS (SELECT 1 FROM user_departments_tb ud WHERE ud.user_id = u.id)
ORDER BY u.id;
