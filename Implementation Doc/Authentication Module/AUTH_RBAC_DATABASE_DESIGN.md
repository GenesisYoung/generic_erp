# SYSTEM DATABASE DESIGN

## Authentication & RBAC Module (existing / as-built)

> **Module:** Authentication, User, and Role-Based Access Control
> **Status:** Implemented — this document records the schema **as it exists**
> **Source of truth:** the JPA entities under `src/main/java/com/gsgd/generic_erp/entity/auth/`
> **Engine:** MySQL 8.0, InnoDB, `utf8mb4` / `utf8mb4_0900_ai_ci`
> **Current schema management:** Hibernate `ddl-auto: update` (see §7)

---

## 0. How This Document Was Produced

Every column below was read from the `@Column` annotations on the entity
classes. Where an entity and another artefact disagree — a database view, a
second entity mapped to the same table, or a comment in the code — the
disagreement is recorded in **§6 Findings** rather than silently resolved. No
column was invented.

Two things are therefore *not* asserted here:

- The real column list of `permission_tb`, because the `NavigationPermissionView`
  query reads columns the `Permission` entity does not map (F1).
- The exact type of `user_login_log_tb`, because two entities map that table with
  conflicting definitions (F3).

Both are flagged, and the accompanying migration marks the affected lines.

---

## 1. Module Overview

The module answers three questions:

1. **Who are you?** — `user_tb` + Argon2 password + JWT, with refresh tokens
   persisted in `user_refresh_token_tb` and every attempt logged in
   `user_login_log_tb`.
2. **What are you?** — `role_tb` and `department_tb`, attached to users through
   `user_roles_tb` and `user_departments_tb`.
3. **What may you see and do?** — `permission_tb` and `actions_tb`, plus the
   navigation-menu access model described in §3.

### 1.1 Table inventory

| # | Table | Entity | Purpose |
|---|---|---|---|
| 1 | `user_tb` | `User` | Core account: credentials, status, lockout |
| 2 | `user_info_tb` | `UserInfo` | Extended profile, split off so login queries stay small |
| 3 | `role_tb` | `Role` | Named role |
| 4 | `department_tb` | `Department` | Organisational unit; self-referencing tree |
| 5 | `permission_tb` | `Permission` | Protectable resource or capability |
| 6 | `actions_tb` | `Action` | Concrete operation (CREATE, READ, UPDATE, DELETE…) |
| 7 | `navigation_menu_tb` | `NavigationMenu` | Sidebar item; i18n key, icon, route, colour |
| 8 | `user_roles_tb` | `UserRole` | Join: user ↔ role |
| 9 | `user_departments_tb` | `UserDepartment` | Join: user ↔ department |
| 10 | `role_permission_tb` | `RolePermission` | Join: role ↔ permission, optionally menu-scoped |
| 11 | `role_department_tb` | `RoleDepartment` | Join: role ↔ department |
| 12 | `action_permission_tb` | `ActionPermission` | Join: permission ↔ action |
| 13 | `user_nav_menu` | `UserNavMenu` | Direct grant of a menu to a user |
| 14 | `menu_registered_permissions_record` | `MenuRegisteredPermissionsRecord` | Which permissions may act on a menu |
| 15 | `page_permission_approvals_record` | `PagePermissionApprovalsRecord` | A user holds a registered permission on a menu |
| 16 | `user_login_log_tb` | `UserLoginLog` / `LoginLog` | Login audit trail |
| 17 | `user_refresh_token_tb` | `UserRefreshToken` | Hashed refresh tokens for rotation |
| 18 | `navigation_permission_view` | `NavigationPermissionView` | Read-only view flattening user ↔ menu ↔ permission |

### 1.2 Conventions in force

- **Primary keys** are `BIGINT AUTO_INCREMENT`, except `user_login_log_tb` and
  `user_refresh_token_tb`, which use application-generated UUID strings.
- **Foreign keys are stored as plain scalar id columns.** No `@ManyToOne` graphs
  and no database-level `FOREIGN KEY` constraints exist. Referential integrity is
  enforced in the service layer — for example `RoleManagementService.deleteRole`
  clears `role_permission_tb` and `role_department_tb` before deleting the role,
  and refuses outright while `user_roles_tb` still holds a row.
- **Table suffix** is `_tb`, with three exceptions (F7).
- **`create_date` / `create_time`** appear on nearly every table but with three
  different Java types across the module (F8).

