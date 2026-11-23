-- =============================================================================
--  Role dashboards: sidebar entries for the PMC dashboard and the purchasing
--  dashboard. Runs after V7__purchase_exceptions.sql. Re-runnable.
--  No tables. The permission codes menu.pmcDashboard and
--  menu.purchaseDashboard and their grants already exist (V1):
--    pmc_manager, pmc_clerk       -> menu.pmcDashboard      (/pmc/dashboard)
--    purchaser_manager, purchaser -> menu.purchaseDashboard (/purchase/dashboard)
--  The purchasing dashboard shows every purchaser to holders of
--  data.purchaseOrder.all, and only the user's own work to everyone else.
-- =============================================================================

INSERT INTO navigation_menu_tb
    (parent_id, title_key, icon, route, color, permission_id, is_enabled, create_time)
SELECT NULL, seed.title_key, seed.icon, seed.route, seed.color, p.id, 1, NOW()
FROM (
    SELECT 'pmcDashboard' AS title_key, 'mdi-view-dashboard-outline' AS icon, '/pmc/dashboard' AS route,
           'indigo' AS color, 'menu.pmcDashboard' AS code
    UNION ALL
    SELECT 'purchaseDashboard', 'mdi-view-dashboard-variant-outline', '/purchase/dashboard',
           'deep-orange', 'menu.purchaseDashboard'
) AS seed
JOIN permission_tb p ON p.permission_code = seed.code
WHERE NOT EXISTS (SELECT 1 FROM navigation_menu_tb m WHERE m.title_key = seed.title_key);

INSERT INTO user_nav_menu (nav_id, permission_id, user_id, is_enabled, create_date)
SELECT DISTINCT m.id, NULL, ur.user_id, 1, NOW()
FROM navigation_menu_tb m
JOIN role_tb r        ON (m.title_key = 'pmcDashboard'      AND r.role_name IN ('pmc_manager', 'pmc_clerk'))
                      OR (m.title_key = 'purchaseDashboard' AND r.role_name IN ('purchaser_manager', 'purchaser'))
JOIN user_roles_tb ur ON ur.role_id = r.id AND ur.user_enabled = 1
WHERE NOT EXISTS (SELECT 1 FROM user_nav_menu x WHERE x.nav_id = m.id AND x.user_id = ur.user_id);
