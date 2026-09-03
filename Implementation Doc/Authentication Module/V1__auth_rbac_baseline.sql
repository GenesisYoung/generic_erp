-- =============================================================================
--  Authentication & RBAC module - baseline schema
--  Target: MySQL 8.0, InnoDB, utf8mb4 / utf8mb4_0900_ai_ci
--
--  This file reproduces the schema that Hibernate's `ddl-auto: update` has been
--  creating, so that schema management can move to Flyway.
--
--  ADOPTING THIS ON AN EXISTING DATABASE
--  -------------------------------------
--  The tables already exist in your dev/staging database. Do NOT run this file
--  against them - it will fail on the first CREATE TABLE. Instead baseline:
--
--      spring.flyway.enabled=true
--      spring.flyway.baseline-on-migrate=true
--      spring.flyway.baseline-version=1
--      spring.jpa.hibernate.ddl-auto=validate
--
--  Flyway then records the existing schema as version 1 without executing this
--  file, and only V2 onward run. Diff this file against a real dump first:
--
--      mysqldump --no-data --skip-add-drop-table generic_erp > current.sql
--
--  Two tables could not be stated with certainty from the entity classes and
--  are marked VERIFY below: permission_tb (finding F1) and user_login_log_tb
--  (finding F3). Check those two against the dump before baselining.
--
--  TYPE MAPPING USED
--  -----------------
--      Java Boolean       -> BIT(1)        (Hibernate 6 MySQL default)
--      Java Byte          -> TINYINT
--      Java Long          -> BIGINT
--      Java LocalDate     -> DATE
--      Java LocalDateTime -> DATETIME(6)
--
--  FOREIGN KEYS
--  ------------
--  The codebase stores foreign keys as plain scalar id columns and enforces
--  integrity in the service layer. That convention is preserved here: no
--  FOREIGN KEY constraints are declared, only indexes on the FK columns.
--  Section 9 at the end lists the constraints you would add if you later decide
--  to enforce them in the database.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. user_tb
-- -----------------------------------------------------------------------------
CREATE TABLE user_tb (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    username            VARCHAR(50)  NOT NULL,
    display_name        VARCHAR(50)  NULL,
    email               VARCHAR(50)  NULL,
    password            VARCHAR(255) NOT NULL,
    -- 1 = active, 0 = disabled. Overlaps with is_enabled - see finding F5.
    status              BIT(1)       NULL,
    current_session_id  VARCHAR(36)  NULL,
    failed_attempted    TINYINT      NULL DEFAULT 0,
    locked_until        DATETIME(6)  NULL,
    create_date         DATE         NULL,
    is_enabled          TINYINT      NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username),
    -- UserRepository.existsByEmail rejects duplicates in Java only; this index
    -- makes the rule real. Drop it if duplicate/blank emails already exist.
    UNIQUE KEY uk_user_email (email),
    KEY idx_user_session (current_session_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- 2. user_info_tb
-- -----------------------------------------------------------------------------
CREATE TABLE user_info_tb (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    user_id      BIGINT       NOT NULL,
    real_name    VARCHAR(50)  NULL,
    title        VARCHAR(100) NULL,
    birthday     DATE         NULL,
    hire_date    DATE         NULL,
    create_date  DATE         NULL,
    PRIMARY KEY (id),
    -- UserInfoRepository.findByUserId returns Optional, i.e. at most one row.
    UNIQUE KEY uk_user_info_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- 3. role_tb
-- -----------------------------------------------------------------------------
CREATE TABLE role_tb (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    role_name    VARCHAR(50) NOT NULL,
    -- External numeric role code used by UserDTO.roleList. Semantics
    -- undocumented - see finding F6.
    val          INT         NULL,
    create_date  DATE        NULL,
    PRIMARY KEY (id),
    -- RoleManagementService.saveOrUpdate rejects duplicate names in Java.
    UNIQUE KEY uk_role_name (role_name),
    -- RoleRepository.getIdByVal returns a single Long, so val must be unique.
    UNIQUE KEY uk_role_val (val)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- 4. department_tb
-- -----------------------------------------------------------------------------
CREATE TABLE department_tb (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    dept_name    VARCHAR(50) NOT NULL,
    val          INT         NULL,
    parent_id    BIGINT      NULL,
    create_date  DATE        NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_dept_name (dept_name),
    KEY idx_dept_parent (parent_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- 5. permission_tb
--    VERIFY: permission_name and val are NOT mapped by the Permission entity,
--    but navigation_permission_view selects them. They are included so the view
--    can be created. Confirm against your live schema (finding F1) and drop them
--    here if the view is rewritten to use permission_code instead.
-- -----------------------------------------------------------------------------
CREATE TABLE permission_tb (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    permission_code  VARCHAR(50) NOT NULL,
    is_enabled       INT         NOT NULL DEFAULT 1,
    create_date      DATE        NULL,
    permission_name  VARCHAR(50) NULL,   -- VERIFY (F1): legacy, unmapped
    val              BIGINT      NULL,   -- VERIFY (F1): legacy, unmapped
    PRIMARY KEY (id),
    UNIQUE KEY uk_permission_code (permission_code),
    -- PermissionSpecification filters on is_enabled and LIKE 'menu.%'.
    KEY idx_permission_enabled (is_enabled, permission_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- 6. actions_tb   (plural by design - see finding F7)
-- -----------------------------------------------------------------------------
CREATE TABLE actions_tb (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    action_name  VARCHAR(50) NOT NULL,
    create_date  DATE        NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_action_name (action_name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- 7. navigation_menu_tb
--    NOTE: permission_id is VARCHAR because NavigationMenu.pId is a String,
--    while permission_tb.id is BIGINT. This is finding F2. Kept as-is so the
--    baseline matches the current entity; fix the entity and the column
--    together in a later migration.
-- -----------------------------------------------------------------------------
CREATE TABLE navigation_menu_tb (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    parent_id      BIGINT       NULL,
    title_key      VARCHAR(50)  NOT NULL,
    icon           VARCHAR(50)  NOT NULL,
    route          VARCHAR(50)  NOT NULL,
    color          VARCHAR(20)  NOT NULL,
    permission_id  VARCHAR(255) NULL,   -- F2: should be BIGINT
    is_enabled     BIT(1)       NOT NULL DEFAULT b'1',
    create_time    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    -- The seed script and MenuSpecification both look menus up by title_key.
    UNIQUE KEY uk_menu_title_key (title_key),
    KEY idx_menu_parent (parent_id),
    KEY idx_menu_route (route)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- 8. user_roles_tb
-- -----------------------------------------------------------------------------
CREATE TABLE user_roles_tb (
    id           BIGINT NOT NULL AUTO_INCREMENT,
    user_id      BIGINT NOT NULL,
    role_id      BIGINT NOT NULL,
    create_date  DATE   NULL,
    PRIMARY KEY (id),
    -- F4: makes existsByUserIdAndRoleId + save genuinely idempotent.
    UNIQUE KEY uk_user_role (user_id, role_id),
    KEY idx_user_role_role (role_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- 9. user_departments_tb
-- -----------------------------------------------------------------------------
CREATE TABLE user_departments_tb (
    id           BIGINT NOT NULL AUTO_INCREMENT,
    user_id      BIGINT NOT NULL,
    dept_id      BIGINT NOT NULL,
    create_date  DATE   NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_dept (user_id, dept_id),
    KEY idx_user_dept_dept (dept_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- 10. role_permission_tb
--     The only join table with audit columns. created_by falls back to the root
--     user (id 1) for system-initiated grants.
-- -----------------------------------------------------------------------------
CREATE TABLE role_permission_tb (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    role_id        BIGINT      NOT NULL,
    permission_id  BIGINT      NOT NULL,
    -- NULL = role-wide grant; non-null scopes the grant to one menu.
    menu_id        BIGINT      NULL,
    created_by     BIGINT      NOT NULL,
    create_time    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    create_date    DATE        NULL,
    PRIMARY KEY (id),
    -- existsByRoleIdAndPermissionId ignores menu_id, so the natural key is the
    -- pair, not the triple.
    UNIQUE KEY uk_role_permission (role_id, permission_id),
    KEY idx_role_perm_permission (permission_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- 11. role_department_tb
-- -----------------------------------------------------------------------------
CREATE TABLE role_department_tb (
    id             BIGINT NOT NULL AUTO_INCREMENT,
    role_id        BIGINT NOT NULL,
    department_id  BIGINT NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_department (role_id, department_id),
    KEY idx_role_dept_department (department_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- 12. action_permission_tb
-- -----------------------------------------------------------------------------
CREATE TABLE action_permission_tb (
    id             BIGINT NOT NULL AUTO_INCREMENT,
    permission_id  BIGINT NOT NULL,
    action_id      BIGINT NOT NULL,
    create_date    DATE   NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_action_permission (permission_id, action_id),
    KEY idx_action_perm_action (action_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- 13. user_nav_menu   (no _tb suffix - see finding F7)
--     Path A of the menu access model: a direct grant.
-- -----------------------------------------------------------------------------
CREATE TABLE user_nav_menu (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    nav_id         BIGINT      NOT NULL,
    permission_id  BIGINT      NULL,
    user_id        BIGINT      NOT NULL,
    is_enabled     BIT(1)      NOT NULL DEFAULT b'1',
    -- Named _date but holds a time.
    create_date    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_nav (user_id, nav_id),
    -- findByNavIdAndIsEnabledTrue drives the "who can see this menu" screen.
    KEY idx_user_nav_nav (nav_id, is_enabled)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- 14. menu_registered_permissions_record
--     Path B, step 1: permission P may act on menu M.
--     create_date MUST have a DB default - the entity is insertable = false,
--     so Hibernate never sends a value and a NOT NULL column without a default
--     would reject every insert.
-- -----------------------------------------------------------------------------
CREATE TABLE menu_registered_permissions_record (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    menu_id        BIGINT      NOT NULL,
    permission_id  BIGINT      NOT NULL,
    create_date    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_menu_registered (menu_id, permission_id),
    KEY idx_menu_reg_permission (permission_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- 15. page_permission_approvals_record
--     Path B, step 2: user U holds permission P on menu M.
--     Same insertable = false / DB default requirement as above.
-- -----------------------------------------------------------------------------
CREATE TABLE page_permission_approvals_record (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    user_id        BIGINT      NOT NULL,
    permission_id  BIGINT      NOT NULL,
    menu_id        BIGINT      NOT NULL,
    create_date    DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_page_approval (user_id, menu_id, permission_id),
    -- findByUserId drives the sidebar; findByMenuIdAndPermissionId drives the
    -- de-registration cascade.
    KEY idx_approval_user (user_id),
    KEY idx_approval_menu_perm (menu_id, permission_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- 16. user_login_log_tb
--     VERIFY (F3): two entities map this table with conflicting definitions.
--     UserLoginLog  -> id VARCHAR(32), status Boolean, login_ip VARCHAR(32)
--     LoginLog      -> id VARCHAR(36), status Integer
--     The definition below is the widest that satisfies both. Only
--     LoginLogRepository exists and it is typed to LoginLog, so UserLoginLog
--     looks like dead code - delete it and this ambiguity disappears.
--     login_ip is 45 chars so a full IPv6 address fits.
-- -----------------------------------------------------------------------------
CREATE TABLE user_login_log_tb (
    id           VARCHAR(36) NOT NULL,
    user_id      BIGINT      NULL,
    login_ip     VARCHAR(45) NULL,
    login_time   DATETIME(6) NULL,
    status       TINYINT     NULL,
    create_date  DATE        NULL,
    PRIMARY KEY (id),
    -- "recent logins for this user" and "recent failures" are the two reads.
    KEY idx_login_user_time (user_id, login_time),
    KEY idx_login_time (login_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- 17. user_refresh_token_tb
--     token_hash is VARCHAR(64), not the entity's declared 50: a SHA-256 hex
--     digest is exactly 64 chars, and a silently truncated hash never matches
--     (finding F9). Widen the @Column(length) to 64 to match.
-- -----------------------------------------------------------------------------
CREATE TABLE user_refresh_token_tb (
    id           VARCHAR(32) NOT NULL,
    user_id      BIGINT      NOT NULL,
    token_hash   VARCHAR(64) NOT NULL,
    expires_at   DATETIME(6) NOT NULL,
    revoked      BIT(1)      NULL DEFAULT b'0',
    create_date  DATE        NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_token_hash (token_hash),
    -- "revoke every token for this user" on logout.
    KEY idx_refresh_user (user_id, revoked),
    -- Supports a scheduled purge of expired rows.
    KEY idx_refresh_expires (expires_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- -----------------------------------------------------------------------------
-- 18. navigation_permission_view
--     Mapped by NavigationPermissionView via Hibernate's @View. Reproduced here
--     verbatim so the view is owned by the migration rather than by ddl-auto.
--     Depends on permission_tb.permission_name and permission_tb.val - see the
--     VERIFY note on table 5.
-- -----------------------------------------------------------------------------
CREATE OR REPLACE VIEW navigation_permission_view AS
SELECT m.id              AS id,
       n.id              AS nav_id,
       n.title_key       AS nav_key,
       n.route           AS route,
       u.id              AS u_id,
       u.username        AS u_name,
       p.id              AS p_id,
       p.permission_name AS p_name,
       p.val             AS p_val
FROM user_nav_menu m
         LEFT JOIN navigation_menu_tb n ON m.nav_id = n.id
         LEFT JOIN user_tb u            ON m.user_id = u.id
         LEFT JOIN permission_tb p      ON m.permission_id = p.id;

-- =============================================================================
--  19. OPTIONAL - database-level foreign keys
--
--  The codebase deliberately does not use them (scalar id columns, service-layer
--  guards). If you later decide to enforce integrity in the database, these are
--  the constraints. Add them only after cleaning up any orphaned rows, or the
--  ALTER will fail.
--
--  RESTRICT is used where the service already refuses the delete (role,
--  department), CASCADE where the service already deletes children first.
-- =============================================================================
/*
ALTER TABLE user_info_tb          ADD CONSTRAINT fk_user_info_user       FOREIGN KEY (user_id)       REFERENCES user_tb (id)             ON DELETE CASCADE;
ALTER TABLE department_tb         ADD CONSTRAINT fk_dept_parent          FOREIGN KEY (parent_id)     REFERENCES department_tb (id)       ON DELETE RESTRICT;
ALTER TABLE navigation_menu_tb    ADD CONSTRAINT fk_menu_parent          FOREIGN KEY (parent_id)     REFERENCES navigation_menu_tb (id)  ON DELETE RESTRICT;

ALTER TABLE user_roles_tb         ADD CONSTRAINT fk_user_roles_user      FOREIGN KEY (user_id)       REFERENCES user_tb (id)             ON DELETE CASCADE;
ALTER TABLE user_roles_tb         ADD CONSTRAINT fk_user_roles_role      FOREIGN KEY (role_id)       REFERENCES role_tb (id)             ON DELETE RESTRICT;

ALTER TABLE user_departments_tb   ADD CONSTRAINT fk_user_dept_user       FOREIGN KEY (user_id)       REFERENCES user_tb (id)             ON DELETE CASCADE;
ALTER TABLE user_departments_tb   ADD CONSTRAINT fk_user_dept_dept       FOREIGN KEY (dept_id)       REFERENCES department_tb (id)       ON DELETE RESTRICT;

ALTER TABLE role_permission_tb    ADD CONSTRAINT fk_role_perm_role       FOREIGN KEY (role_id)       REFERENCES role_tb (id)             ON DELETE CASCADE;
ALTER TABLE role_permission_tb    ADD CONSTRAINT fk_role_perm_permission FOREIGN KEY (permission_id) REFERENCES permission_tb (id)       ON DELETE RESTRICT;

ALTER TABLE role_department_tb    ADD CONSTRAINT fk_role_dept_role       FOREIGN KEY (role_id)       REFERENCES role_tb (id)             ON DELETE CASCADE;
ALTER TABLE role_department_tb    ADD CONSTRAINT fk_role_dept_dept       FOREIGN KEY (department_id) REFERENCES department_tb (id)       ON DELETE RESTRICT;

ALTER TABLE action_permission_tb  ADD CONSTRAINT fk_act_perm_permission  FOREIGN KEY (permission_id) REFERENCES permission_tb (id)       ON DELETE CASCADE;
ALTER TABLE action_permission_tb  ADD CONSTRAINT fk_act_perm_action      FOREIGN KEY (action_id)     REFERENCES actions_tb (id)          ON DELETE RESTRICT;

ALTER TABLE user_nav_menu         ADD CONSTRAINT fk_user_nav_user        FOREIGN KEY (user_id)       REFERENCES user_tb (id)             ON DELETE CASCADE;
ALTER TABLE user_nav_menu         ADD CONSTRAINT fk_user_nav_menu        FOREIGN KEY (nav_id)        REFERENCES navigation_menu_tb (id)  ON DELETE CASCADE;

ALTER TABLE menu_registered_permissions_record ADD CONSTRAINT fk_menu_reg_menu FOREIGN KEY (menu_id)       REFERENCES navigation_menu_tb (id) ON DELETE CASCADE;
ALTER TABLE menu_registered_permissions_record ADD CONSTRAINT fk_menu_reg_perm FOREIGN KEY (permission_id) REFERENCES permission_tb (id)      ON DELETE CASCADE;

ALTER TABLE page_permission_approvals_record   ADD CONSTRAINT fk_approval_user FOREIGN KEY (user_id)       REFERENCES user_tb (id)            ON DELETE CASCADE;
ALTER TABLE page_permission_approvals_record   ADD CONSTRAINT fk_approval_menu FOREIGN KEY (menu_id)       REFERENCES navigation_menu_tb (id) ON DELETE CASCADE;
ALTER TABLE page_permission_approvals_record   ADD CONSTRAINT fk_approval_perm FOREIGN KEY (permission_id) REFERENCES permission_tb (id)      ON DELETE CASCADE;

ALTER TABLE user_refresh_token_tb ADD CONSTRAINT fk_refresh_user        FOREIGN KEY (user_id)       REFERENCES user_tb (id)             ON DELETE CASCADE;

-- user_login_log_tb intentionally has NO foreign key: an audit trail must
-- survive the deletion of the account it refers to.
*/
