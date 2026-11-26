-- =============================================================================
--  Purchase quotation (采购报价方案). Purchasers pick an order whose purchase
--  plan exists and nothing is bought yet; the system prices its product BOM
--  and packaging BOM with the purchase strategy (采购策略) prices of the old
--  ERP, adds the fees and the profit margin, and exports an .xlsx file.
--  Runs after V13__placement_sources.sql. Re-runnable.
--
--  Hibernate (ddl-auto: update) also creates the table from the entity
--  QuotationRate; section 1 is for environments where ddl-auto is off. The
--  table may stay empty: the default rates then apply (shipping 0.5%,
--  loading 2.5%, management 15%, material loss 1%, development 2%, tax 6% (default 0 since 2026-11-26: prices include tax);
--  margin 15% below 3,000 and 10% from 3,000).
-- =============================================================================

-- 1. Fee rates and profit margin tiers. -----------------------------------------
CREATE TABLE IF NOT EXISTS quotation_rate_tb (
    id            BIGINT         NOT NULL AUTO_INCREMENT,
    kind          VARCHAR(10)    NOT NULL,   -- FEE or MARGIN
    code          VARCHAR(40)    NULL,       -- FEE: SHIPPING, LOADING, MANAGEMENT, MATERIAL_LOSS, DEVELOPMENT, TAX
    base          VARCHAR(10)    NULL,       -- FEE: MATERIAL, COST or PRICE
    rate_percent  DECIMAL(9, 4)  NOT NULL,   -- 2.5 = 2.5%
    min_amount    DECIMAL(18, 2) NULL,       -- MARGIN: tier applies from this cost (before profit)
    sort_order    INT            NOT NULL,
    is_enabled    BIT(1)         NOT NULL,
    updated_at    DATETIME(6)    NULL,
    updated_by    BIGINT         NULL,       -- user_tb.id (logical)
    PRIMARY KEY (id),
    KEY idx_quotation_rate_kind (kind, sort_order)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 2. Permissions: menu.quotation with .read (use the page) / .update (change rates).
INSERT INTO permission_tb (permission_code, is_enabled, create_date)
SELECT seed.code, 1, CURDATE()
FROM (
    SELECT 'menu.quotation'        AS code UNION ALL
    SELECT 'menu.quotation.read'           UNION ALL
    SELECT 'menu.quotation.update'
) AS seed
WHERE NOT EXISTS (SELECT 1 FROM permission_tb p WHERE p.permission_code = seed.code);

-- 3. Grants: the purchasing department role gets everything (after V11 it is
--    "purchaser"; before V11 also "purchaser_manager"). Other users can be
--    given the page on the "By Permission" page.
INSERT INTO role_permission_tb (role_id, permission_id, menu_id, created_by, create_time, create_date)
SELECT r.id, p.id, NULL, u.id, NOW(6), CURDATE()
FROM role_tb r
JOIN permission_tb p ON p.permission_code = 'menu.quotation'
CROSS JOIN user_tb u
WHERE u.username = 'root'
  AND r.role_name IN ('purchaser', 'purchaser_manager')
  AND p.is_enabled = 1
  AND NOT EXISTS (
      SELECT 1 FROM role_permission_tb rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- 4. Sidebar entry (frontend route /purchase/quotations, i18n key quotations)
--    for everyone who already sees the purchase follow-up templates entry.
INSERT INTO navigation_menu_tb
    (parent_id, title_key, icon, route, color, permission_id, is_enabled, create_time)
SELECT NULL, 'quotations', 'mdi-calculator-variant-outline', '/purchase/quotations', 'teal', p.id, 1, NOW()
FROM permission_tb p
WHERE p.permission_code = 'menu.quotation.read'
  AND NOT EXISTS (SELECT 1 FROM navigation_menu_tb m WHERE m.title_key = 'quotations')
LIMIT 1;

INSERT INTO user_nav_menu (nav_id, permission_id, user_id, is_enabled, create_date)
SELECT DISTINCT q.id, NULL, x.user_id, 1, NOW()
FROM navigation_menu_tb q
JOIN navigation_menu_tb t ON t.title_key = 'followUpTemplates'
JOIN user_nav_menu x      ON x.nav_id = t.id AND x.is_enabled = 1
WHERE q.title_key = 'quotations'
  AND NOT EXISTS (SELECT 1 FROM user_nav_menu y WHERE y.nav_id = q.id AND y.user_id = x.user_id);
