# SYSTEM DATABASE DESIGN

## Product Module

> **Module:** Product (Item Master Data)
> **Target Release:** v1.1.0
> **Schema version:** 2 (revised)
> **Engine:** MySQL 8.0, InnoDB, `utf8mb4` / `utf8mb4_0900_ai_ci`
> **Migration tool:** Flyway (`V1__product_module.sql`)

---

## 0. Design Decisions

These decisions shape the whole schema. They are recorded here so that no rule
is left implicit.

### D1 — Every product owns at least one variant

A product **always** has at least one row in `product_variant_tb`. For a
non-configurable product the service layer creates exactly one variant with
`is_default = TRUE`, invisible in the UI.

**Why:** everything that is physically identifiable — SKU, barcode, weight,
image, stock, purchase price — belongs to the concrete sellable thing, not to
the abstract product. If simple products had no variant row, every one of those
tables would need a nullable `product_id` *and* a nullable `variant_id`, and
every query in Inventory, Purchasing, and Sales would need
`COALESCE(variant_id, product_id)` branching. With this rule, downstream modules
reference exactly one column: `variant_id`.

*Alternative considered:* allow child tables to hang off either `product_id` or
`variant_id`. Rejected because a nullable-FK-pair pattern cannot be enforced by
the database and pushes the burden into every service method.

### D2 — Identifier namespaces

| Level | Column | Meaning |
|---|---|---|
| Product | `product_tb.product_code` | The generic/parent item number. Unique. |
| Variant | `product_variant_tb.sku` | The sellable SKU. Unique. **This is the SKU referenced by FR-P-02 and by all downstream modules.** |

For a non-configurable product the service copies `product_code` into the
default variant's `sku`, so a simple product's SKU and product code are the same
string. A service-layer guard rejects a `product_code` that collides with any
existing `sku` and vice versa, keeping the two columns in one logical namespace.

### D3 — Primary keys are `BIGINT AUTO_INCREMENT`

All primary keys are surrogate `BIGINT` values.

**Why:** InnoDB clusters the table on its primary key, and every secondary index
stores a full copy of that key. A random `VARCHAR(36)` UUID causes page splits
on insert and inflates every index by roughly 4–5× compared with an 8-byte
integer. NFR-P-01 allows only 300 ms at 100k products.

Where an id must be exposed outside the system (file storage paths), a separate
opaque column is used — see `appendix_tb.storage_key`.

### D4 — Base UoM lives on the product, packaging conversions on the product

`base_uom_id` is on `product_tb`, not on the variant, because every variant of
one product shares the same stock-keeping unit (all shirt variants are counted
in `EA`). Alternative purchase/sale units and their conversion factors are also
product-level, in `product_uom_tb`.

Physical dimension conversions (g ↔ kg) come from `uom_tb.factor_to_base`.
Packaging conversions (1 BOX = 12 EA) come from `product_uom_tb`, because that
ratio is specific to the product and cannot be derived from the units alone.

### D5 — Prices default to the product, overridable per variant

`standard_cost` / `list_price` / `currency` are nullable on `product_tb`. The
same columns exist as nullable overrides on `product_variant_tb`. Resolution is
`COALESCE(variant.list_price, product.list_price)`. `NULL` means *not priced*,
which is deliberately different from `0`.

### D6 — Soft-delete convention

- Multi-state lifecycle → an `ENUM` column named `status`.
- Simple on/off → a `BOOLEAN` column named `active`, default `TRUE`.

No table in this module is ever hard-deleted by a background job. Physical
deletion happens only through the FR-P-05 path, when nothing references the row.

### D7 — Foreign keys are declared in the database

`ON DELETE RESTRICT` on every FK. This gives the referential-integrity guard of
FR-P-05 / FR-P-13 for free at the storage layer; the service layer catches the
constraint violation and translates it into `NoneDeleteableException` → HTTP-200
code `423`. Child rows that are conceptually owned by their parent
(`product_attribute_tb`, `product_image_tb`) use `ON DELETE CASCADE`.

---

## 1. Table Inventory

| # | Table | Purpose |
|---|---|---|
| 1 | `category_tb` | Hierarchical classification node |
| 2 | `product_tb` | Core item master record |
| 3 | `product_category_rel_tb` | Product ↔ category assignment (many-to-many) |
| 4 | `product_variant_tb` | Concrete sellable version of a product |
| 5 | `uom_tb` | Unit of measure definition |
| 6 | `product_uom_tb` | Product-specific alternative UoM + conversion factor |
| 7 | `attribute_def_tb` | Attribute master (controlled vocabulary) |
| 8 | `product_attribute_tb` | Attribute value on a variant |
| 9 | `product_barcode_tb` | Barcode of a variant |
| 10 | `appendix_tb` | Stored file reference |
| 11 | `product_image_tb` | Ordered image usage of a file by a variant |
| 12 | `supplier_tb` | Supplier master |
| 13 | `product_supplier_code_tb` | Supplier's own part number for a variant |
| 14 | `product_audit_log_tb` | Field-level change history |

### Standard audit columns

Every table except `product_audit_log_tb` carries these four columns. They are
omitted from the schema tables below to avoid repetition.

| FIELD | TYPE | KEY | NULLABLE | DEFAULT | DESCRIPTION |
|---|---|---|---|---|---|
| `created_by` | `BIGINT` | FK → `user_tb` | Not Null | | User who created the row |
| `create_time` | `DATETIME(6)` | | Not Null | `CURRENT_TIMESTAMP(6)` | Creation timestamp |
| `updated_by` | `BIGINT` | FK → `user_tb` | **Nullable** | `NULL` | User of the last update. `NULL` until first update |
| `update_time` | `DATETIME(6)` | | **Nullable** | `NULL ON UPDATE CURRENT_TIMESTAMP(6)` | Last update timestamp |

