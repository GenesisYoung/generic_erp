-- =============================================================================
--  Product Module - development seed data
--
--  Purpose : populate the product module with realistic sample data for local
--            development, UI work and manual testing.
--  Target  : MySQL 8.0.16+ , schema created by
--            V1__product_module.sql followed by UPDATES.sql
--
--  Properties
--    * Idempotent. Rows are matched on their natural keys (product_code, sku,
--      barcode_value, ...), so re-running the script inserts nothing twice.
--    * No hard-coded primary keys. Every foreign key is resolved by joining on
--      a natural key, so the script does not care about AUTO_INCREMENT values.
--    * created_by resolves to the seeded 'root' user, falling back to id 1.
--
--  Run the whole file in ONE session: the staging tables are TEMPORARY.
--
--  To remove the seed data again, see the DELETE block at the end of the file.
-- =============================================================================

SET NAMES utf8mb4;
SET @seed_user := COALESCE((SELECT id FROM user_tb WHERE username = 'root' LIMIT 1), 1);

-- -----------------------------------------------------------------------------
--  Staging tables. They hold the seed rows in a readable, natural-key form;
--  the INSERT ... SELECT statements further down resolve them to real ids.
-- -----------------------------------------------------------------------------
DROP TEMPORARY TABLE IF EXISTS tmp_seed_category;
DROP TEMPORARY TABLE IF EXISTS tmp_seed_supplier;
DROP TEMPORARY TABLE IF EXISTS tmp_seed_attr_def;
DROP TEMPORARY TABLE IF EXISTS tmp_seed_appendix;
DROP TEMPORARY TABLE IF EXISTS tmp_seed_product;
DROP TEMPORARY TABLE IF EXISTS tmp_seed_product_category;
DROP TEMPORARY TABLE IF EXISTS tmp_seed_product_uom;
DROP TEMPORARY TABLE IF EXISTS tmp_seed_variant;
DROP TEMPORARY TABLE IF EXISTS tmp_seed_attribute;
DROP TEMPORARY TABLE IF EXISTS tmp_seed_barcode;
DROP TEMPORARY TABLE IF EXISTS tmp_seed_supplier_code;
DROP TEMPORARY TABLE IF EXISTS tmp_seed_image;