---

## 2. Table Definitions

### 2.1 `user_tb`

The account record used by `CustomizedUserDetailServiceImpl` on every login.

| FIELD | TYPE | KEY | NULLABLE | DEFAULT | DESCRIPTION |
|---|---|---|---|---|---|
| `id` | `BIGINT` | Primary Key | Not Null | `AUTO_INCREMENT` | User ID. **Id 1 is the root account** |
| `username` | `VARCHAR(50)` | Unique Key | Not Null | | Login name |
| `display_name` | `VARCHAR(50)` | | Nullable | | Name shown in the UI |
| `email` | `VARCHAR(50)` | | Nullable | | Email address |
| `password` | `VARCHAR(255)` | | Not Null | | Argon2id hash. Never serialised to JSON |
| `status` | `BIT(1)` | | Nullable | | `1` = active, `0` = disabled |
| `current_session_id` | `VARCHAR(36)` | | Nullable | | UUID of the live session; embedded in the JWT |
| `failed_attempted` | `TINYINT` | | Nullable | | Consecutive failed logins. Reset to 0 on success |
| `locked_until` | `DATETIME(6)` | | Nullable | | Account is locked while this is in the future |
| `create_date` | `DATE` | | Nullable | | Creation date |
| `is_enabled` | `TINYINT` | | Nullable | | Second enablement flag, separate from `status` |

**Note on the two flags.** `UserDetail.isEnabled()` requires
`is_enabled == 1` **and** `status != FALSE`. They are different columns with
different types serving overlapping purposes — see F5.

**Session invalidation.** `current_session_id` is regenerated on each login and
carried inside the JWT, so issuing a new login silently invalidates older tokens.
`findByCurrentSessionId` supports the reverse lookup.

---

### 2.2 `user_info_tb`

| FIELD | TYPE | KEY | NULLABLE | DEFAULT | DESCRIPTION |
|---|---|---|---|---|---|
| `id` | `BIGINT` | Primary Key | Not Null | `AUTO_INCREMENT` | Row ID |
| `user_id` | `BIGINT` | | Not Null | | Owning user |
| `real_name` | `VARCHAR(50)` | | Nullable | | Legal name |
| `title` | `VARCHAR(100)` | | Nullable | | Job title |
| `birthday` | `DATE` | | Nullable | | Date of birth |
| `hire_date` | `DATE` | | Nullable | | Employment start date |
| `create_date` | `DATE` | | Nullable | | Creation date |

The split from `user_tb` is deliberate: the login path loads only `user_tb`, so
profile data never enters the authentication query.

---

### 2.3 `role_tb`

| FIELD | TYPE | KEY | NULLABLE | DEFAULT | DESCRIPTION |
|---|---|---|---|---|---|
| `id` | `BIGINT` | Primary Key | Not Null | `AUTO_INCREMENT` | Role ID |
| `role_name` | `VARCHAR(50)` | | Not Null | | Role name. Becomes the Spring authority `ROLE_<role_name>` |
| `val` | `INT` | | Nullable | | Stable numeric code — see below |
| `create_date` | `DATE` | | Nullable | | Creation date |

**`val` is the external role code.** `UserDTO.roleList` carries a
`List<Integer>` of these values rather than ids, and `RoleRepository` exposes
`getIdByVal` and `findObjByValue`. Its exact semantics are undocumented — the
entity comment says only *"likely used as a bitmask or priority rank"* — so this
document does not state which. **Confirm before seeding production values** (F6).

**Uniqueness.** `RoleManagementService.saveOrUpdate` rejects a duplicate
`role_name` in Java, but there is no unique index behind it (F4).

---

### 2.4 `department_tb`

| FIELD | TYPE | KEY | NULLABLE | DEFAULT | DESCRIPTION |
|---|---|---|---|---|---|
| `id` | `BIGINT` | Primary Key | Not Null | `AUTO_INCREMENT` | Department ID |
| `dept_name` | `VARCHAR(50)` | | Not Null | | Department name |
| `val` | `INT` | | Nullable | | Numeric code, mirroring `role_tb.val` |
| `parent_id` | `BIGINT` | | Nullable | | Parent department. `NULL` for a root node |
| `create_date` | `DATE` | | Nullable | | Creation date |

---

### 2.5 `permission_tb`

