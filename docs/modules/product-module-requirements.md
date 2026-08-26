# Product Module — Function & Feature Requirements

> **Module:** Product (Product / Item Master Data)
> **Status:** Requirements — Not yet implemented
> **Author:** Genesis Young
> **Last Updated:** August 26, 2026
> **Target Release:** v1.1.0 (first ERP business module)
> **Depends on:** Authentication & RBAC framework (implemented), `BasicResponse`/`BasicPageResponse` envelopes, i18n (EN/CN)

---

## Table of Contents

1. [Purpose & Scope](#1-purpose--scope)
2. [Glossary](#2-glossary)
3. [Actors & Roles](#3-actors--roles)
4. [Functional Requirements](#4-functional-requirements)
5. [Feature Breakdown](#5-feature-breakdown)
6. [Data Model](#6-data-model)
7. [API Specification](#7-api-specification)
8. [RBAC — Permissions & Actions](#8-rbac--permissions--actions)
9. [Validation & Business Rules](#9-validation--business-rules)
10. [Non-Functional Requirements](#10-non-functional-requirements)
11. [Internationalisation](#11-internationalisation)
12. [Integration Points](#12-integration-points)
13. [Acceptance Criteria](#13-acceptance-criteria)
14. [Delivery Plan](#14-delivery-plan)
15. [Out of Scope](#15-out-of-scope)

---

## 1. Purpose & Scope

The **Product module** is the master-data foundation for every downstream ERP
business flow. Inventory, purchasing, sales, and manufacturing all reference a
single, authoritative product record. Without it, no stock movement, purchase
order, or invoice line can be created. This module is therefore the first ERP
business module to build on top of the existing auth/RBAC framework.

### In Scope

- Full lifecycle management of **products (items)**: create, read, update,
  soft-retire, restore.
- **Product categories** (hierarchical tree) for classification and reporting.
- **Units of measure (UoM)** and inter-unit conversion.
- **Product attributes / specifications** (extensible key–value spec sheet).
- **Product variants** (e.g. size/colour) derived from a parent product.
- **Barcodes / SKUs** and supplier product codes.
- **Pricing** master data (standard cost, list/sale price) — a single price per
  product for v1.1; tiered/contract pricing is deferred.
- **Media** (product images) reference management.
- **Bulk import/export** (CSV) for catalogue onboarding.
- **Audit trail** of every product mutation.

### Explicitly Not In Scope (this module)

- Real-time stock levels and movements (belongs to the future Inventory module;
  the Product module only owns the item definition, not its quantity on hand).
- Purchase/sales transactions.
- Tax engine and multi-currency conversion logic (a `currency` code and a `taxCategory`
  are stored, but no conversion/calculation is performed here).

---

## 2. Glossary

| Term | Definition |
|---|---|
| **Product / Item** | An orderable, stockable, or sellable good or service tracked by the ERP. |
| **SKU** | Stock Keeping Unit — the unique internal identifier of a product or variant. |
| **UoM** | Unit of Measure (each, box, kg, litre…). |
| **Base UoM** | The canonical unit a product's stock and cost are expressed in. |
| **Category** | A node in the hierarchical classification tree a product belongs to. |
| **Variant** | A concrete sellable version of a parent (configurable) product. |
| **Attribute** | A named specification (e.g. "Colour = Red") attached to a product. |
| **Soft-retire** | Marking a product inactive/discontinued without physical deletion. |

---

## 3. Actors & Roles

The module reuses the existing RBAC model (`Role`, `Permission`, `Action`). No
new authentication mechanism is introduced. Suggested functional roles:

| Actor | Responsibility |
|---|---|
| **Catalogue Manager** | Full CRUD over products, categories, UoM; approves publish. |
| **Product Editor** | Creates/edits products and variants; cannot delete or manage taxonomies. |
| **Purchasing Clerk** | Read products; maintain supplier codes and standard cost. |
| **Sales Clerk** | Read products; maintain list/sale price (if granted). |
| **Viewer / Reporting** | Read-only access to the catalogue. |

Exact role→permission grants are configured at runtime through the existing
`/api/permission/manipulate` endpoints; this document only defines the
**permissions and actions** the module must register (see §8).

---

## 4. Functional Requirements

Each requirement is uniquely identified (`FR-P-nn`) and testable.

### 4.1 Product Master

| ID | Requirement |
|---|---|
| FR-P-01 | The system shall allow an authorised user to **create** a product with, at minimum, a unique SKU, a name, a category, and a base UoM. |
| FR-P-02 | The system shall enforce **SKU uniqueness** across all active and retired products; a duplicate SKU is rejected with a localised error. |
| FR-P-03 | The system shall allow **updating** any editable field of an existing product; the SKU is immutable once assigned. |
| FR-P-04 | The system shall support **soft-retire** (status → `DISCONTINUED`) instead of physical deletion when a product is referenced by any other record. |
| FR-P-05 | The system shall allow **physical deletion** only when the product has never been referenced by another module and has no stock history. Otherwise the delete is refused with an HTTP-200 envelope carrying code `423` (matching the department-deletion convention). |
| FR-P-06 | The system shall allow **restoring** a soft-retired product back to `ACTIVE`. |
| FR-P-07 | The system shall expose a **paginated, filterable, sortable list** of products (by SKU, name, category, status, price range, created date). |
| FR-P-08 | The system shall expose a **single-product detail** view including category path, UoM, variants, attributes, pricing, and media references. |
| FR-P-09 | Each product shall carry a **lifecycle status**: `DRAFT`, `ACTIVE`, `DISCONTINUED`. Only `ACTIVE` products are selectable by downstream modules. |
| FR-P-10 | The system shall record **created-by / created-at / updated-by / updated-at** for every product automatically. |

### 4.2 Categories (Taxonomy)

| ID | Requirement |
|---|---|
| FR-P-11 | The system shall allow creating a **category tree** with an optional `parentId`, supporting arbitrary depth. |
| FR-P-12 | The system shall prevent **cyclic** parent references and prevent a category from being its own ancestor. |
| FR-P-13 | The system shall refuse deletion of a category that has **child categories** or **assigned products** (code `423`). |
| FR-P-14 | The system shall return the **full category subtree** for a given node, and the **breadcrumb path** for any category. |

### 4.3 Units of Measure

| ID | Requirement |
|---|---|
| FR-P-15 | The system shall maintain a catalogue of **units of measure** (code, name, symbol, dimension: count/weight/volume/length). |
| FR-P-16 | The system shall support **conversion factors** between two UoMs of the same dimension (e.g. 1 box = 12 each). |
| FR-P-17 | A product's **base UoM** is mandatory; additional purchase/sale UoMs may be attached with their conversion factor to the base UoM. |

### 4.4 Variants & Attributes

| ID | Requirement |
|---|---|
| FR-P-18 | The system shall allow a product to be flagged **configurable**, owning one or more **variants**, each with its own SKU and attribute combination. |
| FR-P-19 | The system shall enforce **unique attribute combinations** among variants of the same parent. |
| FR-P-20 | The system shall support arbitrary **attributes** (name + value + optional unit) on both standalone products and variants. |

### 4.5 Pricing & Codes

| ID | Requirement |
|---|---|
| FR-P-21 | The system shall store a **standard cost**, **list price**, and **currency code** per product (non-negative, scale 4). |
| FR-P-22 | The system shall store zero or more **barcodes** (EAN/UPC/QR) per product/variant, each unique. |
| FR-P-23 | The system shall store zero or more **supplier product codes** (supplier ref + supplier's own code) — supplier entity is a forward reference and may be a free-text field until the Purchasing module exists. |

### 4.6 Media, Import/Export & Audit

| ID | Requirement |
|---|---|
| FR-P-24 | The system shall store ordered **image references** (URI + alt text + primary flag) per product; exactly one image may be primary. |
| FR-P-25 | The system shall support **CSV bulk import** of products with per-row validation, returning a report of accepted/rejected rows (rejected rows never partially commit). |
| FR-P-26 | The system shall support **CSV export** of the current filtered product list. |
| FR-P-27 | The system shall write an **audit record** for every create/update/status-change/delete, capturing actor, timestamp, and a field-level diff. |

---

## 5. Feature Breakdown

| # | Feature | Description | Priority | FRs |
|---|---|---|---|---|
| F1 | Product CRUD | Create/read/update/retire/restore products | Must | FR-P-01..10 |
| F2 | Product list & search | Paginated, multi-criteria filtered, sortable grid | Must | FR-P-07 |
| F3 | Category tree | Hierarchical taxonomy management | Must | FR-P-11..14 |
| F4 | Units of measure | UoM catalogue + conversions | Must | FR-P-15..17 |
| F5 | Variants | Configurable products & variant matrix | Should | FR-P-18..19 |
| F6 | Attributes | Extensible spec sheet | Should | FR-P-20 |
| F7 | Pricing & barcodes | Cost/price/currency, barcodes, supplier codes | Must | FR-P-21..23 |
| F8 | Media | Image reference management | Could | FR-P-24 |
| F9 | Bulk import/export | CSV onboarding & extract | Should | FR-P-25..26 |
| F10 | Audit trail | Field-level change history | Should | FR-P-27 |

Priority uses MoSCoW. **Must** features constitute the v1.1.0 MVP; **Should/Could**
may slip to v1.1.x.

---

## 6. Data Model

New entities live under `entity/product/`, repositories under
`repository/product/`, following the `entity/<module>/` convention. Tables use
the `*_tb` suffix and Lombok `@Getter/@Setter/@Builder/@NoArgsConstructor/@AllArgsConstructor`
as elsewhere in the codebase.

### 6.1 Entity Overview

| Entity | Table | Purpose |
|---|---|---|
| `Product` | `product_tb` | Core item master record |
| `ProductCategory` | `product_category_tb` | Hierarchical classification node |
| `UnitOfMeasure` | `uom_tb` | Unit definition |
| `ProductUom` | `product_uom_tb` | Join: product ↔ alternative UoM + conversion factor |
| `ProductVariant` | `product_variant_tb` | Sellable variant of a configurable product |
| `ProductAttribute` | `product_attribute_tb` | Key–value spec on a product or variant |
| `ProductBarcode` | `product_barcode_tb` | Barcode (EAN/UPC/QR) for a product/variant |
| `ProductSupplierCode` | `product_supplier_code_tb` | Supplier's own code for a product |
| `ProductImage` | `product_image_tb` | Image reference (URI, alt, primary) |
| `ProductAuditLog` | `product_audit_log_tb` | Field-level change history |

### 6.2 `Product` (core fields)

| Field | Type | Constraints | Notes |
|---|---|---|---|
| `id` | `Long` | PK, identity | |
| `sku` | `String(64)` | not null, unique, immutable | Business key |
| `name` | `String(150)` | not null | i18n handled at presentation layer |
| `description` | `String(2000)` | nullable | |
| `categoryId` | `Long` | not null, FK → `product_category_tb` | |
| `baseUomId` | `Long` | not null, FK → `uom_tb` | |
| `type` | `enum` | not null | `STOCKABLE`, `SERVICE`, `CONSUMABLE` |
| `configurable` | `Boolean` | default false | Owns variants when true |
| `status` | `enum` | not null, default `DRAFT` | `DRAFT`, `ACTIVE`, `DISCONTINUED` |
| `standardCost` | `BigDecimal(19,4)` | ≥ 0, nullable | |
| `listPrice` | `BigDecimal(19,4)` | ≥ 0, nullable | |
| `currency` | `String(3)` | ISO-4217, nullable | No conversion performed |
| `taxCategory` | `String(32)` | nullable | Forward ref to future Tax module |
| `weight` | `BigDecimal(19,4)` | ≥ 0, nullable | In base weight unit |
| `createdBy` | `Long` | not null | User id |
| `createDate` | `LocalDateTime` | not null | |
| `updatedBy` | `Long` | nullable | |
| `updateDate` | `LocalDateTime` | nullable | |

> **Design note:** consistent with the codebase's current approach, foreign keys
> are stored as scalar id columns (e.g. `categoryId`) rather than JPA
> `@ManyToOne` graphs, keeping DTO mapping explicit and avoiding lazy-loading
> surprises across the service boundary.

### 6.3 Relationships

```
ProductCategory ──(parentId, self-ref tree)
      │ 1
      │
      │ *
   Product ──1─── baseUom ──> UnitOfMeasure
      │ 1                          ▲
      ├──* ProductUom ─────────────┘ (alt UoM + factor)
      ├──* ProductVariant ──* ProductAttribute
      ├──* ProductAttribute
      ├──* ProductBarcode
      ├──* ProductSupplierCode
      ├──* ProductImage   (exactly one primary)
      └──* ProductAuditLog
```

### 6.4 DTOs

Following the "never expose raw entities" convention, each list/detail view has a
dedicated DTO under `dto/product/`:

- `ProductListDTO` — id, sku, name, categoryName, status, listPrice, currency (grid row).
- `ProductDetailDTO` — full aggregate incl. nested category path, UoM, variants, attributes, barcodes, images.
- `ProductSaveDTO` — create/update request body (validated).
- `ProductCategoryDTO`, `UnitOfMeasureDTO`, `ProductVariantDTO`, `ProductAttributeDTO`, `ProductImportRowResultDTO`.

---

## 7. API Specification

Base path `/api/product`. Admin/taxonomy management under `/api/admin/product/*`.
All responses use the existing envelopes (`BasicResponse`, `SimpleResponse`,
`BasicPageResponse<T,R>`); pagination uses `BasicPage`/`BasicPageRequest`.

### 7.1 Products

| Method | Endpoint | Description | Permission |
|---|---|---|---|
| `GET` | `/api/product/fetch` | Paginated, filtered product list (`page`, `size`, `sku`, `name`, `categoryId`, `status`, `minPrice`, `maxPrice`, sort) | `PRODUCT_VIEW` |
| `GET` | `/api/product/detail/{id}` | Full product detail aggregate | `PRODUCT_VIEW` |
| `POST` | `/api/product/save` | Create (no id) or update (with id) a product | `PRODUCT_CREATE` / `PRODUCT_EDIT` |
| `POST` | `/api/product/status` | Change lifecycle status (`{id, status}`) | `PRODUCT_PUBLISH` |
| `DELETE` | `/api/product/delete/{id}` | Physical delete when unreferenced; else `423` | `PRODUCT_DELETE` |
| `POST` | `/api/product/import` | CSV bulk import (multipart); returns row report | `PRODUCT_IMPORT` |
| `GET` | `/api/product/export` | CSV export of current filter | `PRODUCT_VIEW` |

### 7.2 Categories

| Method | Endpoint | Description | Permission |
|---|---|---|---|
| `GET` | `/api/admin/product/categories/tree` | Full category tree | `PRODUCT_CATEGORY_VIEW` |
| `GET` | `/api/admin/product/categories/{id}/path` | Breadcrumb path | `PRODUCT_CATEGORY_VIEW` |
| `POST` | `/api/admin/product/categories/save` | Create/update category | `PRODUCT_CATEGORY_MANAGE` |
| `DELETE` | `/api/admin/product/categories/delete/{id}` | Delete (refused if children/products; `423`) | `PRODUCT_CATEGORY_MANAGE` |

### 7.3 Units of Measure

| Method | Endpoint | Description | Permission |
|---|---|---|---|
| `GET` | `/api/admin/product/uom/fetch` | Paginated UoM list | `PRODUCT_UOM_VIEW` |
| `POST` | `/api/admin/product/uom/save` | Create/update UoM | `PRODUCT_UOM_MANAGE` |
| `DELETE` | `/api/admin/product/uom/delete/{id}` | Delete (refused if referenced; `423`) | `PRODUCT_UOM_MANAGE` |
| `POST` | `/api/admin/product/uom/conversion` | Define/update a conversion factor | `PRODUCT_UOM_MANAGE` |

### 7.4 Variants, Attributes, Barcodes, Media

| Method | Endpoint | Description | Permission |
|---|---|---|---|
| `GET` | `/api/product/{id}/variants` | List variants of a product | `PRODUCT_VIEW` |
| `POST` | `/api/product/{id}/variant` | Add/update a variant | `PRODUCT_EDIT` |
| `DELETE` | `/api/product/variant/{variantId}` | Remove a variant | `PRODUCT_EDIT` |
| `POST` | `/api/product/{id}/attribute` | Add/update an attribute | `PRODUCT_EDIT` |
| `DELETE` | `/api/product/attribute/{attrId}` | Remove an attribute | `PRODUCT_EDIT` |
| `POST` | `/api/product/{id}/barcode` | Add a barcode | `PRODUCT_EDIT` |
| `POST` | `/api/product/{id}/image` | Add/update an image reference | `PRODUCT_EDIT` |

> Sample success envelope:
> ```json
> { "status": 200, "message": "OK", "object": { /* payload */ } }
> ```
> Referential-integrity refusal (matches `DepartmentController`):
> ```json
> { "status": 423, "message": "product.delete.referenced" }
> ```

---

## 8. RBAC — Permissions & Actions

The module registers the following **permissions** through the existing
permission-registration flow (`/api/permission/manipulate/registration`), so they
appear in role-assignment screens with no framework changes:

| Permission code | Grants |
|---|---|
| `PRODUCT_VIEW` | List/detail/export products |
| `PRODUCT_CREATE` | Create new products |
| `PRODUCT_EDIT` | Update products, variants, attributes, barcodes, media |
| `PRODUCT_PUBLISH` | Change lifecycle status (DRAFT→ACTIVE, retire, restore) |
| `PRODUCT_DELETE` | Physically delete unreferenced products |
| `PRODUCT_IMPORT` | Bulk CSV import |
| `PRODUCT_CATEGORY_VIEW` / `PRODUCT_CATEGORY_MANAGE` | Read / manage taxonomy |
| `PRODUCT_UOM_VIEW` / `PRODUCT_UOM_MANAGE` | Read / manage units of measure |

Actions (fine-grained, via `ActionPermission`) mirror the CRUD verbs so the
existing action-permission machinery gates each endpoint. A new
`NavigationMenu` entry ("Products") is registered and made visible per user via
`UserNavMenu`, exactly like existing admin screens.

---

## 9. Validation & Business Rules

| Rule | Detail |
|---|---|
| SKU format | Non-blank, ≤ 64 chars, `[A-Za-z0-9._-]+`, trimmed, stored as-entered; uniqueness checked case-insensitively. |
| SKU immutability | Rejected if a save request changes the SKU of an existing id. |
| Price/cost | `BigDecimal`, scale 4, must be `≥ 0`; null allowed (treated as "not set"). |
| Currency | If any price is set, `currency` must be a valid ISO-4217 code. |
| Status transitions | `DRAFT → ACTIVE`, `ACTIVE ↔ DISCONTINUED` only; `DRAFT → DISCONTINUED` allowed; no direct `DISCONTINUED → DRAFT`. Invalid transitions return `400`. |
| Category cycle | On save, walk ancestors; reject if the new parent is the node itself or a descendant. |
| Variant uniqueness | The set of `(attributeName→value)` pairs must be unique among a parent's variants. |
| Base UoM required | Every product must reference an existing `ACTIVE` UoM as its base. |
| Conversion factor | Must be `> 0`; both UoMs must share the same `dimension`. |
| Primary image | At most one `primary = true` per product; setting a new primary clears the old. |
| Delete guard | Any product/category/UoM referenced elsewhere → `NoneDeleteableException` → HTTP-200 code `423` (never a hard `409`, to preserve the frontend refresh-retry contract). |
| Import atomicity | A rejected row is reported but never committed; valid rows in the same file still commit (row-level, not file-level, atomicity) unless `strict=true` is passed, in which case any rejection aborts the whole file. |

---

## 10. Non-Functional Requirements

| ID | Requirement |
|---|---|
| NFR-P-01 | **Performance:** product list (page size ≤ 50) returns in < 300 ms at 100k products with indexed filters (`sku`, `name`, `categoryId`, `status`). |
| NFR-P-02 | **Caching:** category tree and UoM catalogue are cached in Redis (reusing `RedisConfiguration`) and evicted on mutation, mirroring existing cache usage. |
| NFR-P-03 | **Security:** every endpoint is gated by the JWT filter + the permission in §8; no product endpoint is publicly accessible. |
| NFR-P-04 | **Auditability:** 100% of mutations produce a `ProductAuditLog` row; audit writes must not block the primary transaction failing the user request if the diff computation errors (log-and-continue). |
| NFR-P-05 | **Data integrity:** DB-level unique constraints on `sku` and each barcode; FK constraints where the codebase adopts them, else service-layer guards. |
| NFR-P-06 | **Scalability of import:** CSV import streams rows (no full-file in memory); supports files up to 50k rows. |
| NFR-P-07 | **Migrations:** ship the schema via Flyway (introducing migration tooling per the roadmap) rather than relying on `ddl-auto: update`. |
| NFR-P-08 | **Observability:** import/export and status changes emit Actuator-visible metrics (counts, durations). |
| NFR-P-09 | **Testing:** ≥ 80% service-layer branch coverage via the H2 `test` profile; every FR has at least one integration test. |

---

## 11. Internationalisation

- All user-facing messages (validation errors, delete-refusal reasons, import
  results) are resolved through the existing `Language` (EN/CN) enum mechanism —
  no hard-coded English in the service layer.
- Message keys namespaced `product.*` (e.g. `product.sku.duplicate`,
  `product.delete.referenced`, `product.status.invalidTransition`).
- Product `name`/`description` are stored as entered (single language for v1.1);
  a future enhancement may add a `ProductTranslation` table. This is noted, not
  built, in this release.
- The "Products" navigation menu entry supplies EN/CN labels via the existing
  `NavigationMenu` i18n keys.

---

## 12. Integration Points

| Consumer (future) | Dependency on Product module |
|---|---|
| **Inventory** | Reads `Product` (id, sku, baseUom, type=STOCKABLE) as the item being stocked; owns quantities. |
| **Purchasing** | Reads products + supplier codes + standard cost + purchase UoM. |
| **Sales** | Reads `ACTIVE` products + list price + sale UoM + barcodes. |
| **Reporting** | Reads category tree + attributes for grouping. |

The Product module exposes **read APIs** and stable ids for these consumers; it
must never depend on them (dependency arrow points inward), keeping it a true
master-data foundation.

---

## 13. Acceptance Criteria

The module is accepted for v1.1.0 when:

1. All **Must** features (F1–F4, F7) are implemented and every associated `FR-P-*`
   passes an automated test.
2. Products cannot be created with a duplicate SKU; the failure is localised in
   both EN and CN.
3. A product referenced by seeded test data cannot be physically deleted and
   returns code `423`; an unreferenced product deletes successfully.
4. Category cycles are rejected; UoM conversions round-trip correctly
   (e.g. 1 box = 12 each ⇒ 24 each = 2 box).
5. RBAC gates are enforced: a user without `PRODUCT_EDIT` receives `403` on save.
6. The product list meets NFR-P-01 against a 100k-row seed.
7. Flyway migrations create the schema on a clean database; the H2 test profile
   still boots.
8. Audit rows are written for create/update/status/delete.

---

## 14. Delivery Plan

| Phase | Scope | Exit criteria |
|---|---|---|
| **P1 — Foundations** | Entities, repositories, Flyway baseline, UoM & Category CRUD, RBAC permissions + nav menu registration | Taxonomy & UoM manageable end-to-end |
| **P2 — Product core** | Product CRUD, list/search, status lifecycle, delete guard, audit log | F1, F2, F7 + acceptance §13.2–3, §13.5, §13.8 |
| **P3 — Enrichment** | Variants, attributes, barcodes, media | F5, F6, F8 |
| **P4 — Data ops** | CSV import/export, caching, performance hardening | F9, NFR-P-01/02/06 |

Each phase is a PR branched from `main` per the branch strategy, follows the
`entity/product/`, `controller/product/`, `service/product/`,
`repository/product/`, `dto/product/` package layout, and ships with tests.

---

## 15. Out of Scope

- Real-time inventory/stock quantities and movements (future Inventory module).
- Tiered, contract, or promotional pricing; multi-currency conversion.
- Tax calculation (only a `taxCategory` label is stored).
- Multi-language product content (translation table noted for a later release).
- Binary image storage/CDN (only URI references are managed here).
- Supplier master data (a free-text supplier reference bridges until Purchasing exists).

---

_This document defines requirements only. Implementation proceeds per the phased
delivery plan and the repository's existing conventions (DTO-per-view, envelope
responses, RBAC gating, EN/CN i18n, and the `entity/<module>/` package pattern)._