CREATE TEMPORARY TABLE tmp_seed_category (
    cate_name    VARCHAR(50)   NOT NULL,
    cate_abbr    VARCHAR(8)    NOT NULL,
    description  VARCHAR(2000) NULL,
    parent_name  VARCHAR(50)   NULL,
    create_time  DATETIME(6)   NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TEMPORARY TABLE tmp_seed_supplier (
    supplier_code VARCHAR(20)  NOT NULL,
    legal_name    VARCHAR(256) NOT NULL,
    trade_name    VARCHAR(255) NULL,
    status        VARCHAR(16)  NOT NULL,
    tax_id        VARCHAR(50)  NULL,
    create_time   DATETIME(6)  NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TEMPORARY TABLE tmp_seed_attr_def (
    attribute_code  VARCHAR(64)  NOT NULL,
    display_name    VARCHAR(128) NOT NULL,
    value_type      VARCHAR(16)  NOT NULL,
    uom_code        VARCHAR(8)   NULL,
    is_variant_axis TINYINT      NOT NULL,
    create_time     DATETIME(6)  NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TEMPORARY TABLE tmp_seed_appendix (
    storage_key VARCHAR(64)  NOT NULL,
    path        VARCHAR(512) NOT NULL,
    file_name   VARCHAR(128) NOT NULL,
    file_exten  VARCHAR(8)   NOT NULL,
    mime_type   VARCHAR(128) NULL,
    byte_size   BIGINT       NULL,
    create_time DATETIME(6)  NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TEMPORARY TABLE tmp_seed_product (
    product_code        VARCHAR(64)   NOT NULL,
    product_name        VARCHAR(150)  NOT NULL,
    product_description VARCHAR(2000) NULL,
    type                VARCHAR(16)   NOT NULL,
    configurable        TINYINT       NOT NULL,
    status              VARCHAR(16)   NOT NULL,
    base_uom_code       VARCHAR(8)    NOT NULL,
    standard_cost       DECIMAL(19,4) NULL,
    list_price          DECIMAL(19,4) NULL,
    currency            CHAR(3)       NULL,
    tax_category        VARCHAR(32)   NULL,
    create_time         DATETIME(6)   NOT NULL,
    update_time         DATETIME(6)   NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TEMPORARY TABLE tmp_seed_product_category (
    product_code VARCHAR(64) NOT NULL,
    cate_name    VARCHAR(50) NOT NULL,
    cate_abbr    VARCHAR(8)  NOT NULL,
    is_primary   TINYINT     NOT NULL,
    create_time  DATETIME(6) NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TEMPORARY TABLE tmp_seed_product_uom (
    product_code       VARCHAR(64)    NOT NULL,
    uom_code           VARCHAR(8)     NOT NULL,
    factor_to_base_uom DECIMAL(24,10) NOT NULL,
    usage_type         VARCHAR(16)    NOT NULL,
    create_time        DATETIME(6)    NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TEMPORARY TABLE tmp_seed_variant (
    sku             VARCHAR(64)   NOT NULL,
    product_code    VARCHAR(64)   NOT NULL,
    variant_name    VARCHAR(64)   NOT NULL,
    is_default      TINYINT       NOT NULL,
    description     VARCHAR(2000) NULL,
    standard_cost   DECIMAL(19,4) NULL,
    list_price      DECIMAL(19,4) NULL,
    currency        CHAR(3)       NULL,
    net_weight      DECIMAL(19,4) NULL,
    weight_uom_code VARCHAR(8)    NULL,
    length          DECIMAL(12,3) NULL,
    width           DECIMAL(12,3) NULL,
    height          DECIMAL(12,3) NULL,
    size_uom_code   VARCHAR(8)    NULL,
    active          TINYINT       NOT NULL,
    create_time     DATETIME(6)   NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TEMPORARY TABLE tmp_seed_attribute (
    sku             VARCHAR(64)  NOT NULL,
    attribute_code  VARCHAR(64)  NOT NULL,
    attribute_value VARCHAR(255) NOT NULL,
    sort_order      INT          NOT NULL,
    create_time     DATETIME(6)  NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TEMPORARY TABLE tmp_seed_barcode (
    sku           VARCHAR(64) NOT NULL,
    barcode_value VARCHAR(64) NOT NULL,
    barcode_type  VARCHAR(16) NOT NULL,
    region        VARCHAR(8)  NULL,
    is_primary    TINYINT     NOT NULL,
    create_time   DATETIME(6) NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TEMPORARY TABLE tmp_seed_supplier_code (
    sku                VARCHAR(64)   NOT NULL,
    supplier_code      VARCHAR(20)   NOT NULL,
    supplier_part_no   VARCHAR(64)   NOT NULL,
    is_preferred       TINYINT       NOT NULL,
    last_purchase_cost DECIMAL(19,4) NULL,
    lead_time_days     INT           NULL,
    create_time        DATETIME(6)   NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TEMPORARY TABLE tmp_seed_image (
    sku         VARCHAR(64)  NOT NULL,
    storage_key VARCHAR(64)  NOT NULL,
    alt_text    VARCHAR(255) NULL,
    sort_order  INT          NOT NULL,
    is_primary  TINYINT      NOT NULL,
    create_time DATETIME(6)  NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------------------------------------
--  Staging data
-- ---------------------------------------------------------------------------
INSERT INTO tmp_seed_category (cate_name, cate_abbr, description, parent_name, create_time) VALUES
    ('Electronics', 'ELEC', 'Computing hardware, displays and accessories.', NULL, '2025-03-04 09:20:00'),
    ('Furniture', 'FURN', 'Office and workspace furniture.', NULL, '2025-03-04 09:20:00'),
    ('Office Supplies', 'OFFC', 'Everyday consumable office goods.', NULL, '2025-03-04 09:20:00'),
    ('Industrial Parts', 'INDP', 'Mechanical components and workplace safety gear.', NULL, '2025-03-04 09:20:00'),
    ('Services', 'SERV', 'Billable services delivered to customers.', NULL, '2025-03-04 09:20:00'),
    ('Laptops', 'LAP', 'Portable computers and mobile workstations.', 'Electronics', '2025-03-04 09:20:00'),
    ('Monitors', 'MON', 'Desktop displays.', 'Electronics', '2025-03-04 09:20:00'),
    ('Peripherals', 'PER', 'Input devices, docks and audio accessories.', 'Electronics', '2025-03-04 09:20:00'),
    ('Cables & Adapters', 'CBL', 'Signal and power cabling.', 'Electronics', '2025-03-04 09:20:00'),
    ('Desks', 'DSK', 'Working surfaces, fixed and height adjustable.', 'Furniture', '2025-03-04 09:20:00'),
    ('Chairs', 'CHR', 'Seating for offices and meeting rooms.', 'Furniture', '2025-03-04 09:20:00'),
    ('Storage', 'STG', 'Cabinets, pedestals and shelving.', 'Furniture', '2025-03-04 09:20:00'),
    ('Paper', 'PPR', 'Cut-size paper and paper rolls.', 'Office Supplies', '2025-03-04 09:20:00'),
    ('Writing', 'WRT', 'Pens, markers and pencils.', 'Office Supplies', '2025-03-04 09:20:00'),
    ('Consumables', 'CNS', 'Printing and cleaning consumables.', 'Office Supplies', '2025-03-04 09:20:00'),
    ('Fasteners', 'FST', 'Bolts, nuts and washers.', 'Industrial Parts', '2025-03-04 09:20:00'),
    ('Bearings', 'BRG', 'Rolling element bearings.', 'Industrial Parts', '2025-03-04 09:20:00'),
    ('Safety Gear', 'SFT', 'Personal protective equipment.', 'Industrial Parts', '2025-03-04 09:20:00'),
    ('Installation', 'INS', 'On-site installation and commissioning.', 'Services', '2025-03-04 09:20:00'),
    ('Support', 'SUP', 'Maintenance and support contracts.', 'Services', '2025-03-04 09:20:00');

INSERT INTO tmp_seed_supplier (supplier_code, legal_name, trade_name, status, tax_id, create_time) VALUES
    ('SUP-001', 'Northwind Components Limited', 'Northwind', 'ACTIVE', 'GB-TAX-88412', '2025-03-05 09:20:00'),
    ('SUP-002', 'Aurora Office Group Incorporated', 'Aurora Office', 'ACTIVE', 'US-TAX-55019', '2025-03-05 09:20:00'),
    ('SUP-003', 'Baltic Steel and Fasteners AB', 'Baltic Steel', 'ACTIVE', 'SE-TAX-31277', '2025-03-05 09:20:00'),
    ('SUP-004', 'Kanto Precision Works K.K.', 'Kanto Precision', 'ACTIVE', 'JP-TAX-70933', '2025-03-05 09:20:00'),
    ('SUP-005', 'Meridian Furniture Company', 'Meridian', 'ACTIVE', 'US-TAX-24188', '2025-03-05 09:20:00'),
    ('SUP-006', 'Cascade Electronics LLC', 'Cascade', 'ACTIVE', 'US-TAX-61044', '2025-03-05 09:20:00'),
    ('SUP-007', 'Helios Paper Mills S.A.', 'Helios Paper', 'HOLD', 'ES-TAX-90561', '2025-03-05 09:20:00'),
    ('SUP-008', 'Vertex Safety Supplies Limited', 'Vertex Safety', 'INACTIVE', 'GB-TAX-47720', '2025-03-05 09:20:00');

INSERT INTO tmp_seed_attr_def (attribute_code, display_name, value_type, uom_code, is_variant_axis, create_time) VALUES
    ('COLOR', 'Colour', 'ENUM', NULL, 1, '2025-03-06 09:20:00'),
    ('SIZE', 'Size', 'ENUM', NULL, 1, '2025-03-06 09:20:00'),
    ('CONFIG', 'Memory / Storage', 'ENUM', NULL, 1, '2025-03-06 09:20:00'),
    ('SWITCH', 'Switch Type', 'ENUM', NULL, 1, '2025-03-06 09:20:00'),
    ('MATERIAL', 'Material', 'TEXT', NULL, 0, '2025-03-06 09:20:00'),
    ('WARRANTY_MONTHS', 'Warranty (months)', 'NUMBER', NULL, 0, '2025-03-06 09:20:00'),
    ('SCREEN_SIZE', 'Screen Size', 'NUMBER', 'INH', 0, '2025-03-06 09:20:00'),
    ('RECYCLABLE', 'Recyclable Packaging', 'BOOL', NULL, 0, '2025-03-06 09:20:00');

INSERT INTO tmp_seed_appendix (storage_key, path, file_name, file_exten, mime_type, byte_size, create_time) VALUES
    ('seed-img-0001', '/uploads/product/LAP-001/lap_001_front.jpg', 'lap_001_front.jpg', 'jpg', 'image/jpeg', 185297, '2025-03-04 09:20:00'),
    ('seed-img-0002', '/uploads/product/LAP-002/lap_002_front.jpg', 'lap_002_front.jpg', 'jpg', 'image/jpeg', 186274, '2025-03-13 09:20:00'),
    ('seed-img-0003', '/uploads/product/LAP-003/lap_003_front.jpg', 'lap_003_front.jpg', 'jpg', 'image/jpeg', 187251, '2025-03-22 09:20:00'),
    ('seed-img-0004', '/uploads/product/MON-001/mon_001_front.jpg', 'mon_001_front.jpg', 'jpg', 'image/jpeg', 188228, '2025-04-18 09:20:00'),
    ('seed-img-0005', '/uploads/product/MON-002/mon_002_front.jpg', 'mon_002_front.jpg', 'jpg', 'image/jpeg', 189205, '2025-04-27 09:20:00'),
    ('seed-img-0006', '/uploads/product/PER-001/per_001_front.jpg', 'per_001_front.jpg', 'jpg', 'image/jpeg', 190182, '2025-05-24 09:20:00'),
    ('seed-img-0007', '/uploads/product/PER-002/per_002_front.jpg', 'per_002_front.jpg', 'jpg', 'image/jpeg', 191159, '2025-06-02 09:20:00'),
    ('seed-img-0008', '/uploads/product/PER-005/per_005_front.jpg', 'per_005_front.jpg', 'jpg', 'image/jpeg', 192136, '2025-06-29 09:20:00'),
    ('seed-img-0009', '/uploads/product/DSK-001/dsk_001_front.jpg', 'dsk_001_front.jpg', 'jpg', 'image/jpeg', 193113, '2025-08-13 09:20:00'),
    ('seed-img-0010', '/uploads/product/CHR-001/chr_001_front.jpg', 'chr_001_front.jpg', 'jpg', 'image/jpeg', 194090, '2025-09-09 09:20:00'),
    ('seed-img-0011', '/uploads/product/PPR-001/ppr_001_front.jpg', 'ppr_001_front.jpg', 'jpg', 'image/jpeg', 195067, '2025-11-11 09:20:00'),
    ('seed-img-0012', '/uploads/product/CNS-001/cns_001_front.jpg', 'cns_001_front.jpg', 'jpg', 'image/jpeg', 196044, '2026-01-22 09:20:00'),
    ('seed-img-0013', '/uploads/product/SFT-001/sft_001_front.jpg', 'sft_001_front.jpg', 'jpg', 'image/jpeg', 197021, '2026-04-22 09:20:00');

INSERT INTO tmp_seed_product (product_code, product_name, product_description, type, configurable,
                              status, base_uom_code, standard_cost, list_price, currency,
                              tax_category, create_time, update_time) VALUES
    ('LAP-001', 'ProBook 14 Business Laptop', '14-inch business notebook with aluminium chassis and a spill-resistant keyboard.', 'STOCKABLE', 1, 'ACTIVE', 'EA', 720.0, 1099.0, 'USD', 'STANDARD', '2025-03-04 09:20:00', '2025-05-06 14:05:00'),
    ('LAP-002', 'UltraSlim 13 Ultrabook', '13-inch fanless ultrabook aimed at travelling staff.', 'STOCKABLE', 1, 'ACTIVE', 'EA', 890.0, 1349.0, 'USD', 'STANDARD', '2025-03-13 09:20:00', NULL),
    ('LAP-003', 'Workstation 16 Mobile Workstation', '16-inch mobile workstation with discrete graphics for CAD workloads.', 'STOCKABLE', 0, 'ACTIVE', 'EA', 1580.0, 2299.0, 'USD', 'STANDARD', '2025-03-22 09:20:00', NULL),
    ('LAP-004', 'EduBook 11 Classroom Laptop', 'Ruggedised 11-inch laptop for classroom deployments.', 'STOCKABLE', 0, 'ACTIVE', 'EA', 310.0, 459.0, 'USD', 'STANDARD', '2025-03-31 09:20:00', NULL),
    ('LAP-005', 'ProBook 15 Legacy Model', 'Previous generation 15-inch business notebook. Replaced by the ProBook 14 line.', 'STOCKABLE', 0, 'DISCONTINUED', 'EA', 640.0, 949.0, 'USD', 'STANDARD', '2025-04-09 09:20:00', '2025-06-11 14:05:00'),
    ('MON-001', '24-inch IPS Office Monitor', 'Full HD IPS panel with height adjustable stand.', 'STOCKABLE', 0, 'ACTIVE', 'EA', 95.0, 159.0, 'USD', 'STANDARD', '2025-04-18 09:20:00', NULL),
    ('MON-002', '27-inch QHD Monitor', '1440p display available with a fixed or an ergonomic stand.', 'STOCKABLE', 1, 'ACTIVE', 'EA', 165.0, 269.0, 'USD', 'STANDARD', '2025-04-27 09:20:00', NULL),
    ('MON-003', '34-inch Ultrawide Monitor', 'Curved 21:9 ultrawide display for trading and design desks.', 'STOCKABLE', 0, 'ACTIVE', 'EA', 340.0, 529.0, 'USD', 'STANDARD', '2025-05-06 09:20:00', NULL),
    ('MON-004', '21.5-inch Budget Monitor', 'Entry level Full HD monitor. Pricing not yet approved.', 'STOCKABLE', 0, 'DRAFT', 'EA', 62.0, 109.0, 'USD', 'STANDARD', '2025-05-15 09:20:00', NULL),
    ('PER-001', 'Wireless Optical Mouse', '2.4 GHz wireless mouse with a one-year battery life.', 'STOCKABLE', 1, 'ACTIVE', 'EA', 6.5, 14.99, 'USD', 'STANDARD', '2025-05-24 09:20:00', NULL),
    ('PER-002', 'Mechanical Keyboard TKL', 'Tenkeyless mechanical keyboard with hot-swappable switches.', 'STOCKABLE', 1, 'ACTIVE', 'EA', 28.0, 59.9, 'USD', 'STANDARD', '2025-06-02 09:20:00', NULL),
    ('PER-003', 'USB-C Docking Station', 'Single-cable dock with dual video output and 90 W power delivery.', 'STOCKABLE', 0, 'ACTIVE', 'EA', 74.0, 129.0, 'USD', 'STANDARD', '2025-06-11 09:20:00', NULL),
    ('PER-004', '1080p Webcam', 'Full HD webcam with a dual microphone array and privacy shutter.', 'STOCKABLE', 0, 'ACTIVE', 'EA', 18.0, 39.9, 'USD', 'STANDARD', '2025-06-20 09:20:00', NULL),
    ('PER-005', 'Noise-Cancelling Headset', 'Binaural USB headset with active noise cancellation for open-plan offices.', 'STOCKABLE', 1, 'ACTIVE', 'EA', 42.0, 89.0, 'USD', 'STANDARD', '2025-06-29 09:20:00', NULL),
    ('CBL-001', 'USB-C to USB-C Cable 2 m', 'USB 3.2 Gen 2 cable rated for 100 W power delivery.', 'STOCKABLE', 1, 'ACTIVE', 'EA', 1.8, 6.99, 'USD', 'STANDARD', '2025-07-08 09:20:00', NULL),
    ('CBL-002', 'HDMI 2.1 Cable 3 m', 'Certified ultra-high-speed HDMI cable supporting 4K at 120 Hz.', 'STOCKABLE', 0, 'ACTIVE', 'EA', 3.2, 11.5, 'USD', 'STANDARD', '2025-07-17 09:20:00', NULL),
    ('CBL-003', 'Cat6 Patch Cable 5 m', 'Shielded Cat6 patch lead with moulded strain relief.', 'STOCKABLE', 1, 'ACTIVE', 'EA', 1.1, 4.75, 'USD', 'STANDARD', '2025-07-26 09:20:00', NULL),
    ('CBL-004', 'DisplayPort Cable 1.8 m', 'DisplayPort 1.4 cable. Withdrawn in favour of the USB-C line.', 'STOCKABLE', 0, 'DISCONTINUED', 'EA', 2.4, 8.9, 'USD', 'STANDARD', '2025-08-04 09:20:00', '2025-10-06 14:05:00'),
    ('DSK-001', 'Height-Adjustable Standing Desk', 'Electric sit-stand desk with a dual-motor column and memory presets.', 'STOCKABLE', 1, 'ACTIVE', 'EA', 210.0, 389.0, 'USD', 'STANDARD', '2025-08-13 09:20:00', '2025-10-15 14:05:00'),
    ('DSK-002', 'Fixed Office Desk 120x60', 'Entry level fixed-height desk with a cable management tray.', 'STOCKABLE', 0, 'ACTIVE', 'EA', 95.0, 179.0, 'USD', 'STANDARD', '2025-08-22 09:20:00', NULL),
    ('DSK-003', 'Corner Workstation Desk', 'L-shaped corner desk suitable for dual-monitor setups.', 'STOCKABLE', 0, 'ACTIVE', 'EA', 175.0, 299.0, 'USD', 'STANDARD', '2025-08-31 09:20:00', NULL),
    ('CHR-001', 'Ergonomic Mesh Task Chair', 'Breathable mesh back with adjustable lumbar support and 3D armrests.', 'STOCKABLE', 1, 'ACTIVE', 'EA', 120.0, 239.0, 'USD', 'STANDARD', '2025-09-09 09:20:00', NULL),
    ('CHR-002', 'Executive Leather Chair', 'High-back executive chair in bonded leather with a synchronised tilt.', 'STOCKABLE', 1, 'ACTIVE', 'EA', 195.0, 379.0, 'USD', 'STANDARD', '2025-09-18 09:20:00', NULL),
    ('CHR-003', 'Stackable Visitor Chair', 'Stackable four-leg visitor chair for meeting and training rooms.', 'STOCKABLE', 0, 'ACTIVE', 'EA', 34.0, 69.0, 'USD', 'STANDARD', '2025-09-27 09:20:00', NULL),
    ('CHR-004', 'Drafting Stool', 'High stool with a foot ring for standing-height workbenches.', 'STOCKABLE', 0, 'DRAFT', 'EA', 58.0, 109.0, 'USD', 'STANDARD', '2025-10-06 09:20:00', NULL),
    ('STG-001', '4-Drawer Steel Filing Cabinet', 'Lockable four-drawer cabinet with an anti-tilt interlock.', 'STOCKABLE', 0, 'ACTIVE', 'EA', 88.0, 165.0, 'USD', 'STANDARD', '2025-10-15 09:20:00', NULL),
    ('STG-002', 'Mobile Pedestal 3-Drawer', 'Under-desk pedestal on castors with a central lock.', 'STOCKABLE', 1, 'ACTIVE', 'EA', 52.0, 99.0, 'USD', 'STANDARD', '2025-10-24 09:20:00', NULL),
    ('STG-003', 'Open Shelving Unit 5-Tier', 'Bolt-free shelving unit for stock rooms and archives.', 'STOCKABLE', 0, 'ACTIVE', 'EA', 64.0, 119.0, 'USD', 'STANDARD', '2025-11-02 09:20:00', NULL),
    ('PPR-001', 'A4 Copy Paper 80 gsm', 'Ream of 500 sheets, FSC certified, for everyday printing.', 'CONSUMABLE', 0, 'ACTIVE', 'EA', 2.6, 4.9, 'USD', 'STANDARD', '2025-11-11 09:20:00', NULL),
    ('PPR-002', 'A3 Copy Paper 80 gsm', 'Ream of 500 A3 sheets for plan and drawing output.', 'CONSUMABLE', 0, 'ACTIVE', 'EA', 5.1, 9.4, 'USD', 'STANDARD', '2025-11-20 09:20:00', NULL),
    ('PPR-003', 'Thermal Receipt Roll 57 mm', 'BPA-free thermal roll for point-of-sale printers.', 'CONSUMABLE', 0, 'ACTIVE', 'EA', 0.35, 0.99, 'USD', 'STANDARD', '2025-11-29 09:20:00', NULL),
    ('PPR-004', 'Sticky Notes 76 x 76 mm', 'Repositionable note pad, 100 sheets per pad.', 'CONSUMABLE', 1, 'ACTIVE', 'EA', 0.42, 1.29, 'USD', 'STANDARD', '2025-12-08 09:20:00', NULL),
    ('WRT-001', 'Ballpoint Pen 0.7 mm', 'Retractable ballpoint pen with a low-viscosity ink.', 'CONSUMABLE', 1, 'ACTIVE', 'EA', 0.11, 0.45, 'USD', 'STANDARD', '2025-12-17 09:20:00', NULL),
    ('WRT-002', 'Whiteboard Marker', 'Dry-wipe marker with a bullet tip and a low-odour ink.', 'CONSUMABLE', 1, 'ACTIVE', 'EA', 0.38, 1.15, 'USD', 'STANDARD', '2025-12-26 09:20:00', NULL),
    ('WRT-003', 'Highlighter Chisel Tip', 'Chisel-tip highlighter with fluorescent water-based ink.', 'CONSUMABLE', 0, 'ACTIVE', 'EA', 0.29, 0.89, 'USD', 'STANDARD', '2026-01-04 09:20:00', NULL),
    ('WRT-004', 'Mechanical Pencil 0.5 mm', 'Metal-barrel mechanical pencil with a retractable tip.', 'CONSUMABLE', 0, 'ACTIVE', 'EA', 0.55, 1.79, 'USD', 'STANDARD', '2026-01-13 09:20:00', NULL),
    ('CNS-001', 'Toner Cartridge Black High Yield', 'High-yield black toner rated for 10,000 pages.', 'CONSUMABLE', 0, 'ACTIVE', 'EA', 38.0, 79.0, 'USD', 'STANDARD', '2026-01-22 09:20:00', NULL),
    ('CNS-002', 'Inkjet Cartridge Tri-Colour', 'Tri-colour inkjet cartridge for desktop printers.', 'CONSUMABLE', 0, 'ACTIVE', 'EA', 14.0, 29.9, 'USD', 'STANDARD', '2026-01-31 09:20:00', NULL),
    ('CNS-003', 'Cleaning Wipes Screen Safe', 'Alcohol-free wipes for displays and touch panels, 100 per tub.', 'CONSUMABLE', 0, 'ACTIVE', 'EA', 2.1, 5.49, 'USD', 'STANDARD', '2026-02-09 09:20:00', NULL),
    ('CNS-004', 'Disinfectant Spray 500 mL', 'Surface disinfectant for shared desks and meeting rooms.', 'CONSUMABLE', 0, 'ACTIVE', 'EA', 1.7, 4.29, 'USD', 'STANDARD', '2026-02-18 09:20:00', NULL),
    ('FST-001', 'Hex Bolt M8 Zinc Plated', 'DIN 933 hexagon head bolt, zinc plated, property class 8.8.', 'STOCKABLE', 1, 'ACTIVE', 'EA', 0.08, 0.29, 'USD', 'STANDARD', '2026-02-27 09:20:00', NULL),
    ('FST-002', 'Hex Nut M8 Zinc Plated', 'DIN 934 hexagon nut, zinc plated, property class 8.', 'STOCKABLE', 0, 'ACTIVE', 'EA', 0.03, 0.12, 'USD', 'STANDARD', '2026-03-08 09:20:00', NULL),
    ('FST-003', 'Flat Washer M8 Stainless', 'DIN 125 flat washer in A2 stainless steel.', 'STOCKABLE', 0, 'ACTIVE', 'EA', 0.02, 0.09, 'USD', 'STANDARD', '2026-03-17 09:20:00', NULL),
    ('BRG-001', 'Deep Groove Ball Bearing 6204', 'Single row deep groove ball bearing, 2RS sealed, 20 x 47 x 14 mm.', 'STOCKABLE', 0, 'ACTIVE', 'EA', 1.9, 5.6, 'USD', 'STANDARD', '2026-03-26 09:20:00', NULL),
    ('BRG-002', 'Tapered Roller Bearing 30205', 'Metric tapered roller bearing, 25 x 52 x 16.25 mm.', 'STOCKABLE', 0, 'ACTIVE', 'EA', 4.3, 11.9, 'USD', 'STANDARD', '2026-04-04 09:20:00', NULL),
    ('BRG-003', 'Linear Bearing LM8UU', '8 mm linear ball bushing for guide rails. Awaiting supplier qualification.', 'STOCKABLE', 0, 'DRAFT', 'EA', 0.9, 2.75, 'USD', 'STANDARD', '2026-04-13 09:20:00', NULL),
    ('SFT-001', 'Safety Helmet Class E', 'Electrically insulating hard hat with a six-point harness.', 'STOCKABLE', 1, 'ACTIVE', 'EA', 7.4, 18.9, 'USD', 'STANDARD', '2026-04-22 09:20:00', NULL),
    ('SFT-002', 'Nitrile Gloves Box of 100', 'Powder-free disposable nitrile gloves, 100 per dispenser box.', 'CONSUMABLE', 1, 'ACTIVE', 'EA', 4.2, 9.99, 'USD', 'STANDARD', '2026-05-01 09:20:00', NULL),
    ('SRV-001', 'On-Site Installation (per hour)', 'Engineer time for on-site delivery, assembly and commissioning. Billed per hour.', 'SERVICE', 0, 'ACTIVE', 'EA', NULL, 85.0, 'USD', 'SERVICES', '2026-05-10 09:20:00', NULL),
    ('SRV-002', 'Extended Hardware Support 12 Months', 'Next business day hardware support contract for a twelve month term.', 'SERVICE', 0, 'ACTIVE', 'EA', 40.0, 149.0, 'USD', 'SERVICES', '2026-05-19 09:20:00', '2026-07-21 14:05:00');

INSERT INTO tmp_seed_product_category (product_code, cate_name, cate_abbr, is_primary, create_time) VALUES
    ('LAP-001', 'Laptops', 'LAP', 1, '2025-03-04 09:20:00'),
    ('LAP-001', 'Electronics', 'ELEC', 0, '2025-03-04 09:20:00'),
    ('LAP-002', 'Laptops', 'LAP', 1, '2025-03-13 09:20:00'),
    ('LAP-003', 'Laptops', 'LAP', 1, '2025-03-22 09:20:00'),
    ('LAP-004', 'Laptops', 'LAP', 1, '2025-03-31 09:20:00'),
    ('LAP-004', 'Electronics', 'ELEC', 0, '2025-03-31 09:20:00'),
    ('LAP-005', 'Laptops', 'LAP', 1, '2025-04-09 09:20:00'),
    ('MON-001', 'Monitors', 'MON', 1, '2025-04-18 09:20:00'),
    ('MON-002', 'Monitors', 'MON', 1, '2025-04-27 09:20:00'),
    ('MON-002', 'Electronics', 'ELEC', 0, '2025-04-27 09:20:00'),
    ('MON-003', 'Monitors', 'MON', 1, '2025-05-06 09:20:00'),
    ('MON-004', 'Monitors', 'MON', 1, '2025-05-15 09:20:00'),
    ('PER-001', 'Peripherals', 'PER', 1, '2025-05-24 09:20:00'),
    ('PER-001', 'Electronics', 'ELEC', 0, '2025-05-24 09:20:00'),
    ('PER-002', 'Peripherals', 'PER', 1, '2025-06-02 09:20:00'),
    ('PER-003', 'Peripherals', 'PER', 1, '2025-06-11 09:20:00'),
    ('PER-004', 'Peripherals', 'PER', 1, '2025-06-20 09:20:00'),
    ('PER-004', 'Electronics', 'ELEC', 0, '2025-06-20 09:20:00'),
    ('PER-005', 'Peripherals', 'PER', 1, '2025-06-29 09:20:00'),
    ('CBL-001', 'Cables & Adapters', 'CBL', 1, '2025-07-08 09:20:00'),
    ('CBL-002', 'Cables & Adapters', 'CBL', 1, '2025-07-17 09:20:00'),
    ('CBL-002', 'Electronics', 'ELEC', 0, '2025-07-17 09:20:00'),
    ('CBL-003', 'Cables & Adapters', 'CBL', 1, '2025-07-26 09:20:00'),
    ('CBL-004', 'Cables & Adapters', 'CBL', 1, '2025-08-04 09:20:00'),
    ('DSK-001', 'Desks', 'DSK', 1, '2025-08-13 09:20:00'),
    ('DSK-001', 'Furniture', 'FURN', 0, '2025-08-13 09:20:00'),
    ('DSK-002', 'Desks', 'DSK', 1, '2025-08-22 09:20:00'),
    ('DSK-003', 'Desks', 'DSK', 1, '2025-08-31 09:20:00'),
    ('CHR-001', 'Chairs', 'CHR', 1, '2025-09-09 09:20:00'),
    ('CHR-001', 'Furniture', 'FURN', 0, '2025-09-09 09:20:00'),
    ('CHR-002', 'Chairs', 'CHR', 1, '2025-09-18 09:20:00'),
    ('CHR-003', 'Chairs', 'CHR', 1, '2025-09-27 09:20:00'),
    ('CHR-004', 'Chairs', 'CHR', 1, '2025-10-06 09:20:00'),
    ('CHR-004', 'Furniture', 'FURN', 0, '2025-10-06 09:20:00'),
    ('STG-001', 'Storage', 'STG', 1, '2025-10-15 09:20:00'),
    ('STG-002', 'Storage', 'STG', 1, '2025-10-24 09:20:00'),
    ('STG-003', 'Storage', 'STG', 1, '2025-11-02 09:20:00'),
    ('STG-003', 'Furniture', 'FURN', 0, '2025-11-02 09:20:00'),
    ('PPR-001', 'Paper', 'PPR', 1, '2025-11-11 09:20:00'),
    ('PPR-002', 'Paper', 'PPR', 1, '2025-11-20 09:20:00'),
    ('PPR-003', 'Paper', 'PPR', 1, '2025-11-29 09:20:00'),
    ('PPR-003', 'Office Supplies', 'OFFC', 0, '2025-11-29 09:20:00'),
    ('PPR-004', 'Paper', 'PPR', 1, '2025-12-08 09:20:00'),
    ('WRT-001', 'Writing', 'WRT', 1, '2025-12-17 09:20:00'),
    ('WRT-002', 'Writing', 'WRT', 1, '2025-12-26 09:20:00'),
    ('WRT-002', 'Office Supplies', 'OFFC', 0, '2025-12-26 09:20:00'),
    ('WRT-003', 'Writing', 'WRT', 1, '2026-01-04 09:20:00'),
    ('WRT-004', 'Writing', 'WRT', 1, '2026-01-13 09:20:00'),
    ('CNS-001', 'Consumables', 'CNS', 1, '2026-01-22 09:20:00'),
    ('CNS-001', 'Office Supplies', 'OFFC', 0, '2026-01-22 09:20:00'),
    ('CNS-002', 'Consumables', 'CNS', 1, '2026-01-31 09:20:00'),
    ('CNS-003', 'Consumables', 'CNS', 1, '2026-02-09 09:20:00'),
    ('CNS-004', 'Consumables', 'CNS', 1, '2026-02-18 09:20:00'),
    ('CNS-004', 'Office Supplies', 'OFFC', 0, '2026-02-18 09:20:00'),
    ('FST-001', 'Fasteners', 'FST', 1, '2026-02-27 09:20:00'),
    ('FST-002', 'Fasteners', 'FST', 1, '2026-03-08 09:20:00'),
    ('FST-003', 'Fasteners', 'FST', 1, '2026-03-17 09:20:00'),
    ('FST-003', 'Industrial Parts', 'INDP', 0, '2026-03-17 09:20:00'),
    ('BRG-001', 'Bearings', 'BRG', 1, '2026-03-26 09:20:00'),
    ('BRG-002', 'Bearings', 'BRG', 1, '2026-04-04 09:20:00'),
    ('BRG-003', 'Bearings', 'BRG', 1, '2026-04-13 09:20:00'),
    ('BRG-003', 'Industrial Parts', 'INDP', 0, '2026-04-13 09:20:00'),
    ('SFT-001', 'Safety Gear', 'SFT', 1, '2026-04-22 09:20:00'),
    ('SFT-002', 'Safety Gear', 'SFT', 1, '2026-05-01 09:20:00'),
    ('SRV-001', 'Installation', 'INS', 1, '2026-05-10 09:20:00'),
    ('SRV-001', 'Services', 'SERV', 0, '2026-05-10 09:20:00'),
    ('SRV-002', 'Support', 'SUP', 1, '2026-05-19 09:20:00');

INSERT INTO tmp_seed_product_uom (product_code, uom_code, factor_to_base_uom, usage_type, create_time) VALUES
    ('LAP-001', 'BOX', 10, 'PURCHASE', '2025-03-04 09:20:00'),
    ('LAP-002', 'BOX', 10, 'PURCHASE', '2025-03-13 09:20:00'),
    ('LAP-003', 'BOX', 5, 'PURCHASE', '2025-03-22 09:20:00'),
    ('LAP-004', 'BOX', 12, 'PURCHASE', '2025-03-31 09:20:00'),
    ('LAP-004', 'PAL', 288, 'PURCHASE', '2025-03-31 09:20:00'),
    ('MON-001', 'BOX', 4, 'PURCHASE', '2025-04-18 09:20:00'),
    ('MON-002', 'BOX', 3, 'PURCHASE', '2025-04-27 09:20:00'),
    ('PER-001', 'BOX', 20, 'BOTH', '2025-05-24 09:20:00'),
    ('PER-001', 'CT', 200, 'PURCHASE', '2025-05-24 09:20:00'),
    ('PER-002', 'BOX', 10, 'BOTH', '2025-06-02 09:20:00'),
    ('PER-003', 'BOX', 10, 'BOTH', '2025-06-11 09:20:00'),
    ('PER-004', 'BOX', 20, 'BOTH', '2025-06-20 09:20:00'),
    ('PER-005', 'BOX', 12, 'BOTH', '2025-06-29 09:20:00'),
    ('CBL-001', 'BOX', 25, 'BOTH', '2025-07-08 09:20:00'),
    ('CBL-001', 'CT', 250, 'PURCHASE', '2025-07-08 09:20:00'),
    ('CBL-002', 'BOX', 25, 'BOTH', '2025-07-17 09:20:00'),
    ('CBL-003', 'BOX', 50, 'BOTH', '2025-07-26 09:20:00'),
    ('CBL-003', 'CT', 500, 'PURCHASE', '2025-07-26 09:20:00'),
    ('CHR-003', 'BOX', 4, 'PURCHASE', '2025-09-27 09:20:00'),
    ('PPR-001', 'BOX', 5, 'BOTH', '2025-11-11 09:20:00'),
    ('PPR-001', 'PAL', 1000, 'PURCHASE', '2025-11-11 09:20:00'),
    ('PPR-002', 'BOX', 5, 'BOTH', '2025-11-20 09:20:00'),
    ('PPR-003', 'BOX', 50, 'BOTH', '2025-11-29 09:20:00'),
    ('PPR-003', 'CT', 500, 'PURCHASE', '2025-11-29 09:20:00'),
    ('PPR-004', 'BOX', 12, 'BOTH', '2025-12-08 09:20:00'),
    ('WRT-001', 'BOX', 50, 'BOTH', '2025-12-17 09:20:00'),
    ('WRT-001', 'CT', 1000, 'PURCHASE', '2025-12-17 09:20:00'),
    ('WRT-002', 'BOX', 12, 'BOTH', '2025-12-26 09:20:00'),
    ('WRT-003', 'BOX', 10, 'BOTH', '2026-01-04 09:20:00'),
    ('WRT-004', 'BOX', 24, 'BOTH', '2026-01-13 09:20:00'),
    ('CNS-001', 'BOX', 6, 'PURCHASE', '2026-01-22 09:20:00'),
    ('CNS-002', 'BOX', 10, 'PURCHASE', '2026-01-31 09:20:00'),
    ('CNS-003', 'BOX', 12, 'BOTH', '2026-02-09 09:20:00'),
    ('CNS-004', 'BOX', 12, 'BOTH', '2026-02-18 09:20:00'),
    ('FST-001', 'BOX', 100, 'BOTH', '2026-02-27 09:20:00'),
    ('FST-001', 'CT', 2000, 'PURCHASE', '2026-02-27 09:20:00'),
    ('FST-002', 'BOX', 200, 'BOTH', '2026-03-08 09:20:00'),
    ('FST-002', 'CT', 4000, 'PURCHASE', '2026-03-08 09:20:00'),
    ('FST-003', 'BOX', 200, 'BOTH', '2026-03-17 09:20:00'),
    ('BRG-001', 'BOX', 20, 'BOTH', '2026-03-26 09:20:00'),
    ('BRG-002', 'BOX', 10, 'BOTH', '2026-04-04 09:20:00'),
    ('SFT-001', 'BOX', 12, 'BOTH', '2026-04-22 09:20:00'),
    ('SFT-002', 'BOX', 10, 'BOTH', '2026-05-01 09:20:00'),
    ('SFT-002', 'CT', 100, 'PURCHASE', '2026-05-01 09:20:00');

INSERT INTO tmp_seed_variant (sku, product_code, variant_name, is_default, description,
                              standard_cost, list_price, currency, net_weight, weight_uom_code,
                              `length`, width, height, size_uom_code, active, create_time) VALUES
    ('LAP-001-8-256', 'LAP-001', '8 GB / 256 GB', 1, 'ProBook 14 Business Laptop - 8 GB / 256 GB.', 720.0, 1099.0, 'USD', 1.38, 'KGM', 322.0, 214.0, 18.0, 'MMT', 1, '2025-03-04 09:20:00'),
    ('LAP-001-16-512', 'LAP-001', '16 GB / 512 GB', 0, 'ProBook 14 Business Laptop - 16 GB / 512 GB.', 805.0, 1239.0, 'USD', 1.38, 'KGM', 322.0, 214.0, 18.0, 'MMT', 1, '2025-03-04 09:20:00'),
    ('LAP-001-32-1024', 'LAP-001', '32 GB / 1 TB', 0, 'ProBook 14 Business Laptop - 32 GB / 1 TB.', 930.0, 1429.0, 'USD', 1.38, 'KGM', 322.0, 214.0, 18.0, 'MMT', 1, '2025-03-04 09:20:00'),
    ('LAP-002-16-512', 'LAP-002', '16 GB / 512 GB', 1, 'UltraSlim 13 Ultrabook - 16 GB / 512 GB.', 890.0, 1349.0, 'USD', 990.0, 'GRM', 297.0, 199.0, 15.0, 'MMT', 1, '2025-03-13 09:20:00'),
    ('LAP-002-16-1024', 'LAP-002', '16 GB / 1 TB', 0, 'UltraSlim 13 Ultrabook - 16 GB / 1 TB.', 960.0, 1469.0, 'USD', 990.0, 'GRM', 297.0, 199.0, 15.0, 'MMT', 1, '2025-03-13 09:20:00'),
    ('LAP-003-STD', 'LAP-003', 'Standard', 1, NULL, NULL, NULL, NULL, 2.35, 'KGM', 358.0, 248.0, 24.0, 'MMT', 1, '2025-03-22 09:20:00'),
    ('LAP-004-STD', 'LAP-004', 'Standard', 1, NULL, NULL, NULL, NULL, 1.25, 'KGM', 292.0, 205.0, 21.0, 'MMT', 1, '2025-03-31 09:20:00'),
    ('LAP-005-STD', 'LAP-005', 'Standard', 1, NULL, NULL, NULL, NULL, 1.89, 'KGM', 365.0, 245.0, 22.0, 'MMT', 0, '2025-04-09 09:20:00'),
    ('MON-001-STD', 'MON-001', 'Standard', 1, NULL, NULL, NULL, NULL, 4.2, 'KGM', 540.0, 210.0, 420.0, 'MMT', 1, '2025-04-18 09:20:00'),
    ('MON-002-FIX', 'MON-002', 'Fixed Stand', 1, '27-inch QHD Monitor - Fixed Stand.', 165.0, 269.0, 'USD', 5.6, 'KGM', 614.0, 230.0, 460.0, 'MMT', 1, '2025-04-27 09:20:00'),
    ('MON-002-ERG', 'MON-002', 'Ergonomic Stand', 0, '27-inch QHD Monitor - Ergonomic Stand.', 187.0, 314.0, 'USD', 5.6, 'KGM', 614.0, 230.0, 460.0, 'MMT', 1, '2025-04-27 09:20:00'),
    ('MON-003-STD', 'MON-003', 'Standard', 1, NULL, NULL, NULL, NULL, 8.1, 'KGM', 810.0, 260.0, 520.0, 'MMT', 1, '2025-05-06 09:20:00'),
    ('MON-004-STD', 'MON-004', 'Standard', 1, NULL, NULL, NULL, NULL, 3.1, 'KGM', 495.0, 195.0, 380.0, 'MMT', 1, '2025-05-15 09:20:00'),
    ('PER-001-BLK', 'PER-001', 'Black', 1, 'Wireless Optical Mouse - Black.', 6.5, 14.99, 'USD', 78.0, 'GRM', 115.0, 62.0, 38.0, 'MMT', 1, '2025-05-24 09:20:00'),
    ('PER-001-GRP', 'PER-001', 'Graphite', 0, 'Wireless Optical Mouse - Graphite.', 6.5, 14.99, 'USD', 78.0, 'GRM', 115.0, 62.0, 38.0, 'MMT', 1, '2025-05-24 09:20:00'),
    ('PER-001-WHT', 'PER-001', 'White', 0, 'Wireless Optical Mouse - White.', 6.7, 15.99, 'USD', 78.0, 'GRM', 115.0, 62.0, 38.0, 'MMT', 1, '2025-05-24 09:20:00'),
    ('PER-002-RED', 'PER-002', 'Linear Red', 1, 'Mechanical Keyboard TKL - Linear Red.', 28.0, 59.9, 'USD', 760.0, 'GRM', 360.0, 140.0, 40.0, 'MMT', 1, '2025-06-02 09:20:00'),
    ('PER-002-BRN', 'PER-002', 'Tactile Brown', 0, 'Mechanical Keyboard TKL - Tactile Brown.', 28.0, 59.9, 'USD', 760.0, 'GRM', 360.0, 140.0, 40.0, 'MMT', 1, '2025-06-02 09:20:00'),
    ('PER-002-BLU', 'PER-002', 'Clicky Blue', 0, 'Mechanical Keyboard TKL - Clicky Blue.', 28.0, 59.9, 'USD', 760.0, 'GRM', 360.0, 140.0, 40.0, 'MMT', 1, '2025-06-02 09:20:00'),
    ('PER-003-STD', 'PER-003', 'Standard', 1, NULL, NULL, NULL, NULL, 520.0, 'GRM', 190.0, 85.0, 30.0, 'MMT', 1, '2025-06-11 09:20:00'),
    ('PER-004-STD', 'PER-004', 'Standard', 1, NULL, NULL, NULL, NULL, 140.0, 'GRM', 95.0, 55.0, 50.0, 'MMT', 1, '2025-06-20 09:20:00'),
    ('PER-005-BLK', 'PER-005', 'Black', 1, 'Noise-Cancelling Headset - Black.', 42.0, 89.0, 'USD', 245.0, 'GRM', 200.0, 180.0, 90.0, 'MMT', 1, '2025-06-29 09:20:00'),
    ('PER-005-SLV', 'PER-005', 'Silver', 0, 'Noise-Cancelling Headset - Silver.', 43.5, 93.0, 'USD', 245.0, 'GRM', 200.0, 180.0, 90.0, 'MMT', 1, '2025-06-29 09:20:00'),
    ('CBL-001-BLK', 'CBL-001', 'Black', 1, 'USB-C to USB-C Cable 2 m - Black.', 1.8, 6.99, 'USD', 86.0, 'GRM', 120.0, 100.0, 25.0, 'MMT', 1, '2025-07-08 09:20:00'),
    ('CBL-001-WHT', 'CBL-001', 'White', 0, 'USB-C to USB-C Cable 2 m - White.', 1.8, 6.99, 'USD', 86.0, 'GRM', 120.0, 100.0, 25.0, 'MMT', 1, '2025-07-08 09:20:00'),
    ('CBL-002-STD', 'CBL-002', 'Standard', 1, NULL, NULL, NULL, NULL, 180.0, 'GRM', 130.0, 110.0, 30.0, 'MMT', 1, '2025-07-17 09:20:00'),
    ('CBL-003-GRY', 'CBL-003', 'Grey', 1, 'Cat6 Patch Cable 5 m - Grey.', 1.1, 4.75, 'USD', 210.0, 'GRM', 140.0, 120.0, 30.0, 'MMT', 1, '2025-07-26 09:20:00'),
    ('CBL-003-BLU', 'CBL-003', 'Blue', 0, 'Cat6 Patch Cable 5 m - Blue.', 1.1, 4.75, 'USD', 210.0, 'GRM', 140.0, 120.0, 30.0, 'MMT', 1, '2025-07-26 09:20:00'),
    ('CBL-003-YEL', 'CBL-003', 'Yellow', 0, 'Cat6 Patch Cable 5 m - Yellow.', 1.1, 4.75, 'USD', 210.0, 'GRM', 140.0, 120.0, 30.0, 'MMT', 1, '2025-07-26 09:20:00'),
    ('CBL-004-STD', 'CBL-004', 'Standard', 1, NULL, NULL, NULL, NULL, 150.0, 'GRM', 120.0, 100.0, 28.0, 'MMT', 0, '2025-08-04 09:20:00'),
    ('DSK-001-120', 'DSK-001', '120 x 60 cm', 1, 'Height-Adjustable Standing Desk - 120 x 60 cm.', 210.0, 389.0, 'USD', 32.0, 'KGM', 1450.0, 750.0, 180.0, 'MMT', 1, '2025-08-13 09:20:00'),
    ('DSK-001-140', 'DSK-001', '140 x 70 cm', 0, 'Height-Adjustable Standing Desk - 140 x 70 cm.', 234.0, 434.0, 'USD', 32.0, 'KGM', 1450.0, 750.0, 180.0, 'MMT', 1, '2025-08-13 09:20:00'),
    ('DSK-001-160', 'DSK-001', '160 x 80 cm', 0, 'Height-Adjustable Standing Desk - 160 x 80 cm.', 262.0, 484.0, 'USD', 32.0, 'KGM', 1450.0, 750.0, 180.0, 'MMT', 1, '2025-08-13 09:20:00'),
    ('DSK-002-STD', 'DSK-002', 'Standard', 1, NULL, NULL, NULL, NULL, 21.0, 'KGM', 1250.0, 640.0, 120.0, 'MMT', 1, '2025-08-22 09:20:00'),
    ('DSK-003-STD', 'DSK-003', 'Standard', 1, NULL, NULL, NULL, NULL, 38.0, 'KGM', 1600.0, 1200.0, 150.0, 'MMT', 1, '2025-08-31 09:20:00'),
    ('CHR-001-BLK', 'CHR-001', 'Black', 1, 'Ergonomic Mesh Task Chair - Black.', 120.0, 239.0, 'USD', 14.5, 'KGM', 680.0, 680.0, 1150.0, 'MMT', 1, '2025-09-09 09:20:00'),
    ('CHR-001-GRY', 'CHR-001', 'Grey', 0, 'Ergonomic Mesh Task Chair - Grey.', 120.0, 239.0, 'USD', 14.5, 'KGM', 680.0, 680.0, 1150.0, 'MMT', 1, '2025-09-09 09:20:00'),
    ('CHR-001-NVY', 'CHR-001', 'Navy', 0, 'Ergonomic Mesh Task Chair - Navy.', 123.0, 249.0, 'USD', 14.5, 'KGM', 680.0, 680.0, 1150.0, 'MMT', 1, '2025-09-09 09:20:00'),
    ('CHR-002-BLK', 'CHR-002', 'Black', 1, 'Executive Leather Chair - Black.', 195.0, 379.0, 'USD', 19.8, 'KGM', 720.0, 720.0, 1250.0, 'MMT', 1, '2025-09-18 09:20:00'),
    ('CHR-002-BRN', 'CHR-002', 'Brown', 0, 'Executive Leather Chair - Brown.', 201.0, 394.0, 'USD', 19.8, 'KGM', 720.0, 720.0, 1250.0, 'MMT', 1, '2025-09-18 09:20:00'),
    ('CHR-003-STD', 'CHR-003', 'Standard', 1, NULL, NULL, NULL, NULL, 5.2, 'KGM', 540.0, 520.0, 820.0, 'MMT', 1, '2025-09-27 09:20:00'),
    ('CHR-004-STD', 'CHR-004', 'Standard', 1, NULL, NULL, NULL, NULL, 8.6, 'KGM', 560.0, 560.0, 1100.0, 'MMT', 1, '2025-10-06 09:20:00'),
    ('STG-001-STD', 'STG-001', 'Standard', 1, NULL, NULL, NULL, NULL, 42.0, 'KGM', 470.0, 620.0, 1320.0, 'MMT', 1, '2025-10-15 09:20:00'),
    ('STG-002-WHT', 'STG-002', 'White', 1, 'Mobile Pedestal 3-Drawer - White.', 52.0, 99.0, 'USD', 18.5, 'KGM', 400.0, 550.0, 600.0, 'MMT', 1, '2025-10-24 09:20:00'),
    ('STG-002-GRP', 'STG-002', 'Graphite', 0, 'Mobile Pedestal 3-Drawer - Graphite.', 52.0, 99.0, 'USD', 18.5, 'KGM', 400.0, 550.0, 600.0, 'MMT', 1, '2025-10-24 09:20:00'),
    ('STG-003-STD', 'STG-003', 'Standard', 1, NULL, NULL, NULL, NULL, 26.0, 'KGM', 900.0, 400.0, 1800.0, 'MMT', 1, '2025-11-02 09:20:00'),
    ('PPR-001-STD', 'PPR-001', 'Standard', 1, NULL, NULL, NULL, NULL, 2.5, 'KGM', 297.0, 210.0, 55.0, 'MMT', 1, '2025-11-11 09:20:00'),
    ('PPR-002-STD', 'PPR-002', 'Standard', 1, NULL, NULL, NULL, NULL, 5.0, 'KGM', 420.0, 297.0, 55.0, 'MMT', 1, '2025-11-20 09:20:00'),
    ('PPR-003-STD', 'PPR-003', 'Standard', 1, NULL, NULL, NULL, NULL, 48.0, 'GRM', 57.0, 57.0, 40.0, 'MMT', 1, '2025-11-29 09:20:00'),
    ('PPR-004-YEL', 'PPR-004', 'Yellow', 1, 'Sticky Notes 76 x 76 mm - Yellow.', 0.42, 1.29, 'USD', 62.0, 'GRM', 76.0, 76.0, 20.0, 'MMT', 1, '2025-12-08 09:20:00'),
    ('PPR-004-PNK', 'PPR-004', 'Pink', 0, 'Sticky Notes 76 x 76 mm - Pink.', 0.42, 1.29, 'USD', 62.0, 'GRM', 76.0, 76.0, 20.0, 'MMT', 1, '2025-12-08 09:20:00'),
    ('PPR-004-GRN', 'PPR-004', 'Green', 0, 'Sticky Notes 76 x 76 mm - Green.', 0.42, 1.29, 'USD', 62.0, 'GRM', 76.0, 76.0, 20.0, 'MMT', 1, '2025-12-08 09:20:00'),
    ('PPR-004-BLU', 'PPR-004', 'Blue', 0, 'Sticky Notes 76 x 76 mm - Blue.', 0.42, 1.29, 'USD', 62.0, 'GRM', 76.0, 76.0, 20.0, 'MMT', 1, '2025-12-08 09:20:00'),
    ('WRT-001-BLK', 'WRT-001', 'Black', 1, 'Ballpoint Pen 0.7 mm - Black.', 0.11, 0.45, 'USD', 11.0, 'GRM', 145.0, 12.0, 12.0, 'MMT', 1, '2025-12-17 09:20:00'),
    ('WRT-001-BLU', 'WRT-001', 'Blue', 0, 'Ballpoint Pen 0.7 mm - Blue.', 0.11, 0.45, 'USD', 11.0, 'GRM', 145.0, 12.0, 12.0, 'MMT', 1, '2025-12-17 09:20:00'),
    ('WRT-001-RED', 'WRT-001', 'Red', 0, 'Ballpoint Pen 0.7 mm - Red.', 0.11, 0.45, 'USD', 11.0, 'GRM', 145.0, 12.0, 12.0, 'MMT', 1, '2025-12-17 09:20:00'),
    ('WRT-002-BLK', 'WRT-002', 'Black', 1, 'Whiteboard Marker - Black.', 0.38, 1.15, 'USD', 18.0, 'GRM', 140.0, 16.0, 16.0, 'MMT', 1, '2025-12-26 09:20:00'),
    ('WRT-002-BLU', 'WRT-002', 'Blue', 0, 'Whiteboard Marker - Blue.', 0.38, 1.15, 'USD', 18.0, 'GRM', 140.0, 16.0, 16.0, 'MMT', 1, '2025-12-26 09:20:00'),
    ('WRT-002-RED', 'WRT-002', 'Red', 0, 'Whiteboard Marker - Red.', 0.38, 1.15, 'USD', 18.0, 'GRM', 140.0, 16.0, 16.0, 'MMT', 1, '2025-12-26 09:20:00'),
    ('WRT-002-GRN', 'WRT-002', 'Green', 0, 'Whiteboard Marker - Green.', 0.38, 1.15, 'USD', 18.0, 'GRM', 140.0, 16.0, 16.0, 'MMT', 1, '2025-12-26 09:20:00'),
    ('WRT-003-STD', 'WRT-003', 'Standard', 1, NULL, NULL, NULL, NULL, 15.0, 'GRM', 130.0, 18.0, 12.0, 'MMT', 1, '2026-01-04 09:20:00'),
    ('WRT-004-STD', 'WRT-004', 'Standard', 1, NULL, NULL, NULL, NULL, 14.0, 'GRM', 148.0, 10.0, 10.0, 'MMT', 1, '2026-01-13 09:20:00'),
    ('CNS-001-STD', 'CNS-001', 'Standard', 1, NULL, NULL, NULL, NULL, 1.15, 'KGM', 360.0, 130.0, 150.0, 'MMT', 1, '2026-01-22 09:20:00'),
    ('CNS-002-STD', 'CNS-002', 'Standard', 1, NULL, NULL, NULL, NULL, 95.0, 'GRM', 115.0, 45.0, 65.0, 'MMT', 1, '2026-01-31 09:20:00'),
    ('CNS-003-STD', 'CNS-003', 'Standard', 1, NULL, NULL, NULL, NULL, 430.0, 'GRM', 110.0, 110.0, 140.0, 'MMT', 1, '2026-02-09 09:20:00'),
    ('CNS-004-STD', 'CNS-004', 'Standard', 1, NULL, NULL, NULL, NULL, 540.0, 'GRM', 70.0, 70.0, 230.0, 'MMT', 1, '2026-02-18 09:20:00'),
    ('FST-001-30', 'FST-001', 'M8 x 30 mm', 1, 'Hex Bolt M8 Zinc Plated - M8 x 30 mm.', 0.08, 0.29, 'USD', 22.0, 'GRM', 40.0, 13.0, 13.0, 'MMT', 1, '2026-02-27 09:20:00'),
    ('FST-001-40', 'FST-001', 'M8 x 40 mm', 0, 'Hex Bolt M8 Zinc Plated - M8 x 40 mm.', 0.09, 0.33, 'USD', 22.0, 'GRM', 40.0, 13.0, 13.0, 'MMT', 1, '2026-02-27 09:20:00'),
    ('FST-001-60', 'FST-001', 'M8 x 60 mm', 0, 'Hex Bolt M8 Zinc Plated - M8 x 60 mm.', 0.11, 0.38, 'USD', 22.0, 'GRM', 40.0, 13.0, 13.0, 'MMT', 1, '2026-02-27 09:20:00'),
    ('FST-002-STD', 'FST-002', 'Standard', 1, NULL, NULL, NULL, NULL, 6.0, 'GRM', 13.0, 13.0, 7.0, 'MMT', 1, '2026-03-08 09:20:00'),
    ('FST-003-STD', 'FST-003', 'Standard', 1, NULL, NULL, NULL, NULL, 3.0, 'GRM', 16.0, 16.0, 2.0, 'MMT', 1, '2026-03-17 09:20:00'),
    ('BRG-001-STD', 'BRG-001', 'Standard', 1, NULL, NULL, NULL, NULL, 105.0, 'GRM', 47.0, 47.0, 14.0, 'MMT', 1, '2026-03-26 09:20:00'),
    ('BRG-002-STD', 'BRG-002', 'Standard', 1, NULL, NULL, NULL, NULL, 180.0, 'GRM', 52.0, 52.0, 17.0, 'MMT', 1, '2026-04-04 09:20:00'),
    ('BRG-003-STD', 'BRG-003', 'Standard', 1, NULL, NULL, NULL, NULL, 22.0, 'GRM', 24.0, 15.0, 15.0, 'MMT', 1, '2026-04-13 09:20:00'),
    ('SFT-001-WHT', 'SFT-001', 'White', 1, 'Safety Helmet Class E - White.', 7.4, 18.9, 'USD', 390.0, 'GRM', 290.0, 230.0, 180.0, 'MMT', 1, '2026-04-22 09:20:00'),
    ('SFT-001-YEL', 'SFT-001', 'Yellow', 0, 'Safety Helmet Class E - Yellow.', 7.4, 18.9, 'USD', 390.0, 'GRM', 290.0, 230.0, 180.0, 'MMT', 1, '2026-04-22 09:20:00'),
    ('SFT-001-ORG', 'SFT-001', 'Orange', 0, 'Safety Helmet Class E - Orange.', 7.4, 18.9, 'USD', 390.0, 'GRM', 290.0, 230.0, 180.0, 'MMT', 1, '2026-04-22 09:20:00'),
    ('SFT-002-M', 'SFT-002', 'Medium', 1, 'Nitrile Gloves Box of 100 - Medium.', 4.2, 9.99, 'USD', 520.0, 'GRM', 240.0, 125.0, 70.0, 'MMT', 1, '2026-05-01 09:20:00'),
    ('SFT-002-L', 'SFT-002', 'Large', 0, 'Nitrile Gloves Box of 100 - Large.', 4.2, 9.99, 'USD', 520.0, 'GRM', 240.0, 125.0, 70.0, 'MMT', 1, '2026-05-01 09:20:00'),
    ('SFT-002-XL', 'SFT-002', 'Extra Large', 0, 'Nitrile Gloves Box of 100 - Extra Large.', 4.35, 10.49, 'USD', 520.0, 'GRM', 240.0, 125.0, 70.0, 'MMT', 1, '2026-05-01 09:20:00'),
    ('SRV-001-STD', 'SRV-001', 'Standard', 1, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, '2026-05-10 09:20:00'),
    ('SRV-002-STD', 'SRV-002', 'Standard', 1, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, '2026-05-19 09:20:00');

INSERT INTO tmp_seed_attribute (sku, attribute_code, attribute_value, sort_order, create_time) VALUES
    ('LAP-001-8-256', 'CONFIG', '8 GB / 256 GB', 0, '2025-03-04 09:20:00'),
    ('LAP-001-8-256', 'WARRANTY_MONTHS', '36', 1, '2025-03-04 09:20:00'),
    ('LAP-001-8-256', 'SCREEN_SIZE', '14', 2, '2025-03-04 09:20:00'),
    ('LAP-001-16-512', 'CONFIG', '16 GB / 512 GB', 0, '2025-03-04 09:20:00'),
    ('LAP-001-32-1024', 'CONFIG', '32 GB / 1 TB', 0, '2025-03-04 09:20:00'),
    ('LAP-002-16-512', 'CONFIG', '16 GB / 512 GB', 0, '2025-03-13 09:20:00'),
    ('LAP-002-16-512', 'WARRANTY_MONTHS', '24', 1, '2025-03-13 09:20:00'),
    ('LAP-002-16-512', 'SCREEN_SIZE', '13', 2, '2025-03-13 09:20:00'),
    ('LAP-002-16-1024', 'CONFIG', '16 GB / 1 TB', 0, '2025-03-13 09:20:00'),
    ('LAP-003-STD', 'WARRANTY_MONTHS', '36', 0, '2025-03-22 09:20:00'),
    ('LAP-003-STD', 'SCREEN_SIZE', '16', 1, '2025-03-22 09:20:00'),
    ('LAP-004-STD', 'WARRANTY_MONTHS', '12', 0, '2025-03-31 09:20:00'),
    ('LAP-004-STD', 'SCREEN_SIZE', '11', 1, '2025-03-31 09:20:00'),
    ('LAP-005-STD', 'WARRANTY_MONTHS', '24', 0, '2025-04-09 09:20:00'),
    ('MON-001-STD', 'SCREEN_SIZE', '24', 0, '2025-04-18 09:20:00'),
    ('MON-001-STD', 'WARRANTY_MONTHS', '36', 1, '2025-04-18 09:20:00'),
    ('MON-002-FIX', 'CONFIG', 'Fixed Stand', 0, '2025-04-27 09:20:00'),
    ('MON-002-FIX', 'SCREEN_SIZE', '27', 1, '2025-04-27 09:20:00'),
    ('MON-002-ERG', 'CONFIG', 'Ergonomic Stand', 0, '2025-04-27 09:20:00'),
    ('MON-003-STD', 'SCREEN_SIZE', '34', 0, '2025-05-06 09:20:00'),
    ('PER-001-BLK', 'COLOR', 'Black', 0, '2025-05-24 09:20:00'),
    ('PER-001-GRP', 'COLOR', 'Graphite', 0, '2025-05-24 09:20:00'),
    ('PER-001-WHT', 'COLOR', 'White', 0, '2025-05-24 09:20:00'),
    ('PER-002-RED', 'SWITCH', 'Linear Red', 0, '2025-06-02 09:20:00'),
    ('PER-002-BRN', 'SWITCH', 'Tactile Brown', 0, '2025-06-02 09:20:00'),
    ('PER-002-BLU', 'SWITCH', 'Clicky Blue', 0, '2025-06-02 09:20:00'),
    ('PER-003-STD', 'WARRANTY_MONTHS', '24', 0, '2025-06-11 09:20:00'),
    ('PER-005-BLK', 'COLOR', 'Black', 0, '2025-06-29 09:20:00'),
    ('PER-005-SLV', 'COLOR', 'Silver', 0, '2025-06-29 09:20:00'),
    ('CBL-001-BLK', 'COLOR', 'Black', 0, '2025-07-08 09:20:00'),
    ('CBL-001-WHT', 'COLOR', 'White', 0, '2025-07-08 09:20:00'),
    ('CBL-003-GRY', 'COLOR', 'Grey', 0, '2025-07-26 09:20:00'),
    ('CBL-003-BLU', 'COLOR', 'Blue', 0, '2025-07-26 09:20:00'),
    ('CBL-003-YEL', 'COLOR', 'Yellow', 0, '2025-07-26 09:20:00'),
    ('DSK-001-120', 'SIZE', '120 x 60 cm', 0, '2025-08-13 09:20:00'),
    ('DSK-001-120', 'MATERIAL', 'Powder-coated steel frame, laminated MDF top', 1, '2025-08-13 09:20:00'),
    ('DSK-001-140', 'SIZE', '140 x 70 cm', 0, '2025-08-13 09:20:00'),
    ('DSK-001-160', 'SIZE', '160 x 80 cm', 0, '2025-08-13 09:20:00'),
    ('DSK-002-STD', 'MATERIAL', 'Laminated MDF, steel legs', 0, '2025-08-22 09:20:00'),
    ('DSK-003-STD', 'MATERIAL', 'Laminated chipboard, steel frame', 0, '2025-08-31 09:20:00'),
    ('CHR-001-BLK', 'COLOR', 'Black', 0, '2025-09-09 09:20:00'),
    ('CHR-001-BLK', 'MATERIAL', 'Nylon mesh, polyamide base', 1, '2025-09-09 09:20:00'),
    ('CHR-001-GRY', 'COLOR', 'Grey', 0, '2025-09-09 09:20:00'),
    ('CHR-001-NVY', 'COLOR', 'Navy', 0, '2025-09-09 09:20:00'),
    ('CHR-002-BLK', 'COLOR', 'Black', 0, '2025-09-18 09:20:00'),
    ('CHR-002-BLK', 'MATERIAL', 'Bonded leather, aluminium base', 1, '2025-09-18 09:20:00'),
    ('CHR-002-BRN', 'COLOR', 'Brown', 0, '2025-09-18 09:20:00'),
    ('CHR-003-STD', 'MATERIAL', 'Polypropylene shell, chromed steel', 0, '2025-09-27 09:20:00'),
    ('STG-001-STD', 'MATERIAL', 'Powder-coated steel', 0, '2025-10-15 09:20:00'),
    ('STG-002-WHT', 'COLOR', 'White', 0, '2025-10-24 09:20:00'),
    ('STG-002-GRP', 'COLOR', 'Graphite', 0, '2025-10-24 09:20:00'),
    ('STG-003-STD', 'MATERIAL', 'Galvanised steel', 0, '2025-11-02 09:20:00'),
    ('PPR-001-STD', 'RECYCLABLE', 'true', 0, '2025-11-11 09:20:00'),
    ('PPR-002-STD', 'RECYCLABLE', 'true', 0, '2025-11-20 09:20:00'),
    ('PPR-004-YEL', 'COLOR', 'Yellow', 0, '2025-12-08 09:20:00'),
    ('PPR-004-PNK', 'COLOR', 'Pink', 0, '2025-12-08 09:20:00'),
    ('PPR-004-GRN', 'COLOR', 'Green', 0, '2025-12-08 09:20:00'),
    ('PPR-004-BLU', 'COLOR', 'Blue', 0, '2025-12-08 09:20:00'),
    ('WRT-001-BLK', 'COLOR', 'Black', 0, '2025-12-17 09:20:00'),
    ('WRT-001-BLU', 'COLOR', 'Blue', 0, '2025-12-17 09:20:00'),
    ('WRT-001-RED', 'COLOR', 'Red', 0, '2025-12-17 09:20:00'),
    ('WRT-002-BLK', 'COLOR', 'Black', 0, '2025-12-26 09:20:00'),
    ('WRT-002-BLU', 'COLOR', 'Blue', 0, '2025-12-26 09:20:00'),
    ('WRT-002-RED', 'COLOR', 'Red', 0, '2025-12-26 09:20:00'),
    ('WRT-002-GRN', 'COLOR', 'Green', 0, '2025-12-26 09:20:00'),
    ('FST-001-30', 'SIZE', 'M8 x 30 mm', 0, '2026-02-27 09:20:00'),
    ('FST-001-30', 'MATERIAL', 'Zinc plated carbon steel', 1, '2026-02-27 09:20:00'),
    ('FST-001-40', 'SIZE', 'M8 x 40 mm', 0, '2026-02-27 09:20:00'),
    ('FST-001-60', 'SIZE', 'M8 x 60 mm', 0, '2026-02-27 09:20:00'),
    ('FST-002-STD', 'MATERIAL', 'Zinc plated carbon steel', 0, '2026-03-08 09:20:00'),
    ('FST-003-STD', 'MATERIAL', 'A2 stainless steel', 0, '2026-03-17 09:20:00'),
    ('BRG-001-STD', 'MATERIAL', 'Chrome steel GCr15', 0, '2026-03-26 09:20:00'),
    ('SFT-001-WHT', 'COLOR', 'White', 0, '2026-04-22 09:20:00'),
    ('SFT-001-WHT', 'MATERIAL', 'High density polyethylene', 1, '2026-04-22 09:20:00'),
    ('SFT-001-YEL', 'COLOR', 'Yellow', 0, '2026-04-22 09:20:00'),
    ('SFT-001-ORG', 'COLOR', 'Orange', 0, '2026-04-22 09:20:00'),
    ('SFT-002-M', 'SIZE', 'Medium', 0, '2026-05-01 09:20:00'),
    ('SFT-002-L', 'SIZE', 'Large', 0, '2026-05-01 09:20:00'),
    ('SFT-002-XL', 'SIZE', 'Extra Large', 0, '2026-05-01 09:20:00');

INSERT INTO tmp_seed_barcode (sku, barcode_value, barcode_type, region, is_primary, create_time) VALUES
    ('LAP-001-8-256', '6900000000014', 'EAN13', 'CN', 1, '2025-03-04 09:20:00'),
    ('LAP-001-16-512', '6900000000021', 'EAN13', 'CN', 1, '2025-03-04 09:20:00'),
    ('LAP-001-32-1024', '6900000000038', 'EAN13', 'CN', 1, '2025-03-04 09:20:00'),
    ('LAP-002-16-512', '6900000000045', 'EAN13', 'CN', 1, '2025-03-13 09:20:00'),
    ('LAP-002-16-512', 'C128-LAP-002-16-512', 'CODE128', NULL, 0, '2025-03-13 09:20:00'),
    ('LAP-002-16-1024', '6900000000052', 'EAN13', 'CN', 1, '2025-03-13 09:20:00'),
    ('LAP-003-STD', '6900000000069', 'EAN13', 'CN', 1, '2025-03-22 09:20:00'),
    ('LAP-004-STD', '6900000000076', 'EAN13', 'CN', 1, '2025-03-31 09:20:00'),
    ('LAP-005-STD', '6900000000083', 'EAN13', 'CN', 1, '2025-04-09 09:20:00'),
    ('LAP-005-STD', 'C128-LAP-005-STD', 'CODE128', NULL, 0, '2025-04-09 09:20:00'),
    ('MON-001-STD', '6900000000090', 'EAN13', 'CN', 1, '2025-04-18 09:20:00'),
    ('MON-002-FIX', '6900000000106', 'EAN13', 'CN', 1, '2025-04-27 09:20:00'),
    ('MON-002-ERG', '6900000000113', 'EAN13', 'CN', 1, '2025-04-27 09:20:00'),
    ('MON-003-STD', '6900000000120', 'EAN13', 'CN', 1, '2025-05-06 09:20:00'),
    ('MON-003-STD', 'C128-MON-003-STD', 'CODE128', NULL, 0, '2025-05-06 09:20:00'),
    ('MON-004-STD', '6900000000137', 'EAN13', 'CN', 1, '2025-05-15 09:20:00'),
    ('PER-001-BLK', '6900000000144', 'EAN13', 'CN', 1, '2025-05-24 09:20:00'),
    ('PER-001-GRP', '6900000000151', 'EAN13', 'CN', 1, '2025-05-24 09:20:00'),
    ('PER-001-WHT', '6900000000168', 'EAN13', 'CN', 1, '2025-05-24 09:20:00'),
    ('PER-002-RED', '6900000000175', 'EAN13', 'CN', 1, '2025-06-02 09:20:00'),
    ('PER-002-BRN', '6900000000182', 'EAN13', 'CN', 1, '2025-06-02 09:20:00'),
    ('PER-002-BLU', '6900000000199', 'EAN13', 'CN', 1, '2025-06-02 09:20:00'),
    ('PER-003-STD', '6900000000205', 'EAN13', 'CN', 1, '2025-06-11 09:20:00'),
    ('PER-003-STD', 'C128-PER-003-STD', 'CODE128', NULL, 0, '2025-06-11 09:20:00'),
    ('PER-004-STD', '6900000000212', 'EAN13', 'CN', 1, '2025-06-20 09:20:00'),
    ('PER-005-BLK', '6900000000229', 'EAN13', 'CN', 1, '2025-06-29 09:20:00'),
    ('PER-005-SLV', '6900000000236', 'EAN13', 'CN', 1, '2025-06-29 09:20:00'),
    ('CBL-001-BLK', '6900000000243', 'EAN13', 'CN', 1, '2025-07-08 09:20:00'),
    ('CBL-001-BLK', 'C128-CBL-001-BLK', 'CODE128', NULL, 0, '2025-07-08 09:20:00'),
    ('CBL-001-WHT', '6900000000250', 'EAN13', 'CN', 1, '2025-07-08 09:20:00'),
    ('CBL-002-STD', '6900000000267', 'EAN13', 'CN', 1, '2025-07-17 09:20:00'),
    ('CBL-003-GRY', '6900000000274', 'EAN13', 'CN', 1, '2025-07-26 09:20:00'),
    ('CBL-003-BLU', '6900000000281', 'EAN13', 'CN', 1, '2025-07-26 09:20:00'),
    ('CBL-003-YEL', '6900000000298', 'EAN13', 'CN', 1, '2025-07-26 09:20:00'),
    ('CBL-004-STD', '6900000000304', 'EAN13', 'CN', 1, '2025-08-04 09:20:00'),
    ('DSK-001-120', '0030000000311', 'EAN13', 'US', 1, '2025-08-13 09:20:00'),
    ('DSK-001-140', '0030000000328', 'EAN13', 'US', 1, '2025-08-13 09:20:00'),
    ('DSK-001-160', '0030000000335', 'EAN13', 'US', 1, '2025-08-13 09:20:00'),
    ('DSK-002-STD', '0030000000342', 'EAN13', 'US', 1, '2025-08-22 09:20:00'),
    ('DSK-003-STD', '0030000000359', 'EAN13', 'US', 1, '2025-08-31 09:20:00'),
    ('CHR-001-BLK', '0030000000366', 'EAN13', 'US', 1, '2025-09-09 09:20:00'),
    ('CHR-001-BLK', 'C128-CHR-001-BLK', 'CODE128', NULL, 0, '2025-09-09 09:20:00'),
    ('CHR-001-GRY', '0030000000373', 'EAN13', 'US', 1, '2025-09-09 09:20:00'),
    ('CHR-001-NVY', '0030000000380', 'EAN13', 'US', 1, '2025-09-09 09:20:00'),
    ('CHR-002-BLK', '0030000000397', 'EAN13', 'US', 1, '2025-09-18 09:20:00'),
    ('CHR-002-BRN', '0030000000403', 'EAN13', 'US', 1, '2025-09-18 09:20:00'),
    ('CHR-003-STD', '0030000000410', 'EAN13', 'US', 1, '2025-09-27 09:20:00'),
    ('CHR-004-STD', '0030000000427', 'EAN13', 'US', 1, '2025-10-06 09:20:00'),
    ('STG-001-STD', '0030000000434', 'EAN13', 'US', 1, '2025-10-15 09:20:00'),
    ('STG-002-WHT', '0030000000441', 'EAN13', 'US', 1, '2025-10-24 09:20:00'),
    ('STG-002-WHT', 'C128-STG-002-WHT', 'CODE128', NULL, 0, '2025-10-24 09:20:00'),
    ('STG-002-GRP', '0030000000458', 'EAN13', 'US', 1, '2025-10-24 09:20:00'),
    ('STG-003-STD', '0030000000465', 'EAN13', 'US', 1, '2025-11-02 09:20:00'),
    ('PPR-001-STD', '5000000000470', 'EAN13', 'EU', 1, '2025-11-11 09:20:00'),
    ('PPR-002-STD', '5000000000487', 'EAN13', 'EU', 1, '2025-11-20 09:20:00'),
    ('PPR-003-STD', '5000000000494', 'EAN13', 'EU', 1, '2025-11-29 09:20:00'),
    ('PPR-004-YEL', '5000000000500', 'EAN13', 'EU', 1, '2025-12-08 09:20:00'),
    ('PPR-004-PNK', '5000000000517', 'EAN13', 'EU', 1, '2025-12-08 09:20:00'),
    ('PPR-004-GRN', '5000000000524', 'EAN13', 'EU', 1, '2025-12-08 09:20:00'),
    ('PPR-004-BLU', '5000000000531', 'EAN13', 'EU', 1, '2025-12-08 09:20:00'),
    ('WRT-001-BLK', '5000000000548', 'EAN13', 'EU', 1, '2025-12-17 09:20:00'),
    ('WRT-001-BLU', '5000000000555', 'EAN13', 'EU', 1, '2025-12-17 09:20:00'),
    ('WRT-001-RED', '5000000000562', 'EAN13', 'EU', 1, '2025-12-17 09:20:00'),
    ('WRT-002-BLK', '5000000000579', 'EAN13', 'EU', 1, '2025-12-26 09:20:00'),
    ('WRT-002-BLU', '5000000000586', 'EAN13', 'EU', 1, '2025-12-26 09:20:00'),
    ('WRT-002-RED', '5000000000593', 'EAN13', 'EU', 1, '2025-12-26 09:20:00'),
    ('WRT-002-GRN', '5000000000609', 'EAN13', 'EU', 1, '2025-12-26 09:20:00'),
    ('WRT-003-STD', '5000000000616', 'EAN13', 'EU', 1, '2026-01-04 09:20:00'),
    ('WRT-004-STD', '5000000000623', 'EAN13', 'EU', 1, '2026-01-13 09:20:00'),
    ('CNS-001-STD', '5000000000630', 'EAN13', 'EU', 1, '2026-01-22 09:20:00'),
    ('CNS-002-STD', '5000000000647', 'EAN13', 'EU', 1, '2026-01-31 09:20:00'),
    ('CNS-003-STD', '5000000000654', 'EAN13', 'EU', 1, '2026-02-09 09:20:00'),
    ('CNS-004-STD', '5000000000661', 'EAN13', 'EU', 1, '2026-02-18 09:20:00'),
    ('FST-001-30', '7300000000677', 'EAN13', 'EU', 1, '2026-02-27 09:20:00'),
    ('FST-001-40', '7300000000684', 'EAN13', 'EU', 1, '2026-02-27 09:20:00'),
    ('FST-001-60', '7300000000691', 'EAN13', 'EU', 1, '2026-02-27 09:20:00'),
    ('FST-002-STD', '7300000000707', 'EAN13', 'EU', 1, '2026-03-08 09:20:00'),
    ('FST-003-STD', '7300000000714', 'EAN13', 'EU', 1, '2026-03-17 09:20:00'),
    ('BRG-001-STD', '7300000000721', 'EAN13', 'EU', 1, '2026-03-26 09:20:00'),
    ('BRG-001-STD', 'C128-BRG-001-STD', 'CODE128', NULL, 0, '2026-03-26 09:20:00'),
    ('BRG-002-STD', '7300000000738', 'EAN13', 'EU', 1, '2026-04-04 09:20:00'),
    ('BRG-003-STD', '7300000000745', 'EAN13', 'EU', 1, '2026-04-13 09:20:00'),
    ('SFT-001-WHT', '7300000000752', 'EAN13', 'EU', 1, '2026-04-22 09:20:00'),
    ('SFT-001-YEL', '7300000000769', 'EAN13', 'EU', 1, '2026-04-22 09:20:00'),
    ('SFT-001-ORG', '7300000000776', 'EAN13', 'EU', 1, '2026-04-22 09:20:00'),
    ('SFT-002-M', '7300000000783', 'EAN13', 'EU', 1, '2026-05-01 09:20:00'),
    ('SFT-002-L', '7300000000790', 'EAN13', 'EU', 1, '2026-05-01 09:20:00'),
    ('SFT-002-XL', '7300000000806', 'EAN13', 'EU', 1, '2026-05-01 09:20:00');

INSERT INTO tmp_seed_supplier_code (sku, supplier_code, supplier_part_no, is_preferred,
                                    last_purchase_cost, lead_time_days, create_time) VALUES
    ('LAP-001-8-256', 'SUP-006', '006-LAP-001-8-256', 1, 691.2, 10, '2025-03-04 09:20:00'),
    ('LAP-001-16-512', 'SUP-006', '006-LAP-001-16-512', 1, 772.8, 10, '2025-03-04 09:20:00'),
    ('LAP-001-32-1024', 'SUP-006', '006-LAP-001-32-1024', 1, 892.8, 10, '2025-03-04 09:20:00'),
    ('LAP-002-16-512', 'SUP-006', '006-LAP-002-16-512', 1, 854.4, 10, '2025-03-13 09:20:00'),
    ('LAP-002-16-512', 'SUP-001', '001-LAP-002-16-512', 0, 897.12, 14, '2025-03-13 09:20:00'),
    ('LAP-002-16-1024', 'SUP-006', '006-LAP-002-16-1024', 1, 921.6, 10, '2025-03-13 09:20:00'),
    ('LAP-003-STD', 'SUP-006', '006-LAP-003-STD', 1, 1516.8, 10, '2025-03-22 09:20:00'),
    ('LAP-004-STD', 'SUP-006', '006-LAP-004-STD', 1, 297.6, 10, '2025-03-31 09:20:00'),
    ('LAP-005-STD', 'SUP-006', '006-LAP-005-STD', 1, 614.4, 10, '2025-04-09 09:20:00'),
    ('LAP-005-STD', 'SUP-001', '001-LAP-005-STD', 0, 645.12, 14, '2025-04-09 09:20:00'),
    ('MON-001-STD', 'SUP-006', '006-MON-001-STD', 1, 91.2, 10, '2025-04-18 09:20:00'),
    ('MON-002-FIX', 'SUP-006', '006-MON-002-FIX', 1, 158.4, 10, '2025-04-27 09:20:00'),
    ('MON-002-ERG', 'SUP-006', '006-MON-002-ERG', 1, 179.52, 10, '2025-04-27 09:20:00'),
    ('MON-003-STD', 'SUP-006', '006-MON-003-STD', 1, 326.4, 10, '2025-05-06 09:20:00'),
    ('MON-003-STD', 'SUP-001', '001-MON-003-STD', 0, 342.72, 14, '2025-05-06 09:20:00'),
    ('PER-001-BLK', 'SUP-006', '006-PER-001-BLK', 1, 6.24, 10, '2025-05-24 09:20:00'),
    ('PER-001-GRP', 'SUP-006', '006-PER-001-GRP', 1, 6.24, 10, '2025-05-24 09:20:00'),
    ('PER-001-WHT', 'SUP-006', '006-PER-001-WHT', 1, 6.432, 10, '2025-05-24 09:20:00'),
    ('PER-002-RED', 'SUP-006', '006-PER-002-RED', 1, 26.88, 10, '2025-06-02 09:20:00'),
    ('PER-002-RED', 'SUP-001', '001-PER-002-RED', 0, 28.224, 14, '2025-06-02 09:20:00'),
    ('PER-002-BRN', 'SUP-006', '006-PER-002-BRN', 1, 26.88, 10, '2025-06-02 09:20:00'),
    ('PER-002-BLU', 'SUP-006', '006-PER-002-BLU', 1, 26.88, 10, '2025-06-02 09:20:00'),
    ('PER-003-STD', 'SUP-006', '006-PER-003-STD', 1, 71.04, 10, '2025-06-11 09:20:00'),
    ('PER-004-STD', 'SUP-006', '006-PER-004-STD', 1, 17.28, 10, '2025-06-20 09:20:00'),
    ('PER-005-BLK', 'SUP-006', '006-PER-005-BLK', 1, 40.32, 10, '2025-06-29 09:20:00'),
    ('PER-005-BLK', 'SUP-001', '001-PER-005-BLK', 0, 42.336, 14, '2025-06-29 09:20:00'),
    ('PER-005-SLV', 'SUP-006', '006-PER-005-SLV', 1, 41.76, 10, '2025-06-29 09:20:00'),
    ('CBL-001-BLK', 'SUP-006', '006-CBL-001-BLK', 1, 1.728, 10, '2025-07-08 09:20:00'),
    ('CBL-001-WHT', 'SUP-006', '006-CBL-001-WHT', 1, 1.728, 10, '2025-07-08 09:20:00'),
    ('CBL-002-STD', 'SUP-006', '006-CBL-002-STD', 1, 3.072, 10, '2025-07-17 09:20:00'),
    ('CBL-003-GRY', 'SUP-006', '006-CBL-003-GRY', 1, 1.056, 10, '2025-07-26 09:20:00'),
    ('CBL-003-GRY', 'SUP-001', '001-CBL-003-GRY', 0, 1.1088, 14, '2025-07-26 09:20:00'),
    ('CBL-003-BLU', 'SUP-006', '006-CBL-003-BLU', 1, 1.056, 10, '2025-07-26 09:20:00'),
    ('CBL-003-YEL', 'SUP-006', '006-CBL-003-YEL', 1, 1.056, 10, '2025-07-26 09:20:00'),
    ('CBL-004-STD', 'SUP-006', '006-CBL-004-STD', 1, 2.304, 10, '2025-08-04 09:20:00'),
    ('DSK-001-120', 'SUP-005', '005-DSK-001-120', 1, 201.6, 28, '2025-08-13 09:20:00'),
    ('DSK-001-140', 'SUP-005', '005-DSK-001-140', 1, 224.64, 28, '2025-08-13 09:20:00'),
    ('DSK-001-160', 'SUP-005', '005-DSK-001-160', 1, 251.52, 28, '2025-08-13 09:20:00'),
    ('DSK-002-STD', 'SUP-005', '005-DSK-002-STD', 1, 91.2, 28, '2025-08-22 09:20:00'),
    ('DSK-002-STD', 'SUP-001', '001-DSK-002-STD', 0, 95.76, 14, '2025-08-22 09:20:00'),
    ('DSK-003-STD', 'SUP-005', '005-DSK-003-STD', 1, 168.0, 28, '2025-08-31 09:20:00'),
    ('CHR-001-BLK', 'SUP-005', '005-CHR-001-BLK', 1, 115.2, 28, '2025-09-09 09:20:00'),
    ('CHR-001-GRY', 'SUP-005', '005-CHR-001-GRY', 1, 115.2, 28, '2025-09-09 09:20:00'),
    ('CHR-001-NVY', 'SUP-005', '005-CHR-001-NVY', 1, 118.08, 28, '2025-09-09 09:20:00'),
    ('CHR-002-BLK', 'SUP-005', '005-CHR-002-BLK', 1, 187.2, 28, '2025-09-18 09:20:00'),
    ('CHR-002-BLK', 'SUP-001', '001-CHR-002-BLK', 0, 196.56, 14, '2025-09-18 09:20:00'),
    ('CHR-002-BRN', 'SUP-005', '005-CHR-002-BRN', 1, 192.96, 28, '2025-09-18 09:20:00'),
    ('CHR-003-STD', 'SUP-005', '005-CHR-003-STD', 1, 32.64, 28, '2025-09-27 09:20:00'),
    ('STG-001-STD', 'SUP-005', '005-STG-001-STD', 1, 84.48, 28, '2025-10-15 09:20:00'),
    ('STG-001-STD', 'SUP-001', '001-STG-001-STD', 0, 88.704, 14, '2025-10-15 09:20:00'),
    ('STG-002-WHT', 'SUP-005', '005-STG-002-WHT', 1, 49.92, 28, '2025-10-24 09:20:00'),
    ('STG-002-GRP', 'SUP-005', '005-STG-002-GRP', 1, 49.92, 28, '2025-10-24 09:20:00'),
    ('STG-003-STD', 'SUP-005', '005-STG-003-STD', 1, 61.44, 28, '2025-11-02 09:20:00'),
    ('PPR-001-STD', 'SUP-002', '002-PPR-001-STD', 1, 2.496, 7, '2025-11-11 09:20:00'),
    ('PPR-001-STD', 'SUP-007', '007-PPR-001-STD', 0, 2.6208, 18, '2025-11-11 09:20:00'),
    ('PPR-002-STD', 'SUP-002', '002-PPR-002-STD', 1, 4.896, 7, '2025-11-20 09:20:00'),
    ('PPR-003-STD', 'SUP-002', '002-PPR-003-STD', 1, 0.336, 7, '2025-11-29 09:20:00'),
    ('PPR-004-YEL', 'SUP-002', '002-PPR-004-YEL', 1, 0.4032, 7, '2025-12-08 09:20:00'),
    ('PPR-004-YEL', 'SUP-007', '007-PPR-004-YEL', 0, 0.4234, 18, '2025-12-08 09:20:00'),
    ('PPR-004-PNK', 'SUP-002', '002-PPR-004-PNK', 1, 0.4032, 7, '2025-12-08 09:20:00'),
    ('PPR-004-GRN', 'SUP-002', '002-PPR-004-GRN', 1, 0.4032, 7, '2025-12-08 09:20:00'),
    ('PPR-004-BLU', 'SUP-002', '002-PPR-004-BLU', 1, 0.4032, 7, '2025-12-08 09:20:00'),
    ('WRT-001-BLK', 'SUP-002', '002-WRT-001-BLK', 1, 0.1056, 7, '2025-12-17 09:20:00'),
    ('WRT-001-BLU', 'SUP-002', '002-WRT-001-BLU', 1, 0.1056, 7, '2025-12-17 09:20:00'),
    ('WRT-001-RED', 'SUP-002', '002-WRT-001-RED', 1, 0.1056, 7, '2025-12-17 09:20:00'),
    ('WRT-002-BLK', 'SUP-002', '002-WRT-002-BLK', 1, 0.3648, 7, '2025-12-26 09:20:00'),
    ('WRT-002-BLU', 'SUP-002', '002-WRT-002-BLU', 1, 0.3648, 7, '2025-12-26 09:20:00'),
    ('WRT-002-RED', 'SUP-002', '002-WRT-002-RED', 1, 0.3648, 7, '2025-12-26 09:20:00'),
    ('WRT-002-GRN', 'SUP-002', '002-WRT-002-GRN', 1, 0.3648, 7, '2025-12-26 09:20:00'),
    ('WRT-003-STD', 'SUP-002', '002-WRT-003-STD', 1, 0.2784, 7, '2026-01-04 09:20:00'),
    ('WRT-003-STD', 'SUP-007', '007-WRT-003-STD', 0, 0.2923, 18, '2026-01-04 09:20:00'),
    ('WRT-004-STD', 'SUP-002', '002-WRT-004-STD', 1, 0.528, 7, '2026-01-13 09:20:00'),
    ('CNS-001-STD', 'SUP-002', '002-CNS-001-STD', 1, 36.48, 7, '2026-01-22 09:20:00'),
    ('CNS-002-STD', 'SUP-002', '002-CNS-002-STD', 1, 13.44, 7, '2026-01-31 09:20:00'),
    ('CNS-002-STD', 'SUP-007', '007-CNS-002-STD', 0, 14.112, 18, '2026-01-31 09:20:00'),
    ('CNS-003-STD', 'SUP-002', '002-CNS-003-STD', 1, 2.016, 7, '2026-02-09 09:20:00'),
    ('CNS-004-STD', 'SUP-002', '002-CNS-004-STD', 1, 1.632, 7, '2026-02-18 09:20:00'),
    ('FST-001-30', 'SUP-003', '003-FST-001-30', 1, 0.0768, 21, '2026-02-27 09:20:00'),
    ('FST-001-30', 'SUP-004', '004-FST-001-30', 0, 0.0806, 30, '2026-02-27 09:20:00'),
    ('FST-001-40', 'SUP-003', '003-FST-001-40', 1, 0.0864, 21, '2026-02-27 09:20:00'),
    ('FST-001-60', 'SUP-003', '003-FST-001-60', 1, 0.1056, 21, '2026-02-27 09:20:00'),
    ('FST-002-STD', 'SUP-003', '003-FST-002-STD', 1, 0.0288, 21, '2026-03-08 09:20:00'),
    ('FST-003-STD', 'SUP-003', '003-FST-003-STD', 1, 0.0192, 21, '2026-03-17 09:20:00'),
    ('BRG-001-STD', 'SUP-003', '003-BRG-001-STD', 1, 1.824, 21, '2026-03-26 09:20:00'),
    ('BRG-001-STD', 'SUP-004', '004-BRG-001-STD', 0, 1.9152, 30, '2026-03-26 09:20:00'),
    ('BRG-002-STD', 'SUP-003', '003-BRG-002-STD', 1, 4.128, 21, '2026-04-04 09:20:00'),
    ('SFT-001-WHT', 'SUP-003', '003-SFT-001-WHT', 1, 7.104, 21, '2026-04-22 09:20:00'),
    ('SFT-001-WHT', 'SUP-004', '004-SFT-001-WHT', 0, 7.4592, 30, '2026-04-22 09:20:00'),
    ('SFT-001-YEL', 'SUP-003', '003-SFT-001-YEL', 1, 7.104, 21, '2026-04-22 09:20:00'),
    ('SFT-001-ORG', 'SUP-003', '003-SFT-001-ORG', 1, 7.104, 21, '2026-04-22 09:20:00'),
    ('SFT-002-M', 'SUP-003', '003-SFT-002-M', 1, 4.032, 21, '2026-05-01 09:20:00'),
    ('SFT-002-L', 'SUP-003', '003-SFT-002-L', 1, 4.032, 21, '2026-05-01 09:20:00'),
    ('SFT-002-XL', 'SUP-003', '003-SFT-002-XL', 1, 4.176, 21, '2026-05-01 09:20:00');

INSERT INTO tmp_seed_image (sku, storage_key, alt_text, sort_order, is_primary, create_time) VALUES
    ('LAP-001-8-256', 'seed-img-0001', 'ProBook 14 Business Laptop - front view', 0, 1, '2025-03-04 09:20:00'),
    ('LAP-002-16-512', 'seed-img-0002', 'UltraSlim 13 Ultrabook - front view', 0, 1, '2025-03-13 09:20:00'),
    ('LAP-003-STD', 'seed-img-0003', 'Workstation 16 Mobile Workstation - front view', 0, 1, '2025-03-22 09:20:00'),
    ('MON-001-STD', 'seed-img-0004', '24-inch IPS Office Monitor - front view', 0, 1, '2025-04-18 09:20:00'),
    ('MON-002-FIX', 'seed-img-0005', '27-inch QHD Monitor - front view', 0, 1, '2025-04-27 09:20:00'),
    ('PER-001-BLK', 'seed-img-0006', 'Wireless Optical Mouse - front view', 0, 1, '2025-05-24 09:20:00'),
    ('PER-002-RED', 'seed-img-0007', 'Mechanical Keyboard TKL - front view', 0, 1, '2025-06-02 09:20:00'),
    ('PER-005-BLK', 'seed-img-0008', 'Noise-Cancelling Headset - front view', 0, 1, '2025-06-29 09:20:00'),
    ('DSK-001-120', 'seed-img-0009', 'Height-Adjustable Standing Desk - front view', 0, 1, '2025-08-13 09:20:00'),
    ('CHR-001-BLK', 'seed-img-0010', 'Ergonomic Mesh Task Chair - front view', 0, 1, '2025-09-09 09:20:00'),
    ('PPR-001-STD', 'seed-img-0011', 'A4 Copy Paper 80 gsm - front view', 0, 1, '2025-11-11 09:20:00'),
    ('CNS-001-STD', 'seed-img-0012', 'Toner Cartridge Black High Yield - front view', 0, 1, '2026-01-22 09:20:00'),
    ('SFT-001-WHT', 'seed-img-0013', 'Safety Helmet Class E - front view', 0, 1, '2026-04-22 09:20:00');

-- ---------------------------------------------------------------------------
--  Load. Insert order follows the foreign-key dependency order.
-- ---------------------------------------------------------------------------
START TRANSACTION;

-- 1. categories - root level
INSERT INTO category_tb (cate_name, cate_abbr, description, parent_id, depth, active,
                         created_by, create_time)
SELECT s.cate_name, s.cate_abbr, s.description, NULL, 0, TRUE, @seed_user, s.create_time
FROM tmp_seed_category s
WHERE s.parent_name IS NULL
  AND NOT EXISTS (SELECT 1 FROM category_tb c
                  WHERE c.cate_name = s.cate_name AND c.parent_id IS NULL);

-- 2. categories - children (run once per level; this seed is two levels deep)
INSERT INTO category_tb (cate_name, cate_abbr, description, parent_id, depth, active,
                         created_by, create_time)
SELECT s.cate_name, s.cate_abbr, s.description, p.cate_id, p.depth + 1, TRUE,
       @seed_user, s.create_time
FROM tmp_seed_category s
JOIN category_tb p ON p.cate_name = s.parent_name AND p.parent_id IS NULL
WHERE s.parent_name IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM category_tb c
                  WHERE c.cate_name = s.cate_name AND c.parent_id = p.cate_id);

-- 3. materialised ancestor paths, e.g. /1/7/
UPDATE category_tb
SET path_cache = CONCAT('/', cate_id, '/')
WHERE parent_id IS NULL AND path_cache IS NULL;

UPDATE category_tb c
JOIN category_tb p ON p.cate_id = c.parent_id
SET c.path_cache = CONCAT(p.path_cache, c.cate_id, '/')
WHERE c.parent_id IS NOT NULL AND c.path_cache IS NULL AND p.path_cache IS NOT NULL;

-- 4. suppliers
INSERT INTO supplier_tb (supplier_code, legal_name, trade_name, status, tax_id,
                         created_by, create_time)
SELECT s.supplier_code, s.legal_name, s.trade_name, s.status, s.tax_id, @seed_user, s.create_time
FROM tmp_seed_supplier s
WHERE NOT EXISTS (SELECT 1 FROM supplier_tb x WHERE x.supplier_code = s.supplier_code);

-- 5. attribute definitions
INSERT INTO attribute_def_tb (attribute_code, display_name, value_type, uom_id,
                              is_variant_axis, active, created_by, create_time)
SELECT s.attribute_code, s.display_name, s.value_type, u.uom_id, s.is_variant_axis,
       TRUE, @seed_user, s.create_time
FROM tmp_seed_attr_def s
LEFT JOIN uom_tb u ON u.iso_code = s.uom_code
WHERE NOT EXISTS (SELECT 1 FROM attribute_def_tb x WHERE x.attribute_code = s.attribute_code);

-- 6. appendices (placeholder image files)
INSERT INTO appendix_tb (storage_key, path, file_name, file_exten, mime_type, byte_size,
                         active, created_by, create_time)
SELECT s.storage_key, s.path, s.file_name, s.file_exten, s.mime_type, s.byte_size,
       TRUE, @seed_user, s.create_time
FROM tmp_seed_appendix s
WHERE NOT EXISTS (SELECT 1 FROM appendix_tb x WHERE x.storage_key = s.storage_key);

-- 7. products
INSERT INTO product_tb (product_code, product_name, product_description, type, configurable,
                        status, base_uom_id, standard_cost, list_price, currency, tax_category,
                        version, created_by, create_time, updated_by, update_time)
SELECT s.product_code, s.product_name, s.product_description, s.type, s.configurable,
       s.status, u.uom_id, s.standard_cost, s.list_price, s.currency, s.tax_category,
       0, @seed_user, s.create_time,
       CASE WHEN s.update_time IS NULL THEN NULL ELSE @seed_user END, s.update_time
FROM tmp_seed_product s
JOIN uom_tb u ON u.iso_code = s.base_uom_code
WHERE NOT EXISTS (SELECT 1 FROM product_tb p WHERE p.product_code = s.product_code);

-- 8. variants
INSERT INTO product_variant_tb (product_id, sku, variant_name, is_default, description,
                                standard_cost, list_price, currency, net_weight, weight_uom_id,
                                `length`, width, height, size_uom_id, active, version,
                                created_by, create_time)
SELECT p.product_id, s.sku, s.variant_name, s.is_default, s.description,
       s.standard_cost, s.list_price, s.currency, s.net_weight, wu.uom_id,
       s.`length`, s.width, s.height, su.uom_id, s.active, 0, @seed_user, s.create_time
FROM tmp_seed_variant s
JOIN product_tb p ON p.product_code = s.product_code
LEFT JOIN uom_tb wu ON wu.iso_code = s.weight_uom_code
LEFT JOIN uom_tb su ON su.iso_code = s.size_uom_code
WHERE NOT EXISTS (SELECT 1 FROM product_variant_tb v WHERE v.sku = s.sku);

-- 9. product / category assignments
INSERT INTO product_category_rel_tb (product_id, cate_id, is_primary, active, created_by, create_time)
SELECT p.product_id, c.cate_id, s.is_primary, TRUE, @seed_user, s.create_time
FROM tmp_seed_product_category s
JOIN product_tb p ON p.product_code = s.product_code
JOIN category_tb c ON c.cate_name = s.cate_name AND c.cate_abbr = s.cate_abbr
WHERE NOT EXISTS (SELECT 1 FROM product_category_rel_tb x
                  WHERE x.product_id = p.product_id AND x.cate_id = c.cate_id);

-- 10. packaging conversions, e.g. 1 BOX = 12 EA
INSERT INTO product_uom_tb (product_id, uom_id, factor_to_base_uom, usage_type, active,
                            created_by, create_time)
SELECT p.product_id, u.uom_id, s.factor_to_base_uom, s.usage_type, TRUE, @seed_user, s.create_time
FROM tmp_seed_product_uom s
JOIN product_tb p ON p.product_code = s.product_code
JOIN uom_tb u ON u.iso_code = s.uom_code
WHERE NOT EXISTS (SELECT 1 FROM product_uom_tb x
                  WHERE x.product_id = p.product_id AND x.uom_id = u.uom_id);

-- 11. variant attributes
INSERT INTO product_attribute_tb (variant_id, attr_def_id, attribute_value, sort_order, active,
                                  created_by, create_time)
SELECT v.variant_id, d.attr_def_id, s.attribute_value, s.sort_order, TRUE, @seed_user, s.create_time
FROM tmp_seed_attribute s
JOIN product_variant_tb v ON v.sku = s.sku
JOIN attribute_def_tb d ON d.attribute_code = s.attribute_code
WHERE NOT EXISTS (SELECT 1 FROM product_attribute_tb x
                  WHERE x.variant_id = v.variant_id AND x.attr_def_id = d.attr_def_id);

-- 12. barcodes (EAN-13 values carry a valid check digit)
INSERT INTO product_barcode_tb (variant_id, barcode_value, barcode_type, region, is_primary,
                                active, created_by, create_time)
SELECT v.variant_id, s.barcode_value, s.barcode_type, s.region, s.is_primary, TRUE,
       @seed_user, s.create_time
FROM tmp_seed_barcode s
JOIN product_variant_tb v ON v.sku = s.sku
WHERE NOT EXISTS (SELECT 1 FROM product_barcode_tb x WHERE x.barcode_value = s.barcode_value);

-- 13. supplier part numbers
INSERT INTO product_supplier_code_tb (variant_id, supplier_id, supplier_part_no, is_preferred,
                                      last_purchase_cost, lead_time_days, active,
                                      created_by, create_time)
SELECT v.variant_id, sp.supplier_id, s.supplier_part_no, s.is_preferred, s.last_purchase_cost,
       s.lead_time_days, TRUE, @seed_user, s.create_time
FROM tmp_seed_supplier_code s
JOIN product_variant_tb v ON v.sku = s.sku
JOIN supplier_tb sp ON sp.supplier_code = s.supplier_code
WHERE NOT EXISTS (SELECT 1 FROM product_supplier_code_tb x
                  WHERE x.variant_id = v.variant_id AND x.supplier_id = sp.supplier_id);

-- 14. product images
INSERT INTO product_image_tb (variant_id, appendix_id, alt_text, sort_order, is_primary,
                              created_by, create_time)
SELECT v.variant_id, a.appendix_id, s.alt_text, s.sort_order, s.is_primary, @seed_user, s.create_time
FROM tmp_seed_image s
JOIN product_variant_tb v ON v.sku = s.sku
JOIN appendix_tb a ON a.storage_key = s.storage_key
WHERE NOT EXISTS (SELECT 1 FROM product_image_tb x
                  WHERE x.variant_id = v.variant_id AND x.appendix_id = a.appendix_id);

COMMIT;

DROP TEMPORARY TABLE IF EXISTS tmp_seed_category;
DROP TEMPORARY TABLE IF EXISTS tmp_seed_supplier;
DROP TEMPORARY TABLE IF EXISTS tmp_seed_attr_def;
DROP TEMPORARY TABLE IF EXISTS tmp_seed_appendix;
DROP TEMPORARY TABLE IF EXISTS tmp_seed_product;
DROP TEMPORARY TABLE IF EXISTS tmp_seed_product_category;
DROP TEMPORARY TABLE IF EXISTS tmp_seed_product_uom;
DROP TEMPORARY TABLE IF EXISTS tmp_seed_variant;
DROP TEMPORARY TABLE IF EXISTS tmp_seed_attribute;
DROP TEMPORARY TABLE IF EXISTS tmp_seed_barcode;
DROP TEMPORARY TABLE IF EXISTS tmp_seed_supplier_code;
DROP TEMPORARY TABLE IF EXISTS tmp_seed_image;

-- ---------------------------------------------------------------------------
--  Summary
-- ---------------------------------------------------------------------------
SELECT 'category_tb'              AS table_name, COUNT(*) AS rows_now FROM category_tb
UNION ALL SELECT 'supplier_tb',              COUNT(*) FROM supplier_tb
UNION ALL SELECT 'attribute_def_tb',         COUNT(*) FROM attribute_def_tb
UNION ALL SELECT 'appendix_tb',              COUNT(*) FROM appendix_tb
UNION ALL SELECT 'product_tb',               COUNT(*) FROM product_tb
UNION ALL SELECT 'product_variant_tb',       COUNT(*) FROM product_variant_tb
UNION ALL SELECT 'product_category_rel_tb',  COUNT(*) FROM product_category_rel_tb
UNION ALL SELECT 'product_uom_tb',           COUNT(*) FROM product_uom_tb
UNION ALL SELECT 'product_attribute_tb',     COUNT(*) FROM product_attribute_tb
UNION ALL SELECT 'product_barcode_tb',       COUNT(*) FROM product_barcode_tb
UNION ALL SELECT 'product_supplier_code_tb', COUNT(*) FROM product_supplier_code_tb
UNION ALL SELECT 'product_image_tb',         COUNT(*) FROM product_image_tb;

-- =============================================================================
--  Rollback: removes only the rows this script inserted.
--  Order is the reverse of the load order.
-- =============================================================================
/*
START TRANSACTION;
DELETE pi FROM product_image_tb pi
  JOIN product_variant_tb v ON v.variant_id = pi.variant_id
  JOIN product_tb p ON p.product_id = v.product_id
 WHERE p.product_code REGEXP '^(LAP|MON|PER|CBL|DSK|CHR|STG|PPR|WRT|CNS|FST|BRG|SFT|SRV)-[0-9]{3}$';
DELETE psc FROM product_supplier_code_tb psc
  JOIN product_variant_tb v ON v.variant_id = psc.variant_id
  JOIN product_tb p ON p.product_id = v.product_id
 WHERE p.product_code REGEXP '^(LAP|MON|PER|CBL|DSK|CHR|STG|PPR|WRT|CNS|FST|BRG|SFT|SRV)-[0-9]{3}$';
DELETE b FROM product_barcode_tb b
  JOIN product_variant_tb v ON v.variant_id = b.variant_id
  JOIN product_tb p ON p.product_id = v.product_id
 WHERE p.product_code REGEXP '^(LAP|MON|PER|CBL|DSK|CHR|STG|PPR|WRT|CNS|FST|BRG|SFT|SRV)-[0-9]{3}$';
DELETE a FROM product_attribute_tb a
  JOIN product_variant_tb v ON v.variant_id = a.variant_id
  JOIN product_tb p ON p.product_id = v.product_id
 WHERE p.product_code REGEXP '^(LAP|MON|PER|CBL|DSK|CHR|STG|PPR|WRT|CNS|FST|BRG|SFT|SRV)-[0-9]{3}$';
DELETE pu FROM product_uom_tb pu
  JOIN product_tb p ON p.product_id = pu.product_id
 WHERE p.product_code REGEXP '^(LAP|MON|PER|CBL|DSK|CHR|STG|PPR|WRT|CNS|FST|BRG|SFT|SRV)-[0-9]{3}$';
DELETE pc FROM product_category_rel_tb pc
  JOIN product_tb p ON p.product_id = pc.product_id
 WHERE p.product_code REGEXP '^(LAP|MON|PER|CBL|DSK|CHR|STG|PPR|WRT|CNS|FST|BRG|SFT|SRV)-[0-9]{3}$';
DELETE v FROM product_variant_tb v
  JOIN product_tb p ON p.product_id = v.product_id
 WHERE p.product_code REGEXP '^(LAP|MON|PER|CBL|DSK|CHR|STG|PPR|WRT|CNS|FST|BRG|SFT|SRV)-[0-9]{3}$';
DELETE FROM product_tb
 WHERE product_code REGEXP '^(LAP|MON|PER|CBL|DSK|CHR|STG|PPR|WRT|CNS|FST|BRG|SFT|SRV)-[0-9]{3}$';
DELETE FROM appendix_tb WHERE storage_key LIKE 'seed-img-%';
DELETE FROM attribute_def_tb
 WHERE attribute_code IN ('COLOR','SIZE','CONFIG','SWITCH','MATERIAL','WARRANTY_MONTHS','SCREEN_SIZE','RECYCLABLE');
DELETE FROM supplier_tb WHERE supplier_code LIKE 'SUP-0%';
DELETE FROM category_tb WHERE parent_id IS NOT NULL AND cate_abbr IN
 ('LAP','MON','PER','CBL','DSK','CHR','STG','PPR','WRT','CNS','FST','BRG','SFT','INS','SUP');
DELETE FROM category_tb WHERE parent_id IS NULL AND cate_abbr IN ('ELEC','FURN','OFFC','INDP','SERV');
COMMIT;
*/