> `updated_by` is nullable because on `INSERT` nobody has updated the row yet.

---

## 2. `category_tb`

### Description

Stores the classification nodes used to categorise products. Nodes form a tree
of arbitrary depth through the self-referencing `parent_id`. A `NULL` `parent_id`
marks a root node.

### Schema design

| FIELD | TYPE | KEY | NULLABLE | DEFAULT | DESCRIPTION | INDEX |
|---|---|---|---|---|---|---|
| `cate_id` | `BIGINT` | Primary Key | Not Null | `AUTO_INCREMENT` | Category ID | |
| `cate_name` | `VARCHAR(50)` | Unique Key | Not Null | | Category name. Unique per parent, not globally — see note | Yes |
| `cate_abbr` | `VARCHAR(8)` | | Nullable | | Abbreviation used in compact UI columns | Yes |
| `description` | `VARCHAR(2000)` | | Nullable | | Category description | |
| `parent_id` | `BIGINT` | FK → `category_tb` | Nullable | `NULL` | Parent node. `NULL` = root | Yes |
| `path_cache` | `VARCHAR(512)` | | Nullable | | Materialised ancestor path, e.g. `/1/7/23/`. Maintained by the service | Yes |
| `depth` | `INT` | | Not Null | `0` | Distance from root. Root = 0 | |
| `active` | `BOOLEAN` | | Not Null | `TRUE` | Soft-delete flag | |

### Notes

- **Uniqueness is `(parent_id, cate_name)`, not `cate_name` alone.** "Accessories"
  is a legitimate child of both "Men" and "Women". A global unique key would
  reject the second one.
- **`path_cache` and `depth` are denormalised on purpose.** They turn FR-P-14's
  breadcrumb into a single lookup and FR-P-12's cycle check into a string test
  (`newParent.path_cache LIKE CONCAT(node.path_cache, '%')` means the new parent
  is a descendant → reject). Without them, both need a recursive walk on every
  save. They must be rebuilt for the whole subtree whenever a node is re-parented.

### Changes from the previous version

| Change | Reason |
|---|---|
| `cate_id` `LONG` → `BIGINT` | `LONG` is not a MySQL type |
| `cate_name` unique → unique on `(parent_id, cate_name)` | Same name may repeat under different parents |
| `description` `LONGTEXT` → `VARCHAR(2000)` | Matches the requirement; `LONGTEXT` is stored off-page and costs an extra read |
| Added `path_cache`, `depth` | Supports FR-P-12 and FR-P-14 without recursion |
| Added `active` | §9 needs a soft-delete for categories; the delete guard is `423`, not a hard delete |

---

## 3. `product_tb`

### Description

The core item master. Stores what is true about the item as a concept, across
all of its variants. Three types are supported:

- **`STOCKABLE`** — a physical item requiring warehouse space and stock counts.
- **`SERVICE`** — no warehouse space, no shipping, no stock count.
- **`CONSUMABLE`** — a physical item purchased and held, used internally by the
  business or bundled into a service, but not resold to customers and not
  tracked by exact individual counts.

### Schema design

| FIELD | TYPE | KEY | NULLABLE | DEFAULT | DESCRIPTION | INDEX |
|---|---|---|---|---|---|---|
| `product_id` | `BIGINT` | Primary Key | Not Null | `AUTO_INCREMENT` | Product ID | |
| `product_code` | `VARCHAR(64)` | Unique Key | Not Null | | Generic/parent item number. Immutable after creation. `[A-Za-z0-9._-]+` | Yes |
| `product_name` | `VARCHAR(150)` | | Not Null | | Display name of the product | Yes |
| `product_description` | `VARCHAR(2000)` | | Nullable | | Long description | |
| `type` | `ENUM` | | Not Null | | `STOCKABLE`, `SERVICE`, `CONSUMABLE` | Yes |
| `configurable` | `BOOLEAN` | | Not Null | `FALSE` | `TRUE` = owns user-visible variants | |
| `status` | `ENUM` | | Not Null | `DRAFT` | `DRAFT`, `ACTIVE`, `DISCONTINUED` | Yes |
| `base_uom_id` | `BIGINT` | FK → `uom_tb` | Not Null | | The unit stock and cost are expressed in | Yes |
| `standard_cost` | `DECIMAL(19,4)` | | **Nullable** | `NULL` | Standard cost. `≥ 0`. `NULL` = not set | |
| `list_price` | `DECIMAL(19,4)` | | **Nullable** | `NULL` | List/sale price. `≥ 0`. `NULL` = not set | |
| `currency` | `CHAR(3)` | | Nullable | `NULL` | ISO-4217 code. Required when any price is set | |
| `tax_category` | `VARCHAR(32)` | | **Nullable** | `NULL` | Label for the future Tax module. No calculation here | |
| `version` | `INT` | | Not Null | `0` | Optimistic-lock counter (`@Version`) | |

### Notes

- **`version` prevents silent overwrites.** Two catalogue editors saving the same
  product would otherwise let the second save discard the first. Hibernate
  increments this column and raises `OptimisticLockException` on a stale save,
  which the controller maps to a localised `400`.
- **Prices are nullable.** `0` means "this item is free"; `NULL` means "nobody
  has priced it yet". A `DRAFT` product defaulting to `0` could be sold for
  nothing by a downstream module.
- **`currency` is conditionally required.** The rule "if any price is set,
  `currency` must be valid ISO-4217" is a service-layer check, since MySQL
  `CHECK` constraints cannot look up a code list.

### Changes from the previous version

