-- =============================================================================
--  Authentication & RBAC module - initial data
--  Runs after V1__auth_rbac_baseline.sql
--
--  Every statement is guarded with WHERE NOT EXISTS, matching the style of the
--  existing src/main/resources/db/nav_department_management.sql, so re-running
--  this file is safe.
--
--  ROOT CREDENTIALS
--  ----------------
--      username: root
--      password: ChangeMe@2026
--
--  The hash below is a real Argon2id digest produced with this project's own
--  creation parameters, matching AuthenticationImpl.generatePass():
--      Argon2PasswordEncoder(saltLength=64, hashLength=50, parallelism=2,
--                            memory=65536 KB, iterations=4)
--  Spring's Argon2PasswordEncoder.matches() reads m, t, p and the salt out of
--  the encoded string, so it verifies against the configured bean directly.
--
--  >>> CHANGE THIS PASSWORD IMMEDIATELY AFTER THE FIRST LOGIN. <<<
--  A default credential committed to a repository is public knowledge the
--  moment that repository is shared.
--
--  THREE THINGS TO VERIFY BEFORE RUNNING - each marked "VERIFY" below:
--    1. Menu routes. Only /manage/departments is evidenced in the codebase
--       (nav_department_management.sql). The other five follow the same pattern
--       but must match your Vue Router config, or the sidebar links lead
--       nowhere.
--    2. role_tb.val values. Semantics undocumented (finding F6); sequential
--       integers are used pending confirmation.
--    3. permission_tb.permission_name / val. Populated to mirror
--       permission_code so navigation_permission_view returns something
--       readable, pending the outcome of finding F1.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Actions - the four CRUD verbs named in the Action entity javadoc.
-- -----------------------------------------------------------------------------
INSERT INTO actions_tb (id, action_name, create_date)
SELECT * FROM (
    SELECT 1 AS id, 'CREATE' AS action_name, CURDATE() AS create_date UNION ALL
    SELECT 2,       'READ',                  CURDATE()                UNION ALL
    SELECT 3,       'UPDATE',                CURDATE()                UNION ALL
    SELECT 4,       'DELETE',                CURDATE()
) AS seed
WHERE NOT EXISTS (SELECT 1 FROM actions_tb WHERE actions_tb.action_name = seed.action_name);

-- -----------------------------------------------------------------------------
-- 2. Roles
--    VERIFY (F6): `val` is the external role code carried by UserDTO.roleList.
--    Its semantics are undocumented. If it turns out to be a bitmask, these
--    values must be powers of two (1, 2, 4) instead of sequential.
-- -----------------------------------------------------------------------------
INSERT INTO role_tb (id, role_name, val, create_date)
SELECT * FROM (
    SELECT 1 AS id, 'ROOT'  AS role_name, 1 AS val, CURDATE() AS create_date UNION ALL
    SELECT 2,       'ADMIN',              2,        CURDATE()                UNION ALL
    SELECT 3,       'USER',               3,        CURDATE()
) AS seed
WHERE NOT EXISTS (SELECT 1 FROM role_tb WHERE role_tb.role_name = seed.role_name);

-- -----------------------------------------------------------------------------
-- 3. Departments - one root node so new users have somewhere to belong.
-- -----------------------------------------------------------------------------
INSERT INTO department_tb (id, dept_name, val, parent_id, create_date)
SELECT 1, 'Head Office', 1, NULL, CURDATE()
WHERE NOT EXISTS (SELECT 1 FROM department_tb WHERE dept_name = 'Head Office');

-- -----------------------------------------------------------------------------
-- 4. Permissions
--    The `menu.` prefix is load-bearing: PermissionSpecification
--    .findMenuPermission() selects rows LIKE 'menu.%'. Do not rename it.
--    VERIFY (F1): permission_name and val are populated only so that
--    navigation_permission_view has something to show. Drop both columns and
--    these values if the view is rewritten to use permission_code.
-- -----------------------------------------------------------------------------
INSERT INTO permission_tb (id, permission_code, is_enabled, create_date, permission_name, val)
SELECT * FROM (
    SELECT 1 AS id, 'menu.dashboard'            AS permission_code, 1 AS is_enabled, CURDATE() AS create_date, 'Dashboard'             AS permission_name, 1 AS val UNION ALL
    SELECT 2,       'menu.userManagement',                          1,               CURDATE(),                'User Management',                          2        UNION ALL
    SELECT 3,       'menu.departmentManagement',                    1,               CURDATE(),                'Department Management',                    3        UNION ALL
    SELECT 4,       'menu.roleManagement',                          1,               CURDATE(),                'Role Management',                          4        UNION ALL
    SELECT 5,       'menu.menuManagement',                          1,               CURDATE(),                'Menu Management',                          5        UNION ALL
    SELECT 6,       'menu.permissionManagement',                    1,               CURDATE(),                'Permission Management',                    6
) AS seed
WHERE NOT EXISTS (SELECT 1 FROM permission_tb WHERE permission_tb.permission_code = seed.permission_code);