| FIELD | TYPE | KEY | NULLABLE | DEFAULT | DESCRIPTION |
|---|---|---|---|---|---|
| `id` | `BIGINT` | Primary Key | Not Null | `AUTO_INCREMENT` | Permission ID |
| `permission_code` | `VARCHAR(50)` | | Not Null | | e.g. `menu.userManagement`, `PRODUCT_VIEW` |
| `is_enabled` | `INT` | | Not Null | | `1` = active. Filtered on by `PermissionSpecification.enabled()` |
| `create_date` | `DATE` | | Nullable | | Creation date |
| `permission_name` | `VARCHAR(50)` | | Nullable | | **Not mapped by the entity** — see F1 |
| `val` | `BIGINT` | | Nullable | | **Not mapped by the entity** — see F1 |

**The `menu.` prefix is meaningful.**
`PermissionSpecification.findMenuPermission()` selects rows whose
`permission_code` starts with `menu.`, so that prefix is a real, load-bearing
naming convention rather than a style choice.

---

### 2.6 `actions_tb`

| FIELD | TYPE | KEY | NULLABLE | DEFAULT | DESCRIPTION |
|---|---|---|---|---|---|
| `id` | `BIGINT` | Primary Key | Not Null | `AUTO_INCREMENT` | Action ID |
| `action_name` | `VARCHAR(50)` | | Not Null | | `CREATE`, `READ`, `UPDATE`, `DELETE`, or finer-grained |
| `create_date` | `DATE` | | Nullable | | Creation date |

The table name is plural while every other table is singular. The entity carries
an explicit comment acknowledging this, so it is intentional and must not be
"corrected" (F7).

---

### 2.7 `navigation_menu_tb`

| FIELD | TYPE | KEY | NULLABLE | DEFAULT | DESCRIPTION |
|---|---|---|---|---|---|
| `id` | `BIGINT` | Primary Key | Not Null | `AUTO_INCREMENT` | Menu ID |
| `parent_id` | `BIGINT` | | Nullable | | Parent menu. `NULL` for a top-level item |
| `title_key` | `VARCHAR(50)` | | Not Null | | i18n key resolved by the frontend, e.g. `userManagement` |
| `icon` | `VARCHAR(50)` | | Not Null | | MDI icon name, e.g. `mdi-office-building-cog` |
| `route` | `VARCHAR(50)` | | Not Null | | Vue Router path, e.g. `/manage/departments` |
| `color` | `VARCHAR(20)` | | Not Null | | Vuetify colour, e.g. `indigo` |
| `permission_id` | `VARCHAR(255)` | | Nullable | | Default permission bound to this menu — **stored as a string** (F2) |
| `is_enabled` | `BIT(1)` | | Not Null | | Whether the item is visible at all |
| `create_time` | `DATETIME(6)` | | Not Null | | Creation timestamp |

**The sidebar is data-driven.** The frontend renders whatever
`GET /api/users/fetch/sidebar/menu` returns. A child menu only nests correctly if
its parent is in the same result set, which is why `nav_department_management.sql`
grants both the parent and the child together.

---

### 2.8 Join tables

#### `user_roles_tb`

| FIELD | TYPE | KEY | NULLABLE | DESCRIPTION |
|---|---|---|---|---|
| `id` | `BIGINT` | Primary Key | Not Null | Row ID |
| `user_id` | `BIGINT` | | Not Null | User |
| `role_id` | `BIGINT` | | Not Null | Role |
| `create_date` | `DATE` | | Nullable | Creation date |

#### `user_departments_tb`

| FIELD | TYPE | KEY | NULLABLE | DESCRIPTION |
|---|---|---|---|---|
| `id` | `BIGINT` | Primary Key | Not Null | Row ID |
| `user_id` | `BIGINT` | | Not Null | User |
| `dept_id` | `BIGINT` | | Not Null | Department |
| `create_date` | `DATE` | | Nullable | Creation date |

#### `role_permission_tb`

| FIELD | TYPE | KEY | NULLABLE | DESCRIPTION |
|---|---|---|---|---|
| `id` | `BIGINT` | Primary Key | Not Null | Row ID |
| `role_id` | `BIGINT` | | Not Null | Role |
| `permission_id` | `BIGINT` | | Not Null | Permission |
| `menu_id` | `BIGINT` | | Nullable | Optional menu scope. `NULL` = role-wide grant |
| `created_by` | `BIGINT` | | Not Null | Actor who created the grant |
| `create_time` | `DATETIME(6)` | | Not Null | Creation timestamp |
| `create_date` | `DATE` | | Nullable | Creation date (redundant with `create_time`) |