| Change | Reason |
|---|---|
| **Removed `variant_id`** | It formed a circular FK with `product_variant_tb.product_id`: neither row could be inserted first, and a single column contradicted the one-to-many relationship of FR-P-18 |
| **Added `product_name`** | FR-P-01 requires a name; FR-P-07 filters on it; `ProductListDTO` exposes it. It was absent |
| **Added `base_uom_id`** | FR-P-17 makes base UoM mandatory on the product. It existed only on the variant |
| `sku_name` → `product_code`, widened to 64 | §9 specifies ≤ 64 chars. Renamed because the sellable SKU now lives on the variant (see D2) |
| **Removed `supplier_id`** | FR-P-23 requires *zero or more* suppliers, each with their own part number. A single `NOT NULL` column also blocked creating a `DRAFT` product before sourcing, and made no sense for `SERVICE` items. Replaced by `product_supplier_code_tb` |
| Prices `NOT NULL DEFAULT 0` → nullable | §9 says null is allowed and means "not set". The old default also made the currency rule unsatisfiable: every price counted as "set", so every product without a currency violated it on insert |
| `tax_category` `NOT NULL` → nullable | The requirements list it as nullable and the Tax module does not exist |
| `BigDecimal(19,4)` → `DECIMAL(19,4)`, `String(3)` → `CHAR(3)`, `LONG` → `BIGINT`, `BOOL` → `BOOLEAN` | Java types replaced with SQL types, since this document drives the Flyway migration |
| `product_description` `LONGTEXT` → `VARCHAR(2000)` | Matches the requirement |
| Added `version` | Concurrent-edit protection |

---

## 4. `product_category_rel_tb`

### Description

Assigns products to categories. A product may sit in several categories at once
(for example both "Outerwear" and "Winter Sale"), so this is a many-to-many
relationship. Exactly one assignment per product is marked primary; that is the
one used for breadcrumbs and for reporting roll-ups.

### Schema design

| FIELD | TYPE | KEY | NULLABLE | DEFAULT | DESCRIPTION | INDEX |
|---|---|---|---|---|---|---|
| `id` | `BIGINT` | Primary Key | Not Null | `AUTO_INCREMENT` | Row ID | |
| `product_id` | `BIGINT` | FK → `product_tb` | Not Null | | Product | Yes |
| `cate_id` | `BIGINT` | FK → `category_tb` | Not Null | | Category | Yes |
| `is_primary` | `BOOLEAN` | | Not Null | `FALSE` | The category shown as the product's breadcrumb | |
| `active` | `BOOLEAN` | | Not Null | `TRUE` | Soft-delete flag | |

### Notes

- **Unique key `(product_id, cate_id)`** stops the same product being filed under
  the same category twice.
- **Exactly one primary per product** cannot be expressed as a MySQL constraint,
  so it is a service-layer rule: setting a new primary clears the previous one,
  in the same transaction. This mirrors the primary-image rule of §9.
- The table is renamed from `product_category_tb` because the requirements
  document uses that name for the *taxonomy tree*. Keeping both would guarantee
  confusion during code review.

### Changes from the previous version

| Change | Reason |
|---|---|
| `product_id` `VARCHAR(32)` → `BIGINT`; `cate_id` `VARCHAR(32)` → `BIGINT` | Both mismatched their parent columns. `cate_id` pointed at a `LONG` PK, so the FK could never be created; `product_id` was 32 chars against a 36-char UUID PK, which would silently truncate |
| Added `is_primary` | Multi-category assignment left "which category is *the* category" undefined for breadcrumbs and reporting |
| Added unique key `(product_id, cate_id)` | Prevents duplicate assignment |
| Renamed from `product_category_tb` | Name collision with the requirements' taxonomy table |

---

## 5. `product_variant_tb`

### Description

A concrete, sellable version of a product. Per decision **D1**, every product has
at least one row here; non-configurable products get a single row with
`is_default = TRUE`.

### Schema design

| FIELD | TYPE | KEY | NULLABLE | DEFAULT | DESCRIPTION | INDEX |
|---|---|---|---|---|---|---|
| `variant_id` | `BIGINT` | Primary Key | Not Null | `AUTO_INCREMENT` | Variant ID | |
| `product_id` | `BIGINT` | FK → `product_tb` | Not Null | | Parent product | Yes |
| `sku` | `VARCHAR(64)` | Unique Key | Not Null | | **The sellable SKU.** Immutable after creation | Yes |
| `variant_name` | `VARCHAR(64)` | | Not Null | | Display name, e.g. `Red / XL`. Unique **within** the product | Yes |
| `is_default` | `BOOLEAN` | | Not Null | `FALSE` | `TRUE` for the implicit variant of a simple product | |
| `description` | `VARCHAR(2000)` | | Nullable | | Variant-specific description | |
| `standard_cost` | `DECIMAL(19,4)` | | Nullable | `NULL` | Overrides the product cost when set | |
| `list_price` | `DECIMAL(19,4)` | | Nullable | `NULL` | Overrides the product price when set | |
| `currency` | `CHAR(3)` | | Nullable | `NULL` | Required when a variant price is set | |
| `net_weight` | `DECIMAL(19,4)` | | Nullable | `NULL` | Weight. `≥ 0` | |
| `weight_uom_id` | `BIGINT` | FK → `uom_tb` | Nullable | `NULL` | Unit of `net_weight`. Must be dimension `MASS` | |
| `length_mm` | `DECIMAL(12,3)` | | Nullable | `NULL` | Packed length in millimetres | |
| `width_mm` | `DECIMAL(12,3)` | | Nullable | `NULL` | Packed width in millimetres | |
| `height_mm` | `DECIMAL(12,3)` | | Nullable | `NULL` | Packed height in millimetres | |
| `active` | `BOOLEAN` | | Not Null | `TRUE` | Soft-delete flag | |
| `version` | `INT` | | Not Null | `0` | Optimistic-lock counter | |

### Notes

- **Colour and size are gone as columns.** They are attributes, stored in
  `product_attribute_tb` against `attribute_def_tb` entries. Keeping dedicated
  columns *and* a generic attribute table gives two places to record colour, and
  they will disagree.
