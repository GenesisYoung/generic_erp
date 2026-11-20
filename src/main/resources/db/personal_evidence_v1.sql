-- MySQL: independent personal evidence library and a standalone sidebar menu.
-- Existing task evidence and employee authorizations are not modified.
CREATE TABLE IF NOT EXISTS pmc_personal_evidence (
 id VARCHAR(36) NOT NULL PRIMARY KEY,
 created_by VARCHAR(20) NOT NULL,
 file_name VARCHAR(128) NOT NULL,
 storage_key VARCHAR(64) NOT NULL,
 request_hash VARCHAR(64) NOT NULL,
 byte_size BIGINT NOT NULL,
 purpose VARCHAR(1000) NOT NULL,
 created_at DATETIME(6) NOT NULL,
 INDEX ix_personal_evidence_owner(created_by,created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

INSERT INTO permission_tb(permission_code,is_enabled,create_date)
SELECT 'menu.evidence.read',1,CURDATE() WHERE NOT EXISTS(SELECT 1 FROM permission_tb WHERE permission_code='menu.evidence.read');
INSERT INTO permission_tb(permission_code,is_enabled,create_date)
SELECT 'menu.evidence.create',1,CURDATE() WHERE NOT EXISTS(SELECT 1 FROM permission_tb WHERE permission_code='menu.evidence.create');

INSERT INTO navigation_menu_tb(parent_id,title_key,icon,route,color,permission_id,is_enabled,create_time)
SELECT NULL,'evidenceManagement','mdi-file-document-outline','/pmc/evidence','indigo',p.id,1,NOW()
FROM permission_tb p WHERE p.permission_code='menu.evidence.read' AND p.is_enabled=1
AND NOT EXISTS(SELECT 1 FROM navigation_menu_tb WHERE route='/pmc/evidence');

INSERT INTO menu_registered_permissions_record(menu_id,permission_id,permission_enabled,create_date)
SELECT n.id,p.id,1,NOW() FROM navigation_menu_tb n JOIN permission_tb p ON p.permission_code IN ('menu.evidence.read','menu.evidence.create')
WHERE n.route='/pmc/evidence' AND n.is_enabled=1 AND p.is_enabled=1
AND NOT EXISTS(SELECT 1 FROM menu_registered_permissions_record r WHERE r.menu_id=n.id AND r.permission_id=p.id);

-- Existing ROOT users already possess all capabilities. Add only sidebar visibility.
-- No employee receives new menu or upload permissions automatically.
INSERT INTO user_nav_menu(nav_id,permission_id,user_id,is_enabled,create_date)
SELECT DISTINCT n.id,NULL,u.id,1,NOW()
FROM navigation_menu_tb n JOIN user_roles_tb ur ON ur.user_enabled=1
JOIN role_tb r ON r.id=ur.role_id AND r.role_name='ROOT'
JOIN user_tb u ON u.id=ur.user_id AND u.is_enabled=1 AND u.status=1
WHERE n.route='/pmc/evidence' AND n.is_enabled=1
AND NOT EXISTS(SELECT 1 FROM user_nav_menu g WHERE g.nav_id=n.id AND g.user_id=u.id);