This is the only join table carrying an audit column. `created_by` falls back to
`1` — the root user — when no username principal is present, so system-initiated
grants are attributed to root.

#### `role_department_tb`

| FIELD | TYPE | KEY | NULLABLE | DESCRIPTION |
|---|---|---|---|---|
| `id` | `BIGINT` | Primary Key | Not Null | Row ID |
| `role_id` | `BIGINT` | | Not Null | Role |
| `department_id` | `BIGINT` | | Not Null | Department |

The only table in the module with no timestamp column at all.

#### `action_permission_tb`

| FIELD | TYPE | KEY | NULLABLE | DESCRIPTION |
|---|---|---|---|---|
| `id` | `BIGINT` | Primary Key | Not Null | Row ID |
| `permission_id` | `BIGINT` | | Not Null | Permission |
| `action_id` | `BIGINT` | | Not Null | Action |
| `create_date` | `DATE` | | Nullable | Creation date |

Semantics: permission `product` × action `DELETE` ⇒ the holder may delete
products.

---

### 2.9 Menu access tables

#### `user_nav_menu`

| FIELD | TYPE | KEY | NULLABLE | DESCRIPTION |
|---|---|---|---|---|
| `id` | `BIGINT` | Primary Key | Not Null | Row ID |
| `nav_id` | `BIGINT` | | Not Null | Menu |
| `permission_id` | `BIGINT` | | Nullable | Optional permission attached to the grant |
| `user_id` | `BIGINT` | | Not Null | User |
| `is_enabled` | `BIT(1)` | | Not Null | Whether the grant is live |
| `create_date` | `DATETIME(6)` | | Not Null | Creation timestamp — a `DATETIME` despite the `_date` name |

#### `menu_registered_permissions_record`

| FIELD | TYPE | KEY | NULLABLE | DESCRIPTION |
|---|---|---|---|---|
| `id` | `BIGINT` | Primary Key | Not Null | Row ID |
| `menu_id` | `BIGINT` | | Not Null | Menu |
| `permission_id` | `BIGINT` | | Not Null | Permission registered against that menu |
| `create_date` | `DATETIME(6)` | | Not Null | Set by a **database default** — the entity is `insertable = false` |

#### `page_permission_approvals_record`

| FIELD | TYPE | KEY | NULLABLE | DESCRIPTION |
|---|---|---|---|---|
| `id` | `BIGINT` | Primary Key | Not Null | Row ID |
| `user_id` | `BIGINT` | | Not Null | User |
| `permission_id` | `BIGINT` | | Not Null | Permission the user holds |
| `menu_id` | `BIGINT` | | Not Null | Menu the permission applies to |
| `create_date` | `DATETIME(6)` | | Not Null | Set by a **database default** |

Both tables need `DEFAULT CURRENT_TIMESTAMP(6)` in the DDL. Because the entities
mark `create_date` as `insertable = false`, Hibernate never sends a value; without
the default, every insert fails on the `NOT NULL` constraint.

---

### 2.10 `user_login_log_tb`

| FIELD | TYPE | KEY | NULLABLE | DESCRIPTION |
|---|---|---|---|---|
| `id` | `VARCHAR(36)` | Primary Key | Not Null | UUID generated by the application |
| `user_id` | `BIGINT` | | Nullable | User, when known |
| `login_ip` | `VARCHAR(45)` | | Nullable | Source IP. 45 chars covers IPv6 |
| `login_time` | `DATETIME(6)` | | Nullable | Attempt timestamp |
| `status` | `TINYINT` | | Nullable | Outcome. `1` = success, `0` = failure |
| `create_date` | `DATE` | | Nullable | Creation date |

**Two entities map this table with conflicting definitions.** The types above are
the widest that satisfy both; see F3 for the conflict and what must be done
about it.

---

### 2.11 `user_refresh_token_tb`

| FIELD | TYPE | KEY | NULLABLE | DESCRIPTION |
|---|---|---|---|---|
| `id` | `VARCHAR(32)` | Primary Key | Not Null | UUID, application-generated |
| `user_id` | `BIGINT` | | Not Null | Owning user |
| `token_hash` | `VARCHAR(64)` | | Not Null | Hash of the refresh token — **never the raw token** |
| `expires_at` | `DATETIME(6)` | | Not Null | Expiry |
| `revoked` | `BIT(1)` | | Nullable | `1` after logout or rotation |
| `create_date` | `DATE` | | Nullable | Creation date |