- **Dimensions are three numbers, not one string.** The previous
  `size VARCHAR(16)` holding `"5cm*3cm*4cm"` cannot answer "find everything under
  10 cm tall" and cannot be used to compute shipping volume. Millimetres are
  fixed as the storage unit so no per-row unit column is needed.
- **`net_weight` is `DECIMAL`, not `DOUBLE`.** `DOUBLE` is binary floating point:
  `0.1 + 0.2 != 0.3`. Weights are summed across shipment lines and compared with
  limits, so the error accumulates. `DECIMAL(19,4)` maps to `BigDecimal` exactly.
- **The image reference moved out.** See `product_image_tb`.

### Changes from the previous version

| Change | Reason |
|---|---|
| `variant_id` `VARCHAR(36)` + `AUTO_INCREMENT` → `BIGINT AUTO_INCREMENT` | MySQL supports `AUTO_INCREMENT` only on integer columns, so the original definition was invalid |
| `variant_matnr` → `sku`; `VARCHAR(32)` → `VARCHAR(64)` | Plain naming, and §9 allows 64 chars |
| `variant_name` globally unique → unique on `(product_id, variant_name)` | `Red / XL` is a normal name for hundreds of different shirts; a global key would reject the second one |
| Removed `color`, `size` | Duplicated the generic attribute table |
| Added `length_mm`, `width_mm`, `height_mm` | Makes dimensions queryable and computable |
| `weight DOUBLE` → `net_weight DECIMAL(19,4)` + `weight_uom_id` | Floating point is wrong for measurements; a bare number is meaningless without its unit |
| Removed `img_appendix_id` | One image per variant, with no ordering, alt text, or primary flag, cannot satisfy FR-P-24 |
| Removed `standard_uom_id` | Base UoM belongs to the product (D4) |
| `size` / `weight` marked `FK` | They were not foreign keys; the marking was a copy-paste error |
| Added `is_default`, `version`; price overrides | D1, concurrency, D5 |

---

## 6. `uom_tb`

### Description

The catalogue of units of measure. Each unit belongs to a **dimension**, and
exactly one unit per dimension is flagged as that dimension's base. Conversion
between two units of the same dimension is
`value * from.factor_to_base / to.factor_to_base`.

### Schema design

| FIELD | TYPE | KEY | NULLABLE | DEFAULT | DESCRIPTION | INDEX |
|---|---|---|---|---|---|---|
| `uom_id` | `BIGINT` | Primary Key | Not Null | `AUTO_INCREMENT` | UoM ID | |
| `iso_code` | `VARCHAR(8)` | Unique Key | Not Null | | Unit code, e.g. `KGM`, `EA`, `BOX` | Yes |
| `unit_name` | `VARCHAR(32)` | Unique Key | Not Null | | Full name, e.g. `Kilogram` | Yes |
| `symbol` | `VARCHAR(8)` | | Nullable | | Display symbol, e.g. `kg` | |
| `dimension` | `ENUM` | | Not Null | | See the dimension list below | Yes |
| `is_base_for_dimension` | `BOOLEAN` | | Not Null | `FALSE` | Exactly one `TRUE` per dimension | |
| `factor_to_base` | `DECIMAL(24,10)` | | Not Null | `1` | Multiplier converting this unit to its dimension's base unit. Must be `> 0` |  |
| `description` | `VARCHAR(2000)` | | Nullable | | Description of the unit | |
| `active` | `BOOLEAN` | | Not Null | `TRUE` | Soft-delete flag. §9 requires the base UoM to reference an `ACTIVE` unit | |

### Dimensions

`MASS`, `LENGTH`, `VOLUME`, `AREA`, `TIME`, `TEMP`, `DENS`, `VELOC`, `ACCEL`,
`ENERGY`, `POWER`, `PRESS`, `COUNT`.

`COUNT` replaces the previous `NODMNSN` and covers `EA`, `PC`, `BOX`, `CT`, `PAL`.

### Notes

- **Why the factor must be stored.** The previous design said conversions
  *"will be calculated in Java automatically"*. To turn kilograms into grams, Java
  needs the number `1000`, and that number has to live somewhere. Hardcoding it
  in a `switch` means adding a unit requires a code change and a redeploy, which
  defeats having a `uom_tb` table at all.
- **`COUNT` units cannot be converted through `factor_to_base`.** One `BOX` of
  pens is 12 `EA`; one `BOX` of monitors is 2 `EA`. The ratio belongs to the
  product, not to the units. Therefore all `COUNT` units carry
  `factor_to_base = 1`, and conversion between them is resolved **only** through
  `product_uom_tb`. The conversion service must reject a `COUNT`-to-`COUNT`
  request that has no matching `product_uom_tb` row rather than silently
  returning a 1:1 result.
- **`TEMP` is not convertible by a multiplier** — Celsius to Fahrenheit needs an
  offset as well. Temperature units are stored for labelling only; the conversion
  service refuses `TEMP` inputs.

### Changes from the previous version

| Change | Reason |
|---|---|
| **Added `factor_to_base`, `is_base_for_dimension`** | FR-P-16 requires conversion factors; nothing stored them |
| **Added `symbol`** | FR-P-15 asks for code, name, **symbol**, dimension |
| `ISO_code ` → `iso_code` | Trailing space; mixed-case column names are inconsistent with the rest of the schema |
| `NODMNSN` → `COUNT` | Clearer, and the note above makes the non-convertibility explicit rather than implied |
| Added `active` | §9 requires the base UoM to be an `ACTIVE` unit; there was no way to express that |
| `description` `LONGTEXT` → `VARCHAR(2000)` | Consistency |

---

## 7. `product_uom_tb`

### Description

**New table.** Alternative units in which a product may be purchased or sold,
with the conversion factor back to the product's base UoM. This is where
FR-P-16's example "1 box = 12 each" is stored.

