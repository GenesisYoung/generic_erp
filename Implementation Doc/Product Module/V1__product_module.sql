-- =============================================================================
--  Product Module - baseline schema
--  Target: MySQL 8.0.16+ (CHECK constraints are enforced from 8.0.16)
--  Engine: InnoDB, utf8mb4 / utf8mb4_0900_ai_ci
--
--  Table creation order follows foreign-key dependency order, so this script
--  runs top to bottom with FOREIGN_KEY_CHECKS left on.
--
--  NOTE ON user_tb: created_by / updated_by / edited_by are left WITHOUT a
--  foreign key because the name and primary-key column of the existing user
--  table are not stated in the design document. Add the constraints once
--  confirmed, for example:
--      ALTER TABLE product_tb
--        ADD CONSTRAINT fk_product_created_by
--        FOREIGN KEY (created_by) REFERENCES user_tb (user_id);
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. uom_tb
-- -----------------------------------------------------------------------------
CREATE TABLE uom_tb (
    uom_id                 BIGINT          NOT NULL AUTO_INCREMENT,
    iso_code               VARCHAR(8)      NOT NULL,
    unit_name              VARCHAR(32)     NOT NULL,
    symbol                 VARCHAR(8)      NULL,
    dimension              ENUM('MASS','LENGTH','VOLUME','AREA','TIME','TEMP',
                                'DENS','VELOC','ACCEL','ENERGY','POWER','PRESS',
                                'COUNT')   NOT NULL,
    is_base_for_dimension  BOOLEAN         NOT NULL DEFAULT FALSE,
    factor_to_base         DECIMAL(24,10)  NOT NULL DEFAULT 1,
    description            VARCHAR(2000)   NULL,
    active                 BOOLEAN         NOT NULL DEFAULT TRUE,
    created_by             BIGINT          NOT NULL,
    create_time            DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_by             BIGINT          NULL,
    update_time            DATETIME(6)     NULL ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (uom_id),
    UNIQUE KEY uk_uom_iso_code  (iso_code),
    UNIQUE KEY uk_uom_unit_name (unit_name),
    KEY idx_uom_dimension (dimension, active),
    CONSTRAINT ck_uom_factor_positive CHECK (factor_to_base > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- 2. category_tb
-- -----------------------------------------------------------------------------
CREATE TABLE category_tb (
    cate_id      BIGINT         NOT NULL AUTO_INCREMENT,
    cate_name    VARCHAR(50)    NOT NULL,
    cate_abbr    VARCHAR(8)     NULL,
    description  VARCHAR(2000)  NULL,
    parent_id    BIGINT         NULL,
    path_cache   VARCHAR(512)   NULL,
    depth        INT            NOT NULL DEFAULT 0,
    active       BOOLEAN        NOT NULL DEFAULT TRUE,
    created_by   BIGINT         NOT NULL,
    create_time  DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_by   BIGINT         NULL,
    update_time  DATETIME(6)    NULL ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (cate_id),
    -- A name is unique under one parent, not globally: "Accessories" may exist
    -- under both "Men" and "Women".
    UNIQUE KEY uk_category_parent_name (parent_id, cate_name),
    KEY idx_category_parent (parent_id),
    KEY idx_category_path   (path_cache(255)),
    KEY idx_category_abbr   (cate_abbr),
    CONSTRAINT fk_category_parent
        FOREIGN KEY (parent_id) REFERENCES category_tb (cate_id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_category_depth CHECK (depth >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- 3. appendix_tb
-- -----------------------------------------------------------------------------
CREATE TABLE appendix_tb (
    appendix_id  BIGINT        NOT NULL AUTO_INCREMENT,
    storage_key  VARCHAR(64)   NOT NULL,
    path         VARCHAR(512)  NOT NULL,
    file_name    VARCHAR(128)  NOT NULL,
    file_exten   VARCHAR(8)    NOT NULL,
    mime_type    VARCHAR(128)  NULL,
    byte_size    BIGINT        NULL,
    active       BOOLEAN       NOT NULL DEFAULT TRUE,
    created_by   BIGINT        NOT NULL,
    create_time  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_by   BIGINT        NULL,
    update_time  DATETIME(6)   NULL ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (appendix_id),
    UNIQUE KEY uk_appendix_storage_key (storage_key),
    -- file_name is deliberately NOT unique: two products may both upload
    -- "front.jpg". Identity belongs to storage_key.
    KEY idx_appendix_file_name (file_name),
    CONSTRAINT ck_appendix_size CHECK (byte_size IS NULL OR byte_size >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- 4. supplier_tb
--    Provisional: requirements section 15 places supplier master data out of
--    scope for v1.1. The Purchasing module may take ownership later.
-- -----------------------------------------------------------------------------
CREATE TABLE supplier_tb (
    supplier_id    BIGINT        NOT NULL AUTO_INCREMENT,
    supplier_code  VARCHAR(20)   NOT NULL,
    legal_name     VARCHAR(256)  NOT NULL,
    trade_name     VARCHAR(255)  NULL,
    status         ENUM('ACTIVE','HOLD','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
    tax_id         VARCHAR(50)   NULL,
    created_by     BIGINT        NOT NULL,
    create_time    DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_by     BIGINT        NULL,
    update_time    DATETIME(6)   NULL ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (supplier_id),
    UNIQUE KEY uk_supplier_code   (supplier_code),
    UNIQUE KEY uk_supplier_legal  (legal_name),
    UNIQUE KEY uk_supplier_tax_id (tax_id),
    KEY idx_supplier_status (status),
    KEY idx_supplier_trade  (trade_name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- 5. attribute_def_tb
-- -----------------------------------------------------------------------------
CREATE TABLE attribute_def_tb (
    attr_def_id      BIGINT        NOT NULL AUTO_INCREMENT,
    attribute_code   VARCHAR(64)   NOT NULL,
    display_name     VARCHAR(128)  NOT NULL,
    value_type       ENUM('TEXT','NUMBER','ENUM','BOOL') NOT NULL DEFAULT 'TEXT',
    uom_id           BIGINT        NULL,
    is_variant_axis  BOOLEAN       NOT NULL DEFAULT FALSE,
    active           BOOLEAN       NOT NULL DEFAULT TRUE,
    created_by       BIGINT        NOT NULL,
    create_time      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_by       BIGINT        NULL,
    update_time      DATETIME(6)   NULL ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (attr_def_id),
    UNIQUE KEY uk_attr_def_code (attribute_code),
    KEY idx_attr_def_axis (is_variant_axis, active),
    CONSTRAINT fk_attr_def_uom
        FOREIGN KEY (uom_id) REFERENCES uom_tb (uom_id)
        ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- 6. product_tb
-- -----------------------------------------------------------------------------
CREATE TABLE product_tb (
    product_id           BIGINT         NOT NULL AUTO_INCREMENT,
    product_code         VARCHAR(64)    NOT NULL,
    product_name         VARCHAR(150)   NOT NULL,
    product_description  VARCHAR(2000)  NULL,
    type                 ENUM('STOCKABLE','SERVICE','CONSUMABLE') NOT NULL,
    configurable         BOOLEAN        NOT NULL DEFAULT FALSE,
    status               ENUM('DRAFT','ACTIVE','DISCONTINUED') NOT NULL DEFAULT 'DRAFT',
    base_uom_id          BIGINT         NOT NULL,
    standard_cost        DECIMAL(19,4)  NULL,
    list_price           DECIMAL(19,4)  NULL,
    currency             CHAR(3)        NULL,
    tax_category         VARCHAR(32)    NULL,
    version              INT            NOT NULL DEFAULT 0,
    created_by           BIGINT         NOT NULL,
    create_time          DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_by           BIGINT         NULL,
    update_time          DATETIME(6)    NULL ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (product_id),
    -- Collation is pinned so that case-insensitive uniqueness (section 9) does
    -- not silently depend on the server default.
    UNIQUE KEY uk_product_code (product_code),
    KEY idx_product_status_type (status, type),
    KEY idx_product_name        (product_name(50)),
    KEY idx_product_base_uom    (base_uom_id),
    CONSTRAINT fk_product_base_uom
        FOREIGN KEY (base_uom_id) REFERENCES uom_tb (uom_id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_product_cost_non_negative
        CHECK (standard_cost IS NULL OR standard_cost >= 0),
    CONSTRAINT ck_product_price_non_negative
        CHECK (list_price IS NULL OR list_price >= 0),
    -- If any price is set, a currency must be present. The ISO-4217 code list
    -- itself is validated in the service layer.
    CONSTRAINT ck_product_currency_present
        CHECK ((standard_cost IS NULL AND list_price IS NULL) OR currency IS NOT NULL)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

ALTER TABLE product_tb
    MODIFY product_code VARCHAR(64)
    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL;

-- -----------------------------------------------------------------------------
-- 7. product_variant_tb
--    Every product owns at least one row here (design decision D1).
--    A non-configurable product gets exactly one, with is_default = TRUE.
-- -----------------------------------------------------------------------------
CREATE TABLE product_variant_tb (
    variant_id     BIGINT         NOT NULL AUTO_INCREMENT,
    product_id     BIGINT         NOT NULL,
    sku            VARCHAR(64)    NOT NULL,
    variant_name   VARCHAR(64)    NOT NULL,
    is_default     BOOLEAN        NOT NULL DEFAULT FALSE,
    description    VARCHAR(2000)  NULL,
    standard_cost  DECIMAL(19,4)  NULL,
    list_price     DECIMAL(19,4)  NULL,
    currency       CHAR(3)        NULL,
    net_weight     DECIMAL(19,4)  NULL,
    weight_uom_id  BIGINT         NULL,
    length_mm      DECIMAL(12,3)  NULL,
    width_mm       DECIMAL(12,3)  NULL,
    height_mm      DECIMAL(12,3)  NULL,
    active         BOOLEAN        NOT NULL DEFAULT TRUE,
    version        INT            NOT NULL DEFAULT 0,
    created_by     BIGINT         NOT NULL,
    create_time    DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_by     BIGINT         NULL,
    update_time    DATETIME(6)    NULL ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (variant_id),
    UNIQUE KEY uk_variant_sku  (sku),
    UNIQUE KEY uk_variant_name (product_id, variant_name),
    KEY idx_variant_product (product_id, active),
    KEY idx_variant_weight_uom (weight_uom_id),
    CONSTRAINT fk_variant_product
        FOREIGN KEY (product_id) REFERENCES product_tb (product_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_variant_weight_uom
        FOREIGN KEY (weight_uom_id) REFERENCES uom_tb (uom_id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_variant_cost_non_negative
        CHECK (standard_cost IS NULL OR standard_cost >= 0),
    CONSTRAINT ck_variant_price_non_negative
        CHECK (list_price IS NULL OR list_price >= 0),
    CONSTRAINT ck_variant_weight_non_negative
        CHECK (net_weight IS NULL OR net_weight >= 0),
    -- A weight value without its unit is meaningless.
    CONSTRAINT ck_variant_weight_has_uom
        CHECK (net_weight IS NULL OR weight_uom_id IS NOT NULL)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- 8. product_category_rel_tb
-- -----------------------------------------------------------------------------
CREATE TABLE product_category_rel_tb (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    product_id   BIGINT       NOT NULL,
    cate_id      BIGINT       NOT NULL,
    is_primary   BOOLEAN      NOT NULL DEFAULT FALSE,
    active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by   BIGINT       NOT NULL,
    create_time  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_by   BIGINT       NULL,
    update_time  DATETIME(6)  NULL ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_cate (product_id, cate_id),
    -- Both directions are indexed: "products in this category" (also the
    -- FR-P-13 delete guard) and "categories of this product".
    KEY idx_pc_cate_product (cate_id, product_id),
    CONSTRAINT fk_pc_product
        FOREIGN KEY (product_id) REFERENCES product_tb (product_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_pc_category
        FOREIGN KEY (cate_id) REFERENCES category_tb (cate_id)
        ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- 9. product_uom_tb
--    Product-specific packaging conversions, e.g. 1 BOX = 12 EA.
-- -----------------------------------------------------------------------------
CREATE TABLE product_uom_tb (
    id                  BIGINT          NOT NULL AUTO_INCREMENT,
    product_id          BIGINT          NOT NULL,
    uom_id              BIGINT          NOT NULL,
    factor_to_base_uom  DECIMAL(24,10)  NOT NULL,
    usage_type          ENUM('PURCHASE','SALE','BOTH') NOT NULL DEFAULT 'BOTH',
    active              BOOLEAN         NOT NULL DEFAULT TRUE,
    created_by          BIGINT          NOT NULL,
    create_time         DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_by          BIGINT          NULL,
    update_time         DATETIME(6)     NULL ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_product_uom (product_id, uom_id),
    KEY idx_product_uom_uom (uom_id),
    CONSTRAINT fk_product_uom_product
        FOREIGN KEY (product_id) REFERENCES product_tb (product_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_product_uom_uom
        FOREIGN KEY (uom_id) REFERENCES uom_tb (uom_id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_product_uom_factor CHECK (factor_to_base_uom > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- 10. product_attribute_tb
-- -----------------------------------------------------------------------------
CREATE TABLE product_attribute_tb (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    variant_id       BIGINT        NOT NULL,
    attr_def_id      BIGINT        NOT NULL,
    attribute_value  VARCHAR(255)  NOT NULL,
    sort_order       INT           NOT NULL DEFAULT 0,
    active           BOOLEAN       NOT NULL DEFAULT TRUE,
    created_by       BIGINT        NOT NULL,
    create_time      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_by       BIGINT        NULL,
    update_time      DATETIME(6)   NULL ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_attr_variant_def (variant_id, attr_def_id),
    KEY idx_attr_def (attr_def_id),
    CONSTRAINT fk_attr_variant
        FOREIGN KEY (variant_id) REFERENCES product_variant_tb (variant_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_attr_definition
        FOREIGN KEY (attr_def_id) REFERENCES attribute_def_tb (attr_def_id)
        ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- 11. product_barcode_tb
-- -----------------------------------------------------------------------------
CREATE TABLE product_barcode_tb (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    variant_id     BIGINT       NOT NULL,
    barcode_value  VARCHAR(64)  NOT NULL,
    barcode_type   ENUM('EAN13','EAN8','UPC_A','UPC_E','CODE128','QR') NOT NULL,
    region         ENUM('US','EU','CN','JP') NULL,
    is_primary     BOOLEAN      NOT NULL DEFAULT FALSE,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_by     BIGINT       NOT NULL,
    create_time    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_by     BIGINT       NULL,
    update_time    DATETIME(6)  NULL ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_barcode_value (barcode_value),
    KEY idx_barcode_variant (variant_id),
    KEY idx_barcode_region  (region),
    CONSTRAINT fk_barcode_variant
        FOREIGN KEY (variant_id) REFERENCES product_variant_tb (variant_id)
        ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- 12. product_image_tb
-- -----------------------------------------------------------------------------
CREATE TABLE product_image_tb (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    variant_id   BIGINT        NOT NULL,
    appendix_id  BIGINT        NOT NULL,
    alt_text     VARCHAR(255)  NULL,
    sort_order   INT           NOT NULL DEFAULT 0,
    is_primary   BOOLEAN       NOT NULL DEFAULT FALSE,
    created_by   BIGINT        NOT NULL,
    create_time  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_by   BIGINT        NULL,
    update_time  DATETIME(6)   NULL ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_image_variant_file (variant_id, appendix_id),
    KEY idx_image_variant_order (variant_id, sort_order),
    KEY idx_image_appendix (appendix_id),
    CONSTRAINT fk_image_variant
        FOREIGN KEY (variant_id) REFERENCES product_variant_tb (variant_id)
        ON DELETE CASCADE,
    -- RESTRICT, not CASCADE: a file still used by a product must not vanish.
    CONSTRAINT fk_image_appendix
        FOREIGN KEY (appendix_id) REFERENCES appendix_tb (appendix_id)
        ON DELETE RESTRICT
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- 13. product_supplier_code_tb
-- -----------------------------------------------------------------------------
CREATE TABLE product_supplier_code_tb (
    id                  BIGINT         NOT NULL AUTO_INCREMENT,
    variant_id          BIGINT         NOT NULL,
    supplier_id         BIGINT         NOT NULL,
    supplier_part_no    VARCHAR(64)    NOT NULL,
    is_preferred        BOOLEAN        NOT NULL DEFAULT FALSE,
    last_purchase_cost  DECIMAL(19,4)  NULL,
    lead_time_days      INT            NULL,
    active              BOOLEAN        NOT NULL DEFAULT TRUE,
    created_by          BIGINT         NOT NULL,
    create_time         DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_by          BIGINT         NULL,
    update_time         DATETIME(6)    NULL ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_supplier_part (supplier_id, supplier_part_no),
    UNIQUE KEY uk_variant_supplier (variant_id, supplier_id),
    CONSTRAINT fk_psc_variant
        FOREIGN KEY (variant_id) REFERENCES product_variant_tb (variant_id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_psc_supplier
        FOREIGN KEY (supplier_id) REFERENCES supplier_tb (supplier_id)
        ON DELETE RESTRICT,
    CONSTRAINT ck_psc_cost_non_negative
        CHECK (last_purchase_cost IS NULL OR last_purchase_cost >= 0),
    CONSTRAINT ck_psc_lead_time
        CHECK (lead_time_days IS NULL OR lead_time_days >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- 14. product_audit_log_tb
--     Append-only. No standard audit columns: this table IS the audit record.
-- -----------------------------------------------------------------------------
CREATE TABLE product_audit_log_tb (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    change_group_id  CHAR(36)     NOT NULL,
    operation        ENUM('CREATE','UPDATE','STATUS_CHANGE','DELETE') NOT NULL,
    affected_table   VARCHAR(64)  NOT NULL,
    affected_row     VARCHAR(64)  NOT NULL,
    affected_column  VARCHAR(64)  NOT NULL,
    before_val       TEXT         NULL,
    after_val        TEXT         NULL,
    edited_by        BIGINT       NOT NULL,
    edit_time        DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_audit_target (affected_table, affected_row),
    KEY idx_audit_group  (change_group_id),
    KEY idx_audit_actor  (edited_by, edit_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- =============================================================================
--  Seed data - units of measure
--  created_by = 1 assumes a system/bootstrap user exists with that id. Adjust
--  to match the actual seeded administrator in the auth module.
-- =============================================================================
INSERT INTO uom_tb
    (iso_code, unit_name, symbol, dimension, is_base_for_dimension, factor_to_base, created_by)
VALUES
    -- COUNT: factor_to_base is always 1. Conversion between counting units is
    -- product-specific and resolved only through product_uom_tb.
    ('EA',  'Each',       'ea',  'COUNT',  TRUE,  1,        1),
    ('PC',  'Piece',      'pc',  'COUNT',  FALSE, 1,        1),
    ('BOX', 'Box',        'box', 'COUNT',  FALSE, 1,        1),
    ('CT',  'Carton',     'ct',  'COUNT',  FALSE, 1,        1),
    ('PAL', 'Pallet',     'pal', 'COUNT',  FALSE, 1,        1),
    -- MASS: base is the kilogram.
    ('KGM', 'Kilogram',   'kg',  'MASS',   TRUE,  1,        1),
    ('GRM', 'Gram',       'g',   'MASS',   FALSE, 0.001,    1),
    ('TNE', 'Tonne',      't',   'MASS',   FALSE, 1000,     1),
    ('LBR', 'Pound',      'lb',  'MASS',   FALSE, 0.45359237, 1),
    -- LENGTH: base is the metre.
    ('MTR', 'Metre',      'm',   'LENGTH', TRUE,  1,        1),
    ('MMT', 'Millimetre', 'mm',  'LENGTH', FALSE, 0.001,    1),
    ('CMT', 'Centimetre', 'cm',  'LENGTH', FALSE, 0.01,     1),
    ('INH', 'Inch',       'in',  'LENGTH', FALSE, 0.0254,   1),
    -- VOLUME: base is the litre.
    ('LTR', 'Litre',      'L',   'VOLUME', TRUE,  1,        1),
    ('MLT', 'Millilitre', 'mL',  'VOLUME', FALSE, 0.001,    1);
