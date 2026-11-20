-- MySQL: register the complete workflow's permission catalogue and menu options.
-- No user/role grants, approved business rules or task data are changed.
INSERT INTO permission_tb(permission_code,is_enabled,create_date)
SELECT p.code,1,CURDATE() FROM (
 SELECT 'menu.order-audit-task.read' code UNION ALL
 SELECT 'menu.order-audit-task.create' UNION ALL SELECT 'menu.order-audit-task.update' UNION ALL
 SELECT 'menu.order-audit-task.accept' UNION ALL SELECT 'menu.order-audit-task.execute' UNION ALL
 SELECT 'menu.order-audit-task.submit' UNION ALL SELECT 'menu.order-audit-task.verify' UNION ALL
 SELECT 'menu.order-audit-task.confirm' UNION ALL SELECT 'menu.order-audit-task.change.request' UNION ALL
 SELECT 'menu.order-audit-task.approve' UNION ALL SELECT 'menu.order-audit-task.export' UNION ALL
 SELECT 'menu.order-audit-task.rules.configure' UNION ALL SELECT 'menu.order-audit-task.rules.approve' UNION ALL
 SELECT 'pmc.order-audit.scope.team' UNION ALL SELECT 'pmc.order-audit.scope.all'
) p WHERE NOT EXISTS(SELECT 1 FROM permission_tb x WHERE x.permission_code=p.code);

INSERT INTO menu_registered_permissions_record(menu_id,permission_id,permission_enabled,create_date)
SELECT n.id,p.id,1,NOW() FROM navigation_menu_tb n JOIN permission_tb p ON p.permission_code IN (
 'menu.order-audit-task.read','menu.order-audit-task.create','menu.order-audit-task.update',
 'menu.order-audit-task.accept','menu.order-audit-task.execute','menu.order-audit-task.submit',
 'menu.order-audit-task.verify','menu.order-audit-task.confirm','menu.order-audit-task.change.request',
 'menu.order-audit-task.approve','menu.order-audit-task.export','menu.order-audit-task.rules.configure',
 'menu.order-audit-task.rules.approve','pmc.order-audit.scope.team','pmc.order-audit.scope.all')
WHERE n.route='/pmc/order-audit-task' AND n.is_enabled=1 AND p.is_enabled=1
AND NOT EXISTS(SELECT 1 FROM menu_registered_permissions_record r WHERE r.menu_id=n.id AND r.permission_id=p.id);