### Schema design

| FIELD | TYPE | KEY | NULLABLE | DEFAULT | DESCRIPTION | INDEX |
|---|---|---|---|---|---|---|
| `id` | `BIGINT` | Primary Key | Not Null | `AUTO_INCREMENT` | Row ID | |
| `product_id` | `BIGINT` | FK → `product_tb` | Not Null | | Product this factor applies to | Yes |
| `uom_id` | `BIGINT` | FK → `uom_tb` | Not Null | | The alternative unit | Yes |
| `factor_to_base_uom` | `DECIMAL(24,10)` | | Not Null | | How many base units make one of this unit. Must be `> 0` | |
| `usage_type` | `ENUM` | | Not Null | `BOTH` | `PURCHASE`, `SALE`, `BOTH` | |
| `active` | `BOOLEAN` | | Not Null | `TRUE` | Soft-delete flag | |

### Notes

- Unique key `(product_id, uom_id)` — one factor per product/unit pair.
- Example: a product with `base_uom_id = EA` and a row
  `(uom_id = BOX, factor_to_base_uom = 12)` satisfies acceptance criterion §13.4:
  `24 EA = 2 BOX`.
- `usage_type` supports FR-P-17's "purchase/sale UoMs". A pallet may be a valid
  purchase unit but never a sale unit.
- The row's `uom_id` must share the product's base-UoM dimension. Enforced in the
  service layer.

---

## 8. `attribute_def_tb`

### Description

**New table.** The controlled vocabulary of attribute names. Values are recorded
in `product_attribute_tb`, which points here instead of storing a free-text name.

### Schema design

| FIELD | TYPE | KEY | NULLABLE | DEFAULT | DESCRIPTION | INDEX |
|---|---|---|---|---|---|---|
| `attr_def_id` | `BIGINT` | Primary Key | Not Null | `AUTO_INCREMENT` | Attribute definition ID | |
| `attribute_code` | `VARCHAR(64)` | Unique Key | Not Null | | Stable code, e.g. `COLOUR` | Yes |
| `display_name` | `VARCHAR(128)` | | Not Null | | Label shown in the UI | |
| `value_type` | `ENUM` | | Not Null | `TEXT` | `TEXT`, `NUMBER`, `ENUM`, `BOOL` | |
| `uom_id` | `BIGINT` | FK → `uom_tb` | Nullable | `NULL` | Optional unit of the value, per FR-P-20 | |
| `is_variant_axis` | `BOOLEAN` | | Not Null | `FALSE` | `TRUE` if this attribute distinguishes variants of one product | |
| `active` | `BOOLEAN` | | Not Null | `TRUE` | Soft-delete flag | |

### Notes

- **Why a master table.** With a free-text `attribute_name`, users will enter
  `Colour`, `color`, `COLOR`, and `Color ` as four separate attributes. §12 says
  Reporting groups by attributes, so those four become four groups for one
  concept. A code list stops that at the source.
- **`is_variant_axis` is what makes FR-P-19 enforceable.** To check that variants
  of one product have unique attribute combinations, you compare only the axis
  attributes (colour, size) and ignore descriptive ones (material, country of
  origin). Without the flag, adding a descriptive attribute to one variant would
  wrongly make its combination "unique".
- **`uom_id` supplies FR-P-20's optional unit.** `Screen size = 15.6` is
  ambiguous; `Screen size = 15.6 IN` is not.

---

## 9. `product_attribute_tb`

### Description

The attribute values of a variant — the spec sheet. Since every product has at
least one variant (D1), a simple product's attributes hang off its default
variant, which satisfies FR-P-20's "standalone products and variants" with one
uniform shape.

### Schema design

| FIELD | TYPE | KEY | NULLABLE | DEFAULT | DESCRIPTION | INDEX |
|---|---|---|---|---|---|---|
| `id` | `BIGINT` | Primary Key | Not Null | `AUTO_INCREMENT` | Row ID | |
| `variant_id` | `BIGINT` | FK → `product_variant_tb` | Not Null | | Owning variant | Yes |
| `attr_def_id` | `BIGINT` | FK → `attribute_def_tb` | Not Null | | Which attribute | Yes |
| `attribute_value` | `VARCHAR(255)` | | Not Null | | The value, as text. Parsed per `value_type` | |
| `sort_order` | `INT` | | Not Null | `0` | Display order on the spec sheet | |
| `active` | `BOOLEAN` | | Not Null | `TRUE` | Soft-delete flag | |

### Notes

- Unique key `(variant_id, attr_def_id)` — one value per attribute per variant.
- `ON DELETE CASCADE` from `product_variant_tb`: attributes have no meaning
  without their variant.
- FR-P-19 is enforced in the service layer by building the set of
  `(attr_def_id → attribute_value)` pairs where `is_variant_axis = TRUE`, hashing
  it, and comparing against the product's other variants.

### Changes from the previous version

| Change | Reason |
|---|---|
| Table renamed from `prodcut_attribute_tb` | Spelling |
| `attribute_name VARCHAR(64)` → `attr_def_id` FK | Prevents the four-spellings-of-colour problem and enables FR-P-19 |
| Added `sort_order` | Spec sheets have a meaningful order |
| `status BOOL` → `active BOOLEAN` | `status` was a boolean named like an enum, inconsistent with `product_tb.status` |
| `attribute_value` `VARCHAR(64)` → `VARCHAR(255)` | 64 characters is short for a free-text specification |

---

## 10. `product_barcode_tb`

### Description

Barcodes identifying a variant. A variant may carry several — a EAN-13 for
Europe, a UPC-A for the United States, a QR code for internal logistics.

### Schema design

