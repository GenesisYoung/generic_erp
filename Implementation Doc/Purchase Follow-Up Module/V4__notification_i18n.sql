-- =============================================================================
--  Localization: notifications shown in each reader's language.
--  Runs after V3__followup_records.sql. Re-runnable (each column is added only when missing).
--
--  Hibernate (ddl-auto: update) also adds these columns from the entity
--  (Notification); this statement is for environments where ddl-auto is off.
--
--  title_i18n / content_i18n hold a message key with its arguments as JSON,
--  e.g. {"key":"followup.notify.dueTitle","args":[3]}. The backend renders them
--  in the language the frontend sends (Accept-Language). Rows created before
--  this change keep NULL here and show the stored title/content unchanged.
-- =============================================================================

SET @db := DATABASE();

SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'notification_tb' AND COLUMN_NAME = 'title_i18n') = 0,
               'ALTER TABLE notification_tb ADD COLUMN title_i18n VARCHAR(1000) NULL AFTER content',
               'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := IF((SELECT COUNT(*) FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = @db AND TABLE_NAME = 'notification_tb' AND COLUMN_NAME = 'content_i18n') = 0,
               'ALTER TABLE notification_tb ADD COLUMN content_i18n VARCHAR(4000) NULL AFTER title_i18n',
               'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
