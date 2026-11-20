-- One-time repair for the confirmed disabled permission; repeat execution is safe.
-- Does not delete evidence files, menus, permission catalogue rows, or NULL baseline grants.
START TRANSACTION;
SET @deleted_permission_id = 113;
SELECT id, permission_code, is_enabled FROM permission_tb
WHERE id = @deleted_permission_id AND permission_code = 'menu.evidence.create' AND is_enabled = 0
FOR UPDATE;

DELETE r FROM page_permission_approvals_record r JOIN permission_tb p ON p.id = r.permission_id
WHERE p.id = @deleted_permission_id AND p.permission_code = 'menu.evidence.create' AND p.is_enabled = 0;

DELETE r FROM menu_registered_permissions_record r JOIN permission_tb p ON p.id = r.permission_id
WHERE p.id = @deleted_permission_id AND p.permission_code = 'menu.evidence.create' AND p.is_enabled = 0;

DELETE r FROM role_permission_tb r JOIN permission_tb p ON p.id = r.permission_id
WHERE p.id = @deleted_permission_id AND p.permission_code = 'menu.evidence.create' AND p.is_enabled = 0;

DELETE r FROM user_nav_menu r JOIN permission_tb p ON p.id = r.permission_id
WHERE p.id = @deleted_permission_id AND p.permission_code = 'menu.evidence.create' AND p.is_enabled = 0;

DELETE r FROM action_permission_tb r JOIN permission_tb p ON p.id = r.permission_id
WHERE p.id = @deleted_permission_id AND p.permission_code = 'menu.evidence.create' AND p.is_enabled = 0;

COMMIT;
