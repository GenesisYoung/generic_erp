-- =============================================================================
--  Authentication & RBAC module - API permission codes
--  Runs after V2__auth_rbac_seed.sql. Re-runnable (every insert is guarded).
--
--  Every controller endpoint is now protected by @PreAuthorize("@perm.has(...)")
--  (see PermissionChecker). Codes are hierarchical: holding a code grants every
--  code beneath it, so the existing role grants of "menu.userManagement" etc.
--  keep full access to that screen. The child codes below exist so a role can be
--  given narrower access, e.g. only "menu.userManagement.read".
--
--  ROOT passes every check regardless of these rows.
--
--  IMPORTANT: before this change, any ADMIN could call every admin endpoint and
--  any logged-in user could call every product endpoint. Section 3 grants ADMIN
--  the equivalent of its old access; USER gets read-only product access. Review
--  section 3 against your real roles before running it in production.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Product permission root (the product screens were previously ungated).
-- -----------------------------------------------------------------------------
INSERT INTO permission_tb (permission_code, is_enabled, create_date)
SELECT 'product', 1, CURDATE()
WHERE NOT EXISTS (SELECT 1 FROM permission_tb WHERE permission_code = 'product');

-- -----------------------------------------------------------------------------
-- 2. Action children: <resource>.read / .create / .update / .delete
--    (+ product.import). PermissionSpecification.findMenuPermission() excludes
--    "menu.%.%", so these children do not show up as selectable menus.
-- -----------------------------------------------------------------------------
INSERT INTO permission_tb (permission_code, is_enabled, create_date)
SELECT CONCAT(r.code, '.', a.action), 1, CURDATE()
FROM (
    SELECT 'menu.userManagement'       AS code UNION ALL
    SELECT 'menu.departmentManagement'         UNION ALL
    SELECT 'menu.roleManagement'               UNION ALL
    SELECT 'menu.menuManagement'               UNION ALL
    SELECT 'menu.permissionManagement'         UNION ALL
    SELECT 'product'
) r
CROSS JOIN (
    SELECT 'read' AS action UNION ALL
    SELECT 'create'         UNION ALL
    SELECT 'update'         UNION ALL
    SELECT 'delete'
) a
WHERE NOT EXISTS (SELECT 1 FROM permission_tb p WHERE p.permission_code = CONCAT(r.code, '.', a.action));

INSERT INTO permission_tb (permission_code, is_enabled, create_date)
SELECT 'product.import', 1, CURDATE()
WHERE NOT EXISTS (SELECT 1 FROM permission_tb WHERE permission_code = 'product.import');

-- -----------------------------------------------------------------------------
-- 3. Default role grants (created_by = root user, as in V2).
--    ROOT : every permission (cosmetic - ROOT bypasses checks - but keeps the
--           role screen accurate).
--    ADMIN: all admin screens + full product access (its previous access).
--    USER : read-only products.
-- -----------------------------------------------------------------------------
INSERT INTO role_permission_tb (role_id, permission_id, menu_id, created_by, create_time, create_date)
SELECT r.id, p.id, NULL, u.id, NOW(6), CURDATE()
FROM role_tb r
JOIN permission_tb p ON (
       r.role_name = 'ROOT'
    OR (r.role_name = 'ADMIN' AND p.permission_code IN (
            'menu.dashboard', 'menu.userManagement', 'menu.departmentManagement',
            'menu.roleManagement', 'menu.menuManagement', 'menu.permissionManagement', 'product'))
    OR (r.role_name = 'USER' AND p.permission_code = 'product.read'))
CROSS JOIN user_tb u
WHERE u.username = 'root'
  AND p.is_enabled = 1
  AND NOT EXISTS (
      SELECT 1 FROM role_permission_tb rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