| FIELD | TYPE | KEY | NULLABLE | DEFAULT | DESCRIPTION | INDEX |
|---|---|---|---|---|---|---|
| `id` | `BIGINT` | Primary Key | Not Null | `AUTO_INCREMENT` | Barcode ID | |
| `variant_id` | `BIGINT` | FK → `product_variant_tb` | Not Null | | Owning variant | Yes |
| `barcode_value` | `VARCHAR(64)` | Unique Key | Not Null | | **The actual code**, e.g. `4006381333931` | Yes |
| `barcode_type` | `ENUM` | | Not Null | | `EAN13`, `EAN8`, `UPC_A`, `UPC_E`, `CODE128`, `QR` | |
| `region` | `ENUM` | | Nullable | `NULL` | Market the code is used in: `US`, `EU`, `CN`, `JP` | Yes |
| `is_primary` | `BOOLEAN` | | Not Null | `FALSE` | The code shown by default | |
| `active` | `BOOLEAN` | | Not Null | `TRUE` | Soft-delete flag | |

### Notes

- **The image is not stored.** A barcode graphic is a pure function of
  `barcode_value` + `barcode_type`, so it is rendered on demand in Java (ZXing or
  Barcode4J). Storing it duplicated data that would go stale the moment a value
  was corrected, and put several kilobytes of `LONGTEXT` in every row.
- `barcode_value` is validated against its `barcode_type` before insert —
  check-digit validation for EAN/UPC, length for CODE128.

### Changes from the previous version

| Change | Reason |
|---|---|
| **Added `barcode_value`** | The table stored a picture of a barcode but not the barcode. FR-P-22's uniqueness rule was unenforceable, and a warehouse scanner reading digits had nothing to look up |
| **Added `barcode_type`** | `used_for` recorded the *market*, not the *symbology*. Both are needed |
| **Removed `svg_img`, `img_appendix_id`** | Derivable from the value; see the note above |
| `used_for` → `region`, made nullable | Clearer name; not every code is region-specific |
| FK target `variant_tb` → `product_variant_tb` | The referenced table did not exist under that name |
| Table renamed from `bar_code_tb` | Matches the `product_*` prefix used elsewhere in the module |

---

## 11. `appendix_tb`

### Description

A reference to a stored file. This table records **where the file is**, never how
a product uses it — that separation lets one file serve several products.

### Schema design

| FIELD | TYPE | KEY | NULLABLE | DEFAULT | DESCRIPTION | INDEX |
|---|---|---|---|---|---|---|
| `appendix_id` | `BIGINT` | Primary Key | Not Null | `AUTO_INCREMENT` | File record ID | |
| `storage_key` | `VARCHAR(64)` | Unique Key | Not Null | | Opaque external identifier (UUID) used in the storage path | Yes |
| `path` | `VARCHAR(512)` | | Not Null | | Path or object key of the file | |
| `file_name` | `VARCHAR(128)` | | Not Null | | Original file name, including extension | Yes |
| `file_exten` | `VARCHAR(8)` | | Not Null | | Extension, lower-cased, without the dot | |
| `mime_type` | `VARCHAR(128)` | | Nullable | | e.g. `image/webp` | |
| `byte_size` | `BIGINT` | | Nullable | | File size in bytes | |
| `active` | `BOOLEAN` | | Not Null | `TRUE` | Soft-delete flag | |

### Changes from the previous version

| Change | Reason |
|---|---|
| PK `VARCHAR(36)` → `BIGINT` + `storage_key` | Consistent PK strategy (D3) while keeping an opaque id for URLs and storage paths |
| `file_name` unique → **not unique** | Two different products can legitimately both upload `front.jpg`. Uniqueness belongs to `storage_key` |
| `path` `VARCHAR(256)` → `VARCHAR(512)` | Object-storage keys with date-partitioned prefixes exceed 256 characters |
| Added `mime_type`, `byte_size` | Needed to serve the file with correct headers and to enforce upload limits |

---

## 12. `product_image_tb`

### Description

**New table.** How a variant uses a file: in what order, with what alt text, and
whether it is the primary image.

### Schema design

| FIELD | TYPE | KEY | NULLABLE | DEFAULT | DESCRIPTION | INDEX |
|---|---|---|---|---|---|---|
| `id` | `BIGINT` | Primary Key | Not Null | `AUTO_INCREMENT` | Row ID | |
| `variant_id` | `BIGINT` | FK → `product_variant_tb` | Not Null | | Owning variant | Yes |
| `appendix_id` | `BIGINT` | FK → `appendix_tb` | Not Null | | The file | Yes |
| `alt_text` | `VARCHAR(255)` | | Nullable | | Accessibility text | |
| `sort_order` | `INT` | | Not Null | `0` | Gallery order | |
| `is_primary` | `BOOLEAN` | | Not Null | `FALSE` | At most one `TRUE` per variant | |

### Notes

- Unique key `(variant_id, appendix_id)` — the same file is not attached twice.
- "At most one primary per variant" is a service-layer rule per §9: setting a new
  primary clears the old one inside the same transaction.
- `ON DELETE CASCADE` from `product_variant_tb`; `ON DELETE RESTRICT` from
  `appendix_tb`, so a file still in use cannot be removed.

---

## 13. `supplier_tb`

### Description

Supplier master data. Note that §15 of the requirements places supplier master
data **out of scope** for v1.1, bridged by a free-text reference until the
Purchasing module exists. This table is kept because it is already well formed,
but it should be treated as a **provisional** table that Purchasing may take
ownership of and reshape.

### Schema design