`token_hash` is widened from the entity's declared 50 to **64**, because a
SHA-256 hex digest is exactly 64 characters. The entity itself carries this
warning in a comment — see F9.

---

### 2.12 `navigation_permission_view`

A read-only view flattening one row per menu grant, mapped by
`NavigationPermissionView` and created via Hibernate's `@View`.

```sql
SELECT m.id, n.id AS nav_id, n.title_key AS nav_key, n.route,
       u.id AS u_id, u.username AS u_name,
       p.id AS p_id, p.permission_name AS p_name, p.val AS p_val
FROM user_nav_menu m
  LEFT JOIN navigation_menu_tb n ON m.nav_id = n.id
  LEFT JOIN user_tb u            ON m.user_id = u.id
  LEFT JOIN permission_tb p      ON m.permission_id = p.id
```

This view is the reason `permission_tb` is listed in §2.5 with two unmapped
columns: the view cannot compile without them (F1).

---

## 3. The Menu Access Model

Menu visibility is the most intricate part of the module, so it is worth stating
plainly. `UserService.fetchNavMenu` merges **two independent paths**, and a menu
appears if **either** produces a hit.

**Path A — direct grant.** A row in `user_nav_menu` for that `(user_id, nav_id)`.

**Path B — via permission.** Two rows are needed:

1. `menu_registered_permissions_record` says permission P *may* act on menu M.
2. `page_permission_approvals_record` says user U *holds* P on M.

Registration comes first: `MenuAccessService.listPermissionsForMenu` only ever
offers permissions already registered against the menu, so an unregistered
permission can never be approved.

**De-registration cascades in Java.** When a permission is de-registered from a
menu, `setPermissionRegistration` also deletes every approval row for that
`(menu, permission)` pair. Without that step an orphaned approval would keep
granting access through Path B while no longer being visible in any admin screen
— access that could not be revoked through the UI.

**Revocation must clear both paths.** Removing a direct grant while leaving an
approval in place makes "revoke" a no-op from the administrator's point of view,
because Path B still returns the menu.

---

## 4. Delete Guards

Two deletions are refused rather than cascaded, both returning **HTTP 200 with
an envelope code of `423`** rather than a `4xx` status. The reason is specific:
the frontend's axios interceptor treats any non-2xx response as a signal to
refresh the token and retry, so a real `409` would be swallowed by the retry
path instead of shown to the user.

| Deletion | Refused when | Message key |
|---|---|---|
| Role | any row in `user_roles_tb` references it | `ROLE_ASSIGNED` |
| Department | any user is still assigned to it | `DEPT_ASSIGNED` |

Deleting a role that *is* unused first clears `role_permission_tb` and
`role_department_tb`, so no orphaned join rows remain. Deleting menus clears
`user_nav_menu` first, for the same reason.

The root user (id 1) cannot be deleted at all — `CANT_DELETE_ROOT_USER`.

---

## 5. Authentication Flow

1. `POST /api/auth/login`. A rate limiter allows **20 attempts per minute per IP**
   and **10 per minute per username**; exceeding either returns `429`.
2. The Argon2 encoder verifies the password. On success `failed_attempted` resets
   to 0 and `locked_until` is cleared.
3. A fresh `current_session_id` UUID is written to `user_tb` and embedded in both
   tokens, so a new login invalidates every earlier token.
4. The access token goes to the client in the response body; the refresh token
   goes into an `HttpOnly`, `SameSite=Strict` cookie scoped to `/api/auth`.
5. Refresh-token hashes are persisted in `user_refresh_token_tb` for validation
   and rotation.

### Argon2 parameters

| Where | Parameters |
|---|---|
| `WebSecurityConfiurer.passwordEncoder()` (verification) | `Argon2PasswordEncoder(64, 128, 2, 65536, 4)` |
| `AuthenticationImpl.generatePass()` (creation) | `Argon2PasswordEncoder(64, 50, 2, 65536, 4)` |

That is `saltLength, hashLength, parallelism, memoryKB, iterations`. The two
differ in hash length, 128 versus 50 — despite a comment in each file stating
they must match. Verification still works, because Spring's
`Argon2PasswordEncoder.matches` parses `m`, `t`, `p`, and the salt out of the
stored `$argon2id$…` string rather than using its own configuration. The
mismatch is therefore harmless today but contradicts the documented invariant
(F10).