-- -----------------------------------------------------------------------------
-- 5. Navigation menu
--    title_key must match an i18n key in the frontend language files, or the
--    sidebar renders the raw key as its label.
--    VERIFY: only '/manage/departments' (with icon mdi-office-building-cog and
--    colour indigo) is evidenced in the codebase. Check the other five routes
--    against your Vue Router configuration before running.
--    Top-level items are inserted first so children can reference them.
-- -----------------------------------------------------------------------------
INSERT INTO navigation_menu_tb (id, parent_id, title_key, icon, route, color, permission_id, is_enabled, create_time)
SELECT * FROM (
    SELECT 1 AS id, NULL AS parent_id, 'dashboard'            AS title_key, 'mdi-view-dashboard' AS icon, '/dashboard'        AS route, 'indigo' AS color, '1' AS permission_id, b'1' AS is_enabled, NOW(6) AS create_time UNION ALL
    SELECT 2,       NULL,              'userManagement',                    'mdi-account-group',        '/manage/users',              'indigo',          '2',                 b'1',                NOW(6)                UNION ALL
    SELECT 5,       NULL,              'menuManagement',                    'mdi-menu',                 '/manage/menus',              'indigo',          '5',                 b'1',                NOW(6)                UNION ALL
    SELECT 6,       NULL,              'permissionManagement',              'mdi-key-chain',            '/manage/permissions',        'indigo',          '6',                 b'1',                NOW(6)
) AS seed
WHERE NOT EXISTS (SELECT 1 FROM navigation_menu_tb WHERE navigation_menu_tb.title_key = seed.title_key);

-- Children of userManagement. departmentManagement reproduces the exact values
-- from nav_department_management.sql.
INSERT INTO navigation_menu_tb (id, parent_id, title_key, icon, route, color, permission_id, is_enabled, create_time)
SELECT * FROM (
    SELECT 3 AS id, 2 AS parent_id, 'departmentManagement' AS title_key, 'mdi-office-building-cog' AS icon, '/manage/departments' AS route, 'indigo' AS color, '3' AS permission_id, b'1' AS is_enabled, NOW(6) AS create_time UNION ALL
    SELECT 4,       2,              'roleManagement',                    'mdi-shield-account',            '/manage/roles',              'indigo',          '4',                 b'1',                NOW(6)
) AS seed
WHERE NOT EXISTS (SELECT 1 FROM navigation_menu_tb WHERE navigation_menu_tb.title_key = seed.title_key);

-- -----------------------------------------------------------------------------
-- 6. Root user (id 1)
--    Id 1 is hard-wired: RoleManagementService.currentUserId() falls back to it
--    for the created_by audit column when no principal is present, and
--    Language_EN.CANT_DELETE_ROOT_USER protects it from deletion.
--
--    status = 1 AND is_enabled = 1 are BOTH required by UserDetail.isEnabled().
-- -----------------------------------------------------------------------------
INSERT INTO user_tb (id, username, display_name, email, password, status,
                     current_session_id, failed_attempted, locked_until, create_date, is_enabled)
SELECT 1,
       'root',
       'Root Administrator',
       'root@example.com',
       '$argon2id$v=19$m=65536,t=4,p=2$TypoRb1lFVSUiU9F0qWamqZmFHhRjlH+JYMNeMzFy1bKAtBUz+kvmBSQBr950HIFVzVSGQpsZ7xlaTmdiVf4/w$cVdU32sOSqIUpU554dXEKLg3U1HA+gZ77UoleMDkEygpe6upbvBcGb1pAWwbqnPvzco',
       b'1',
       NULL,
       0,
       NULL,
       CURDATE(),
       1
WHERE NOT EXISTS (SELECT 1 FROM user_tb WHERE username = 'root');

INSERT INTO user_info_tb (user_id, real_name, title, birthday, hire_date, create_date)
SELECT u.id, 'Root Administrator', 'System Administrator', NULL, CURDATE(), CURDATE()
FROM user_tb u
WHERE u.username = 'root'
  AND NOT EXISTS (SELECT 1 FROM user_info_tb i WHERE i.user_id = u.id);