| FIELD | TYPE | KEY | NULLABLE | DEFAULT | DESCRIPTION | INDEX |
|---|---|---|---|---|---|---|
| `supplier_id` | `BIGINT` | Primary Key | Not Null | `AUTO_INCREMENT` | Supplier ID | |
| `supplier_code` | `VARCHAR(20)` | Unique Key | Not Null | | Human-readable ID, e.g. `SUP-10024` | Yes |
| `legal_name` | `VARCHAR(256)` | Unique Key | Not Null | | Full legal entity name used on tax forms and payments | Yes |
| `trade_name` | `VARCHAR(255)` | | Nullable | | "Doing Business As" name shown to purchasing agents | Yes |
| `status` | `ENUM` | | Not Null | `ACTIVE` | `ACTIVE`, `HOLD`, `INACTIVE` — see below | Yes |
| `tax_id` | `VARCHAR(50)` | Unique Key | **Nullable** | `NULL` | EIN, VAT, or local tax registration number | Yes |

### Status semantics

| Value | Meaning |
|---|---|
| `ACTIVE` | All functions available |
| `HOLD` | No **new** purchase orders. Warehouse staff may still receive inventory against POs already in transit, and the record stays fully visible for reporting |
| `INACTIVE` | Soft delete. Hidden from search dropdowns, no new POs, no receiving, no payments |

### Changes from the previous version

| Change | Reason |
|---|---|
| `status` given a `NOT NULL DEFAULT 'ACTIVE'` | It was nullable, leaving the most important business rule of the table undefined for new rows |
| `tax_id` `NOT NULL` → nullable | A supplier can be onboarded before their tax registration is verified. A `NOT NULL UNIQUE` column also breaks on the second supplier if anyone inserts an empty string as a placeholder |
| Column names given backticks/consistent formatting | `legal_name` and `trade_name` were the only unquoted names in the document |

---

## 14. `product_supplier_code_tb`

### Description

**New table.** The supplier's own part number for a variant. When a purchase
order is printed, it must carry the *supplier's* code, not ours.

### Schema design

| FIELD | TYPE | KEY | NULLABLE | DEFAULT | DESCRIPTION | INDEX |
|---|---|---|---|---|---|---|
| `id` | `BIGINT` | Primary Key | Not Null | `AUTO_INCREMENT` | Row ID | |
| `variant_id` | `BIGINT` | FK → `product_variant_tb` | Not Null | | The item being sourced | Yes |
| `supplier_id` | `BIGINT` | FK → `supplier_tb` | Not Null | | The supplier | Yes |
| `supplier_part_no` | `VARCHAR(64)` | | Not Null | | The supplier's own code for this item | Yes |
| `is_preferred` | `BOOLEAN` | | Not Null | `FALSE` | Default source of supply | |
| `last_purchase_cost` | `DECIMAL(19,4)` | | Nullable | `NULL` | Most recent price paid. Informational in v1.1 | |
| `lead_time_days` | `INT` | | Nullable | `NULL` | Quoted lead time | |
| `active` | `BOOLEAN` | | Not Null | `TRUE` | Soft-delete flag | |

### Notes

- Unique key `(supplier_id, supplier_part_no)` — a supplier's own code identifies
  exactly one item in their catalogue.
- Unique key `(variant_id, supplier_id)` — one sourcing record per pair.
- "At most one preferred per variant" is a service-layer rule.

---

## 15. `product_audit_log_tb`

### Description

Field-level change history for the module. One row per changed column; rows
belonging to the same user action share a `change_group_id`.

### Schema design

| FIELD | TYPE | KEY | NULLABLE | DEFAULT | DESCRIPTION | INDEX |
|---|---|---|---|---|---|---|
| `id` | `BIGINT` | Primary Key | Not Null | `AUTO_INCREMENT` | Row ID | |
| `change_group_id` | `CHAR(36)` | | Not Null | | UUID shared by every row written in one transaction | Yes |
| `operation` | `ENUM` | | Not Null | | `CREATE`, `UPDATE`, `STATUS_CHANGE`, `DELETE` | Yes |
| `affected_table` | `VARCHAR(64)` | | Not Null | | Name of the changed table | Yes |
| `affected_row` | `VARCHAR(64)` | | Not Null | | Primary key of the changed row, as text | Yes |
| `affected_column` | `VARCHAR(64)` | | Not Null | | Name of the changed column | |
| `before_val` | `TEXT` | | **Nullable** | `NULL` | Value before the change. `NULL` on `CREATE` | |
| `after_val` | `TEXT` | | **Nullable** | `NULL` | Value after the change. `NULL` on `DELETE` | |
| `edited_by` | `BIGINT` | | Not Null | | User who made the change | Yes |
| `edit_time` | `DATETIME(6)` | | Not Null | `CURRENT_TIMESTAMP(6)` | When the change was made | Yes |

### Notes

- **This table carries no standard audit columns.** It *is* the audit record; a
  `created_by` on an audit row would be redundant with `edited_by`.
- **`change_group_id` is what makes the log readable.** Without it, a user
  changing name, price, and status in one save produces three rows with three
  near-identical timestamps and no proof they were one action. FR-P-27 asks for
  "an audit record for every create/update" — the group id is that record; the
  individual rows are its diff.
- **This table is append-only.** No `UPDATE` or `DELETE` is ever issued against
  it. Enforce with a database grant if the deployment allows it.
- Per NFR-P-04, audit writes are log-and-continue: a failure in diff computation
  must not fail the user's transaction.
- `TEXT` (64 KB) rather than `LONGTEXT` (4 GB) — no single column value in this
  module approaches 64 KB.

### Changes from the previous version

| Change | Reason |
|---|---|
| Table renamed from `product_audit_log_db` | The suffix convention is `_tb` |
| **Added `change_group_id`** | One edit spanning several columns could not be reassembled |
| **Added `operation`** | FR-P-27 distinguishes create / update / status-change / delete |
| `before_val`, `after_val` `NOT NULL` → nullable | A `CREATE` has no previous value and a `DELETE` has no new value, so inserts would have failed |
| `LONGTEXT` → `TEXT` | 4 GB per value is far beyond what any column here holds |
| Added indexes on `(affected_table, affected_row)` and `change_group_id` | "Show me the history of this product" was a full table scan |