The seed migration's root-user hash is generated with the **creation** parameters
(hash length 50), matching what `generatePass` would produce.

---

## 6. Findings

Ordered by how much damage each can do.

### F1 — `navigation_permission_view` reads columns the `Permission` entity does not map

The view selects `p.permission_name` and `p.val` from `permission_tb`. The
`Permission` entity maps only `permission_code`, `is_enabled`, and `create_date`.

Since `ddl-auto: update` **never drops columns**, the most likely explanation is
that an earlier version of the entity had `permission_name` and `val`, they were
renamed to `permission_code`, and the old columns were left behind in the live
database while the view continued to read them.

If so, the view is showing stale data that no code path updates any more.
**Action:** run `SHOW COLUMNS FROM permission_tb;` against the live schema. If
the legacy columns exist and are unpopulated, rewrite the view to use
`permission_code` and drop them. The baseline migration includes both columns,
clearly marked, so the view can be created either way.

### F2 — `navigation_menu_tb.permission_id` is a `String`

```java
@Column(name = "permission_id")
private String pId;
```

It is a de facto foreign key to `permission_tb.id`, which is a `Long`. Joining
them forces MySQL into an implicit type conversion, which **disables index use on
that column** and can produce surprising matches (`'1abc'` converts to `1`).
Change the field to `Long` and the column to `BIGINT`.

### F3 — Two entities map `user_login_log_tb`

| | `UserLoginLog` | `LoginLog` |
|---|---|---|
| `id` | `String`, length 32, no generator | `String`, length 36, `GenerationType.UUID` |
| `status` | `Boolean` | `Integer` |
| `create_date` | `java.time.LocalDate` | `java.sql.Date` |
| `login_ip` | length 32 | default length |

Hibernate registers both. Which one wins for DDL generation is not defined by the
mapping, and code reading through one while writing through the other will
misinterpret `status`. Only `LoginLogRepository` exists, and it is typed to
`LoginLog`, so **`UserLoginLog` appears to be dead code — delete it** and keep
`LoginLog`.

### F4 — No unique constraints on any join table

`user_roles_tb`, `user_departments_tb`, `role_permission_tb`,
`action_permission_tb`, `user_nav_menu`, and both `_record` tables can all hold
duplicate rows. The services guard with a read-then-write pattern:

```java
boolean exists = rolePermissionRepository.existsByRoleIdAndPermissionId(roleId, permissionId);
if (granted) { if (!exists) { rolePermissionRepository.save(...); } }
```

Between the `exists` check and the `save`, a second concurrent request can pass
the same check. Two clicks on a toggle produce two rows, and the later "revoke"
deletes only what it finds in one pass. A unique key on the natural key pair
makes the operation genuinely idempotent, and the DDL to add each one is included
in the migration.

### F5 — `user_tb` has two overlapping enablement flags

`status` is `BIT(1)` and `is_enabled` is `TINYINT`, and
`UserDetail.isEnabled()` requires both. Two flags with one meaning invite the
state where they disagree, and nothing in the schema prevents it. Consolidate
into one column.

### F6 — `role_tb.val` and `department_tb.val` are undocumented

`val` is used as an external identifier across the API — `UserDTO.roleList`
carries values, not ids — but the entity comment says only *"likely used as a
bitmask or priority rank."* If it is a bitmask, the values must be powers of two
and the column will overflow past 31 roles; if it is a rank, they need not be.
The seed migration assigns sequential values and marks them for confirmation.

### F7 — Three tables break the `_tb` naming convention

`user_nav_menu`, `menu_registered_permissions_record`, and
`page_permission_approvals_record`. Renaming them is a breaking change for
existing data, so record the exception rather than fix it. (`actions_tb` is
plural but does carry the suffix, and its entity documents the choice.)

### F8 — `create_date` has three different types

`DATE` in most tables, `DATETIME` in `user_nav_menu` and both `_record` tables,
and `java.sql.Date` in `LoginLog`. A column called `create_date` that sometimes
holds a time and sometimes does not will eventually be compared across tables and
give wrong answers.

### F9 — `user_refresh_token_tb.token_hash` is too short

Declared `VARCHAR(50)`; a SHA-256 hex digest is 64 characters. MySQL in
non-strict mode truncates silently, and a truncated hash never matches — every
refresh fails with no error to explain why. The entity already carries this
warning as a comment. The migration uses `VARCHAR(64)`.

