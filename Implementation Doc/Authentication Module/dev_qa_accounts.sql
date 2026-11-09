-- =============================================================================
--  DEV ONLY - throwaway QA accounts for testing API permissions in the browser.
--  Never run against production. Re-runnable; cleanup is at the bottom.
--
--      qa_root    / QaRoot-351fd5ae2f28   (ROOT: passes every check)
--      qa_tmp     / QaTemp-9c5d2e7a41b3   (created through the UI during browser testing)
--      qa_viewer  / QaView-58e131e171f7   (role QA_VIEWER, see grants below)
--
--  QA_VIEWER holds: menu.userManagement.read, menu.roleManagement.read,
--  menu.roleManagement.update, product.read. It sees the user, allocation and
--  department menus, so the department screen should be refused.
-- =============================================================================

INSERT INTO role_tb (role_name, val, create_date)
SELECT 'QA_VIEWER', 900, CURDATE()
WHERE NOT EXISTS (SELECT 1 FROM role_tb WHERE role_name = 'QA_VIEWER');

INSERT INTO user_tb (username, display_name, email, password, status, failed_attempted, create_date, is_enabled)
SELECT * FROM (
    SELECT 'qa_root' AS username, 'QA Root' AS display_name, 'qa_root@example.test' AS email,
           '$argon2id$v=19$m=65536,t=4,p=2$f0UQMUJxJlR0GdOWCv35ebKwGDbYT5nh8cSPJ+fIZwYCPcOZ4pkXORrsGkNwaQ+b0MDRmuneZbtHQCU24bAadQ$4pt5jKMN8sgPkHi9ERDohrgWZsiKsuJBTslnUmIfknuytHbFFglEUjQP/49Ohr9ZGNA' AS password, 1 AS status, 0 AS failed_attempted, CURDATE() AS create_date, 1 AS is_enabled
    UNION ALL
    SELECT 'qa_viewer', 'QA Viewer', 'qa_viewer@example.test',
           '$argon2id$v=19$m=65536,t=4,p=2$PHIwYJdLFiqGFiRBB50PeachKm1A+QZtuR9U+q+FqyFU5cbMrclSjGk75jvq3xQPzKUljBsJ5VFiP3Ew+PBGKg$fQGWnwp7eWpLGE1VbULKUu7FIbJhLk4MtcZSA3sQ0sVWQdGFYuBYlwrdz3vynoZbD4U', 1, 0, CURDATE(), 1
) seed
WHERE NOT EXISTS (SELECT 1 FROM user_tb u WHERE u.username = seed.username);

INSERT INTO user_roles_tb (user_id, role_id, user_enabled, create_date)
SELECT u.id, r.id, 1, CURDATE()
FROM user_tb u JOIN role_tb r
  ON (u.username = 'qa_root' AND r.role_name = 'ROOT')
  OR (u.username = 'qa_viewer' AND r.role_name = 'QA_VIEWER')
WHERE NOT EXISTS (SELECT 1 FROM user_roles_tb ur WHERE ur.user_id = u.id AND ur.role_id = r.id);

INSERT INTO role_permission_tb (role_id, permission_id, menu_id, created_by, create_time, create_date)
SELECT r.id, p.id, NULL, 1, NOW(6), CURDATE()
FROM role_tb r JOIN permission_tb p ON p.permission_code IN (
    'menu.userManagement.read', 'menu.roleManagement.read', 'menu.roleManagement.update', 'product.read')
WHERE r.role_name = 'QA_VIEWER'
  AND NOT EXISTS (SELECT 1 FROM role_permission_tb rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- Sidebar: qa_root copies user 1's menus; qa_viewer gets the user-management group.
INSERT INTO user_nav_menu (nav_id, permission_id, user_id, is_enabled, create_date)
SELECT m.nav_id, NULL, u.id, 1, NOW(6)
FROM user_tb u JOIN user_nav_menu m ON m.user_id = 1 AND m.permission_id IS NULL
WHERE u.username = 'qa_root'
  AND NOT EXISTS (SELECT 1 FROM user_nav_menu x WHERE x.user_id = u.id AND x.nav_id = m.nav_id);

INSERT INTO user_nav_menu (nav_id, permission_id, user_id, is_enabled, create_date)
SELECT n.id, NULL, u.id, 1, NOW(6)
FROM user_tb u JOIN navigation_menu_tb n ON n.id IN (1, 2, 10, 12, 13)
WHERE u.username = 'qa_viewer'
  AND NOT EXISTS (SELECT 1 FROM user_nav_menu x WHERE x.user_id = u.id AND x.nav_id = n.id);

-- -----------------------------------------------------------------------------
-- Cleanup (run manually when done):
-- DELETE FROM user_nav_menu WHERE user_id IN (SELECT id FROM user_tb WHERE username IN ('qa_root','qa_viewer','qa_tmp'));
-- DELETE FROM user_roles_tb WHERE user_id IN (SELECT id FROM user_tb WHERE username IN ('qa_root','qa_viewer','qa_tmp'));
-- DELETE FROM user_info_tb WHERE user_id IN (SELECT id FROM user_tb WHERE username IN ('qa_root','qa_viewer','qa_tmp'));
-- DELETE FROM user_tb WHERE username IN ('qa_root','qa_viewer','qa_tmp');
-- DELETE FROM role_permission_tb WHERE role_id = (SELECT id FROM role_tb WHERE role_name = 'QA_VIEWER');
-- DELETE FROM role_tb WHERE role_name = 'QA_VIEWER';
-- -----------------------------------------------------------------------------
