-- ---------------------------------------------------------------------------
-- Seed: "Department Management" sidebar entry, nested under "User Management".
--
-- The sidebar is data-driven (navigation_menu_tb) and per-user: a menu is only
-- rendered for a user who has a row in user_nav_menu granting it (see
-- UserService#fetchNavMenu). The frontend also needs the PARENT node in the
-- same result set for the child to nest, so this script grants BOTH the
-- existing "userManagement" node and the new child to whoever can already see
-- "userManagement".
--
-- Run once against the generic_erp schema. Idempotent-ish: guarded so re-runs
-- do not create duplicates.
-- ---------------------------------------------------------------------------

-- 1) Create the menu row as a child of the existing userManagement node.
--    title_key must match the i18n key added to the frontend lang files.
INSERT INTO navigation_menu_tb
    (parent_id, title_key, icon, route, color, permission_id, is_enabled, create_time)
SELECT parent.id,
       'departmentManagement',
       'mdi-office-building-cog',
       '/manage/departments',
       'indigo',
       parent.permission_id,
       1,
       NOW()
FROM navigation_menu_tb parent
WHERE parent.title_key = 'userManagement'
  AND NOT EXISTS (
        SELECT 1 FROM navigation_menu_tb existing
        WHERE existing.title_key = 'departmentManagement'
  )
LIMIT 1;

-- 2) Grant the new child to every user who already has the userManagement node,
--    mirroring their existing access so the same audience sees the new page.
INSERT INTO user_nav_menu (nav_id, permission_id, user_id, is_enabled, create_date)
SELECT child.id, NULL, ung.user_id, 1, NOW()
FROM navigation_menu_tb child
JOIN navigation_menu_tb parent ON parent.title_key = 'userManagement'
JOIN user_nav_menu ung        ON ung.nav_id = parent.id AND ung.is_enabled = 1
WHERE child.title_key = 'departmentManagement'
  AND NOT EXISTS (
        SELECT 1 FROM user_nav_menu dup
        WHERE dup.nav_id = child.id AND dup.user_id = ung.user_id
  );