### F10 — The two Argon2 encoders disagree

See §5. Harmless today, but both files claim they must match, so either the
comment or the code is wrong. Extract the parameters into one constant.

### F11 — `ddl-auto: update` cannot express any of the above

`update` adds tables and columns. It never drops a column, never narrows a type,
never adds a constraint to existing data, and never removes an index. Every
finding here is invisible to it. This is why the accompanying migration exists.

---

## 7. Migration Strategy

The project currently runs `ddl-auto: update`, which `DEVELOPMENT.md` already
flags as unsuitable for a shared environment. The Product module requirements
(NFR-P-07) commit to introducing Flyway. That introduction should start here, not
with the Product module, because the Product tables reference `user_tb` for their
audit columns.

| File | Contents |
|---|---|
| `V1__auth_rbac_baseline.sql` | DDL for all 17 tables plus the view |
| `V2__auth_rbac_seed.sql` | Initial data — root account, roles, actions, permissions, menus |
| `V3__product_module.sql` | The Product module schema (previously numbered `V1`) |

### Adopting Flyway on an existing database

The live database already has these tables. Running `V1` against it would fail.
Use Flyway's baseline mechanism:

```properties
spring.flyway.enabled=true
spring.flyway.baseline-on-migrate=true
spring.flyway.baseline-version=1
spring.jpa.hibernate.ddl-auto=validate
```

`baseline-on-migrate` marks the existing schema as already at version 1 without
running `V1`, so only `V2` onward execute. Switching `ddl-auto` to `validate`
means Hibernate checks the schema against the entities on startup and refuses to
boot on a mismatch, instead of silently patching it.

**Verify `V1` matches your live schema before baselining.** Dump the current
schema and diff it:

```bash
mysqldump --no-data --skip-add-drop-table generic_erp > current_schema.sql
```

Pay particular attention to `permission_tb` (F1) and `user_login_log_tb` (F3),
the two tables this document could not state with certainty.

The H2 `test` profile keeps `create-drop` and is unaffected.

---

## 8. Initial Data

`V2__auth_rbac_seed.sql` creates the minimum needed for a usable system.

| Table | Rows | Notes |
|---|---|---|
| `actions_tb` | 4 | `CREATE`, `READ`, `UPDATE`, `DELETE` |
| `role_tb` | 3 | `ROOT`, `ADMIN`, `USER` |
| `department_tb` | 1 | `Head Office`, root node |
| `permission_tb` | 6 | One `menu.*` code per seeded menu |
| `navigation_menu_tb` | 6 | Sidebar tree, `departmentManagement` nested under `userManagement` |
| `user_tb` | 1 | **id 1 = `root`** |
| `user_info_tb` | 1 | Profile for root |
| `user_roles_tb` | 1 | root → `ROOT` |
| `user_departments_tb` | 1 | root → Head Office |
| `user_nav_menu` | 6 | root sees every menu |
| `menu_registered_permissions_record` | 6 | Each `menu.*` permission registered against its menu |
| `role_permission_tb` | 6 | `ROOT` holds every seeded permission |
| `action_permission_tb` | 24 | Every action on every permission, for `ROOT` |

### Root credentials

| Username | Password |
|---|---|
| `root` | `ChangeMe@2026` |

The stored value is a real Argon2id hash produced with the project's own
creation parameters (`saltLength 64, hashLength 50, parallelism 2, memory 65536 KB,
iterations 4`), so it verifies against the configured encoder without any extra
step.

**Change it immediately after the first login.** A default credential committed
to a repository is public knowledge the moment the repository is shared.

### What must be verified before running the seed

Three values could not be established from the code and are marked with
`-- VERIFY:` comments in the SQL:

1. **Menu routes.** Only `/manage/departments` is evidenced, from
   `nav_department_management.sql`. The other five follow the same `/manage/…`
   pattern but must be checked against the Vue Router configuration — a route
   that does not exist renders a sidebar link leading to a blank page.
2. **`role_tb.val` values.** Sequential `1, 2, 3` are used pending F6.
3. **`permission_tb.val` / `permission_name`.** Populated to mirror
   `permission_code` so `navigation_permission_view` returns something readable,
   pending the outcome of F1.

The seed is written with `INSERT … SELECT … WHERE NOT EXISTS` guards throughout,
matching the style of the existing `nav_department_management.sql`, so re-running
it does not create duplicates.