---

## 16. Index Plan

NFR-P-01 allows 300 ms for a filtered product list over 100k rows.

| Table | Index | Serves |
|---|---|---|
| `product_tb` | `UNIQUE (product_code)` | FR-P-02 |
| `product_tb` | `(status, type)` | Default list filter |
| `product_tb` | `(product_name(50))` | Name search |
| `product_tb` | `(base_uom_id)` | UoM delete guard |
| `product_variant_tb` | `UNIQUE (sku)` | FR-P-02, scanner lookup |
| `product_variant_tb` | `UNIQUE (product_id, variant_name)` | Naming rule |
| `product_variant_tb` | `(product_id, active)` | Detail aggregate |
| `product_category_rel_tb` | `(cate_id, product_id)` | "Products in this category" + FR-P-13 guard |
| `product_category_rel_tb` | `(product_id, cate_id)` | "Categories of this product" |
| `category_tb` | `(parent_id)` | Tree walk |
| `category_tb` | `(path_cache(255))` | Subtree query |
| `product_attribute_tb` | `UNIQUE (variant_id, attr_def_id)` | Spec sheet |
| `product_barcode_tb` | `UNIQUE (barcode_value)` | FR-P-22 |
| `product_uom_tb` | `UNIQUE (product_id, uom_id)` | Conversion lookup |
| `product_supplier_code_tb` | `UNIQUE (supplier_id, supplier_part_no)` | PO printing |
| `product_audit_log_tb` | `(affected_table, affected_row)`, `(change_group_id)` | History view |

### Two points on index behaviour

1. **Composite column order matters.** `(status, type)` answers queries filtering
   on `status` alone, or on `status` **and** `type`, but not on `type` alone.
   MySQL reads a composite index left to right, so put the column that appears in
   the most queries first.
2. **Case-insensitive SKU uniqueness (§9) comes from the collation.** MySQL 8's
   default `utf8mb4_0900_ai_ci` is case-insensitive, so `UNIQUE (sku)` already
   rejects `abc-1` against `ABC-1`. This is pinned explicitly on the column in
   the migration, because switching to a `_bin` collation — or moving to
   PostgreSQL, which is case-sensitive by default — would silently break the rule.

---

## 17. Requirement Traceability

| FR | Requirement | Where it is satisfied |
|---|---|---|
| FR-P-01 | Create with unique SKU, name, category, base UoM | `product_tb.product_code`, `product_name`, `base_uom_id`; `product_category_rel_tb` |
| FR-P-02 | SKU uniqueness | `UNIQUE (product_variant_tb.sku)` + D2 guard |
| FR-P-03 | SKU immutable | Service-layer rule |
| FR-P-04 | Soft-retire | `product_tb.status = DISCONTINUED` |
| FR-P-05 | Physical delete when unreferenced | FK `ON DELETE RESTRICT` (D7) |
| FR-P-06 | Restore | `status` transition |
| FR-P-07 | Paginated filterable list | §16 index plan |
| FR-P-08 | Detail aggregate | Joins across variant / attribute / barcode / image |
| FR-P-09 | Lifecycle status | `product_tb.status` |
| FR-P-10 | Created/updated by and at | Standard audit columns |
| FR-P-11 | Category tree | `category_tb.parent_id` |
| FR-P-12 | No cycles | `path_cache` prefix test |
| FR-P-13 | Refuse category delete when in use | FK `ON DELETE RESTRICT` |
| FR-P-14 | Subtree and breadcrumb | `path_cache`, `depth` |
| FR-P-15 | UoM catalogue | `uom_tb` incl. `symbol` |
| FR-P-16 | Conversion factors | `uom_tb.factor_to_base` + `product_uom_tb` |
| FR-P-17 | Base UoM + purchase/sale UoMs | `product_tb.base_uom_id`, `product_uom_tb.usage_type` |
| FR-P-18 | Configurable products own variants | `product_tb.configurable`, `product_variant_tb` |
| FR-P-19 | Unique attribute combination | `attribute_def_tb.is_variant_axis` + service check |
| FR-P-20 | Attributes with optional unit | `product_attribute_tb`, `attribute_def_tb.uom_id` |
| FR-P-21 | Cost, price, currency, scale 4 | `DECIMAL(19,4)`, `CHAR(3)` |
| FR-P-22 | Unique barcodes | `UNIQUE (product_barcode_tb.barcode_value)` |
| FR-P-23 | Zero or more supplier codes | `product_supplier_code_tb` |
| FR-P-24 | Ordered images, alt text, one primary | `product_image_tb` |
| FR-P-25/26 | CSV import/export | No schema needed; see the note below |
| FR-P-27 | Field-level audit | `product_audit_log_tb` |

### Note on import (F9)

FR-P-25 needs no table for correctness, but NFR-P-06 targets 50k-row files. If
you want progress reporting or a re-runnable report after the HTTP response has
closed, add `product_import_job_tb` (job id, file name, status, counts) and
`product_import_row_tb` (job id, row number, outcome, message) in **P4**. They
are deliberately left out of the v1.1 baseline to keep P1 small.

---

## 18. Open Points

1. **D1 needs your confirmation.** Everything above assumes every product owns at
   least one variant row. If you prefer simple products to have no variant, tell
   me and I will rework tables 5, 8, 9, 10, 12, and 14 to hang off either
   `product_id` or `variant_id`.
2. **`supplier_tb` is scope beyond §15.** Keeping it is fine, but Purchasing will
   likely want to own it. Agree now who owns the table so the Flyway history does
   not fight later.
3. **Currency is per product.** If the ERP will be multi-company with different
   book currencies, price should eventually move to a `product_price_tb` keyed by
   `(product_id, currency, price_list_id)`. §15 defers this; the current shape
   does not block it.