-- -----------------------------------------------------------------------------
-- 7. Root user assignments
-- -----------------------------------------------------------------------------
INSERT INTO user_roles_tb (user_id, role_id, create_date)
SELECT u.id, r.id, CURDATE()
FROM user_tb u
JOIN role_tb r ON r.role_name = 'ROOT'
WHERE u.username = 'root'
  AND NOT EXISTS (SELECT 1 FROM user_roles_tb ur WHERE ur.user_id = u.id AND ur.role_id = r.id);

INSERT INTO user_departments_tb (user_id, dept_id, create_date)
SELECT u.id, d.id, CURDATE()
FROM user_tb u
JOIN department_tb d ON d.dept_name = 'Head Office'
WHERE u.username = 'root'
  AND NOT EXISTS (SELECT 1 FROM user_departments_tb ud WHERE ud.user_id = u.id AND ud.dept_id = d.id);

-- -----------------------------------------------------------------------------
-- 8. Menu access - Path A: direct grant of every menu to root.
--    Without at least one of these, root logs in successfully but sees an empty
--    sidebar, because UserService.fetchNavMenu returns only granted menus.
-- -----------------------------------------------------------------------------
INSERT INTO user_nav_menu (nav_id, permission_id, user_id, is_enabled, create_date)
SELECT n.id, NULL, u.id, b'1', NOW(6)
FROM navigation_menu_tb n
CROSS JOIN user_tb u
WHERE u.username = 'root'
  AND NOT EXISTS (SELECT 1 FROM user_nav_menu m WHERE m.nav_id = n.id AND m.user_id = u.id);

-- -----------------------------------------------------------------------------
-- 9. Menu access - Path B, step 1: register each menu.* permission against the
--    menu it names. Registration must exist before any user can be approved for
--    that permission on that menu (MenuAccessService.listPermissionsForMenu
--    only offers registered permissions).
--
--    The join works because permission_code is 'menu.' + title_key.
-- -----------------------------------------------------------------------------
INSERT INTO menu_registered_permissions_record (menu_id, permission_id, create_date)
SELECT n.id, p.id, NOW(6)
FROM navigation_menu_tb n
JOIN permission_tb p ON p.permission_code = CONCAT('menu.', n.title_key)
WHERE NOT EXISTS (
    SELECT 1 FROM menu_registered_permissions_record r
    WHERE r.menu_id = n.id AND r.permission_id = p.id
);

-- -----------------------------------------------------------------------------
-- 10. Role permissions - ROOT holds every seeded permission.
--     created_by = the root user id, matching the service-layer fallback.
-- -----------------------------------------------------------------------------
INSERT INTO role_permission_tb (role_id, permission_id, menu_id, created_by, create_time, create_date)
SELECT r.id, p.id, NULL, u.id, NOW(6), CURDATE()
FROM role_tb r
CROSS JOIN permission_tb p
CROSS JOIN user_tb u
WHERE r.role_name = 'ROOT'
  AND u.username = 'root'
  AND NOT EXISTS (
      SELECT 1 FROM role_permission_tb rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- -----------------------------------------------------------------------------
-- 11. Action permissions - all four CRUD verbs on every seeded permission.
--     Narrow this later per permission; the seed opens everything so the first
--     login can actually administer the system.
-- -----------------------------------------------------------------------------
INSERT INTO action_permission_tb (permission_id, action_id, create_date)
SELECT p.id, a.id, CURDATE()
FROM permission_tb p
CROSS JOIN actions_tb a
WHERE NOT EXISTS (
    SELECT 1 FROM action_permission_tb ap
    WHERE ap.permission_id = p.id AND ap.action_id = a.id
);

-- -----------------------------------------------------------------------------
-- 12. Keep AUTO_INCREMENT counters ahead of the explicit ids used above.
--     Without this, the next application-created row collides with a seeded id.
-- -----------------------------------------------------------------------------
ALTER TABLE actions_tb          AUTO_INCREMENT = 100;
ALTER TABLE role_tb             AUTO_INCREMENT = 100;
ALTER TABLE department_tb       AUTO_INCREMENT = 100;
ALTER TABLE permission_tb       AUTO_INCREMENT = 100;
ALTER TABLE navigation_menu_tb  AUTO_INCREMENT = 100;
ALTER TABLE user_tb             AUTO_INCREMENT = 100;
