-- =============================================================================
--  Generic ERP - database initialization (schema + required seed data)
--  Target: MySQL 8.4 (8.0+ works), utf8mb4. Generated 2026-11-28 from main.
--
--  WHAT IT CONTAINS
--    * Every table and view the backend uses (Hibernate entities + the
--      columns only the SQL migrations add, e.g. permission_tb.permission_name).
--    * Seed data from the migrations in "Implementation Doc/**/V*.sql" and
--      src/main/resources/db/*.sql, applied in history order:
--        actions, roles (ROOT/ADMIN/USER/pmc/purchaser), department "Head Office",
--        all menu./API permission codes, sidebar menus (routes match the Vue
--        router), ROOT role permissions, follow-up rule defaults, order audit
--        rule, units of measure, and the root account.
--    * Not included: demo products (Product Module/V2 seed), QA accounts,
--      and the old order_assessment_* tables in db/update.sql (no code uses them).
--
--  HOW IT RUNS
--    docker-compose mounts deploy/db/init into /docker-entrypoint-initdb.d, so
--    MySQL runs this file once, on the first start with an EMPTY data volume.
--    Manual run:  mysql -u root -p generic_erp < 01-schema-and-seed.sql
--    It has no DROP TABLE: on a database that already has tables it stops at
--    the first CREATE TABLE instead of deleting anything.
--
--  LOGIN AFTER INIT
--      username: root      password: ChangeMe@2026
--  >>> Change this password right after the first login. <<<
--
--  LATER SCHEMA CHANGES
--    The backend runs with spring.jpa.hibernate.ddl-auto=update, so new
--    entity columns/tables are added on startup. New seed rows (menus,
--    permissions) still need their own migration script.
-- =============================================================================


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `action_permission_tb` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `permission_id` bigint NOT NULL,
  `action_id` bigint NOT NULL,
  `create_date` date DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_action_permission` (`permission_id`,`action_id`),
  KEY `idx_action_perm_action` (`action_id`)
) ENGINE=InnoDB AUTO_INCREMENT=32 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `action_permission_tb` WRITE;
/*!40000 ALTER TABLE `action_permission_tb` DISABLE KEYS */;
INSERT INTO `action_permission_tb` VALUES (1,1,3,'2026-11-28'),(2,1,2,'2026-11-28'),(3,1,4,'2026-11-28'),(4,1,1,'2026-11-28'),(5,3,3,'2026-11-28'),(6,3,2,'2026-11-28'),(7,3,4,'2026-11-28'),(8,3,1,'2026-11-28'),(9,5,3,'2026-11-28'),(10,5,2,'2026-11-28'),(11,5,4,'2026-11-28'),(12,5,1,'2026-11-28'),(13,6,3,'2026-11-28'),(14,6,2,'2026-11-28'),(15,6,4,'2026-11-28'),(16,6,1,'2026-11-28'),(17,4,3,'2026-11-28'),(18,4,2,'2026-11-28'),(19,4,4,'2026-11-28'),(20,4,1,'2026-11-28'),(21,2,3,'2026-11-28'),(22,2,2,'2026-11-28'),(23,2,4,'2026-11-28'),(24,2,1,'2026-11-28');
/*!40000 ALTER TABLE `action_permission_tb` ENABLE KEYS */;
UNLOCK TABLES;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `actions_tb` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `action_name` varchar(50) NOT NULL,
  `create_date` date DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_action_name` (`action_name`)
) ENGINE=InnoDB AUTO_INCREMENT=100 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `actions_tb` WRITE;
/*!40000 ALTER TABLE `actions_tb` DISABLE KEYS */;
INSERT INTO `actions_tb` VALUES (1,'CREATE','2026-11-28'),(2,'READ','2026-11-28'),(3,'UPDATE','2026-11-28'),(4,'DELETE','2026-11-28');
/*!40000 ALTER TABLE `actions_tb` ENABLE KEYS */;
UNLOCK TABLES;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `appendix_tb` (
  `appendix_id` bigint NOT NULL AUTO_INCREMENT,
  `storage_key` varchar(64) NOT NULL,
  `path` varchar(512) DEFAULT NULL,
  `file_name` varchar(128) NOT NULL,
  `file_exten` varchar(8) NOT NULL,
  `mime_type` varchar(128) DEFAULT NULL,
  `byte_size` bigint DEFAULT NULL,
  `active` tinyint(1) NOT NULL DEFAULT '1',
  `created_by` bigint NOT NULL,
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_by` bigint DEFAULT NULL,
  `update_time` datetime(6) DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`appendix_id`),
  UNIQUE KEY `uk_appendix_storage_key` (`storage_key`),
  UNIQUE KEY `idx_appendix_storage_key` (`storage_key`),
  KEY `idx_appendix_file_name` (`file_name`),
  CONSTRAINT `ck_appendix_size` CHECK (((`byte_size` is null) or (`byte_size` >= 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `appendix_tb` WRITE;
/*!40000 ALTER TABLE `appendix_tb` DISABLE KEYS */;
/*!40000 ALTER TABLE `appendix_tb` ENABLE KEYS */;
UNLOCK TABLES;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `attribute_def_tb` (
  `attr_def_id` bigint NOT NULL AUTO_INCREMENT,
  `attribute_code` varchar(64) NOT NULL,
  `display_name` varchar(128) NOT NULL,
  `value_type` enum('TEXT','NUMBER','ENUM','BOOL') NOT NULL DEFAULT 'TEXT',
  `uom_id` bigint DEFAULT NULL,
  `is_variant_axis` tinyint(1) NOT NULL DEFAULT '0',
  `active` tinyint(1) NOT NULL DEFAULT '1',
  `created_by` bigint NOT NULL,
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_by` bigint DEFAULT NULL,
  `update_time` datetime(6) DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`attr_def_id`),
  UNIQUE KEY `uk_attr_def_code` (`attribute_code`),
  UNIQUE KEY `idx_attribute_code` (`attribute_code`),
  KEY `idx_attr_def_axis` (`is_variant_axis`,`active`),
  KEY `fk_attr_def_uom` (`uom_id`),
  CONSTRAINT `fk_attr_def_uom` FOREIGN KEY (`uom_id`) REFERENCES `uom_tb` (`uom_id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `attribute_def_tb` WRITE;
/*!40000 ALTER TABLE `attribute_def_tb` DISABLE KEYS */;
/*!40000 ALTER TABLE `attribute_def_tb` ENABLE KEYS */;
UNLOCK TABLES;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `audit_log_tb` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `action` varchar(20) NOT NULL,
  `client_ip` varchar(45) DEFAULT NULL,
  `create_date` date DEFAULT NULL,
  `description` varchar(255) DEFAULT NULL,
  `method_name` varchar(255) DEFAULT NULL,
  `module` varchar(50) NOT NULL,
  `operate_time` datetime(6) NOT NULL,
  `operator_id` bigint DEFAULT NULL,
  `operator_name` varchar(50) DEFAULT NULL,
  `target_id` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `audit_log_tb` WRITE;
/*!40000 ALTER TABLE `audit_log_tb` DISABLE KEYS */;
/*!40000 ALTER TABLE `audit_log_tb` ENABLE KEYS */;
UNLOCK TABLES;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `category_tb` (
  `cate_id` bigint NOT NULL AUTO_INCREMENT,
  `cate_name` varchar(50) NOT NULL,
  `cate_abbr` varchar(8) DEFAULT NULL,
  `description` varchar(2000) DEFAULT NULL,
  `parent_id` bigint DEFAULT NULL,
  `path_cache` varchar(512) DEFAULT NULL,
  `depth` int NOT NULL DEFAULT '0',
  `active` tinyint(1) NOT NULL DEFAULT '1',
  `created_by` bigint NOT NULL,
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_by` bigint DEFAULT NULL,
  `update_time` datetime(6) DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`cate_id`),
  UNIQUE KEY `uk_category_parent_name` (`parent_id`,`cate_name`),
  KEY `idx_category_parent` (`parent_id`),
  KEY `idx_category_path` (`path_cache`(255)),
  KEY `idx_category_abbr` (`cate_abbr`),
  KEY `idx_category_name` (`cate_name`),
  CONSTRAINT `fk_category_parent` FOREIGN KEY (`parent_id`) REFERENCES `category_tb` (`cate_id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_category_depth` CHECK ((`depth` >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `category_tb` WRITE;
/*!40000 ALTER TABLE `category_tb` DISABLE KEYS */;
/*!40000 ALTER TABLE `category_tb` ENABLE KEYS */;
UNLOCK TABLES;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `department_tb` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `dept_name` varchar(50) NOT NULL,
  `val` int DEFAULT NULL,
  `parent_id` bigint DEFAULT NULL,
  `create_date` date DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dept_name` (`dept_name`),
  KEY `idx_dept_parent` (`parent_id`)
) ENGINE=InnoDB AUTO_INCREMENT=100 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `department_tb` WRITE;
/*!40000 ALTER TABLE `department_tb` DISABLE KEYS */;
INSERT INTO `department_tb` VALUES (1,'Head Office',1,NULL,'2026-11-28');
/*!40000 ALTER TABLE `department_tb` ENABLE KEYS */;
UNLOCK TABLES;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `file_chunk_tb` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `appendix_id` bigint NOT NULL,
  `chuck_idx` int DEFAULT NULL,
  `create_time` datetime(6) DEFAULT NULL,
  `finished` bit(1) DEFAULT NULL,
  `operator_id` bigint DEFAULT NULL,
  `standard_size` int DEFAULT NULL,
  `update_time` datetime(6) DEFAULT NULL,
  `uuid` varchar(36) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `file_chunk_tb_uuid_index` (`uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `file_chunk_tb` WRITE;
/*!40000 ALTER TABLE `file_chunk_tb` DISABLE KEYS */;
/*!40000 ALTER TABLE `file_chunk_tb` ENABLE KEYS */;
UNLOCK TABLES;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `followup_plan_group_tb` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `is_active` bit(1) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `followup_rule_id` bigint DEFAULT NULL,
  `group_name` varchar(100) NOT NULL,
  `note` varchar(200) DEFAULT NULL,
  `is_separate` bit(1) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `followup_plan_group_tb` WRITE;
/*!40000 ALTER TABLE `followup_plan_group_tb` DISABLE KEYS */;
/*!40000 ALTER TABLE `followup_plan_group_tb` ENABLE KEYS */;
UNLOCK TABLES;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `followup_plan_type_tb` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `group_id` bigint DEFAULT NULL,
  `last_seen_at` datetime(6) DEFAULT NULL,
  `type_code` varchar(50) NOT NULL,
  `type_name` varchar(100) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_plan_type_code` (`type_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `followup_plan_type_tb` WRITE;
/*!40000 ALTER TABLE `followup_plan_type_tb` DISABLE KEYS */;
/*!40000 ALTER TABLE `followup_plan_type_tb` ENABLE KEYS */;
UNLOCK TABLES;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `followup_rule_day_tb` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `day_no` int NOT NULL,
  `rule_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_rule_day_rule` (`rule_id`,`day_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `followup_rule_day_tb` WRITE;
/*!40000 ALTER TABLE `followup_rule_day_tb` DISABLE KEYS */;
/*!40000 ALTER TABLE `followup_rule_day_tb` ENABLE KEYS */;
UNLOCK TABLES;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `followup_rule_stage_tb` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `checks_per_period` int NOT NULL,
  `end_day` int NOT NULL,
  `period_days` int NOT NULL,
  `rule_id` bigint NOT NULL,
  `seq` int NOT NULL,
  `stage` enum('DAILY','INITIAL','INTENSIFIED','NOTIFY','OVERDUE') NOT NULL,
  `start_day` int NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_rule_stage_rule` (`rule_id`,`seq`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `followup_rule_stage_tb` WRITE;
/*!40000 ALTER TABLE `followup_rule_stage_tb` DISABLE KEYS */;
INSERT INTO `followup_rule_stage_tb` VALUES (1,2,14,7,1,1,'INITIAL',1),(2,1,21,2,1,2,'INTENSIFIED',15),(3,1,28,1,1,3,'DAILY',22);
/*!40000 ALTER TABLE `followup_rule_stage_tb` ENABLE KEYS */;
UNLOCK TABLES;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `followup_rule_tb` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `is_active` bit(1) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `is_default` bit(1) NOT NULL,
  `material_note` varchar(200) DEFAULT NULL,
  `overdue_period_days` int DEFAULT NULL,
  `production_days` int DEFAULT NULL,
  `rule_name` varchar(100) NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `followup_rule_tb` WRITE;
/*!40000 ALTER TABLE `followup_rule_tb` DISABLE KEYS */;
INSERT INTO `followup_rule_tb` VALUES (1,0x01,'2026-11-28 01:18:22.314746',0x01,NULL,1,NULL,'Default: 2x/week weeks 1-2, every 2 days week 3, daily week 4',NULL);
/*!40000 ALTER TABLE `followup_rule_tb` ENABLE KEYS */;
UNLOCK TABLES;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `legacy_customer_order_tb` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `customer_code` varchar(50) DEFAULT NULL,
  `customer_name` varchar(200) DEFAULT NULL,
  `delivery_date` date DEFAULT NULL,
  `first_seen_at` datetime(6) NOT NULL,
  `last_synced_at` datetime(6) NOT NULL,
  `legacy_audited` bit(1) NOT NULL,
  `legacy_closed` bit(1) NOT NULL,
  `legacy_id` varchar(32) NOT NULL,
  `line_count` int DEFAULT NULL,
  `order_date` date NOT NULL,
  `order_no` varchar(50) NOT NULL,
  `sales_code` varchar(50) DEFAULT NULL,
  `total_qty` decimal(18,4) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_customer_order_legacy_id` (`legacy_id`),
  KEY `idx_customer_order_no` (`order_no`),
  KEY `idx_customer_order_date` (`order_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `legacy_customer_order_tb` WRITE;
/*!40000 ALTER TABLE `legacy_customer_order_tb` DISABLE KEYS */;
/*!40000 ALTER TABLE `legacy_customer_order_tb` ENABLE KEYS */;
UNLOCK TABLES;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `legacy_po_customer_link_tb` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `customer_line_legacy_id` varchar(32) NOT NULL,
  `customer_order_legacy_id` varchar(32) NOT NULL,
  `customer_order_no` varchar(50) NOT NULL,
  `first_seen_at` datetime(6) NOT NULL,
  `po_date` date DEFAULT NULL,
  `po_no` varchar(50) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_po_customer_link` (`customer_line_legacy_id`,`po_no`),
  KEY `idx_po_customer_link_order` (`customer_order_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `legacy_po_customer_link_tb` WRITE;
/*!40000 ALTER TABLE `legacy_po_customer_link_tb` DISABLE KEYS */;
/*!40000 ALTER TABLE `legacy_po_customer_link_tb` ENABLE KEYS */;
UNLOCK TABLES;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `legacy_sync_state_tb` (
  `sync_key` varchar(50) NOT NULL,
  `last_message` varchar(1000) DEFAULT NULL,
  `last_row_count` int DEFAULT NULL,
  `last_run_at` datetime(6) DEFAULT NULL,
  `last_source_updated_at` datetime(6) DEFAULT NULL,
  `last_status` varchar(16) DEFAULT NULL,
  PRIMARY KEY (`sync_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `legacy_sync_state_tb` WRITE;
/*!40000 ALTER TABLE `legacy_sync_state_tb` DISABLE KEYS */;
/*!40000 ALTER TABLE `legacy_sync_state_tb` ENABLE KEYS */;
UNLOCK TABLES;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `legacy_user_map_tb` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `legacy_code` varchar(50) NOT NULL,
  `source_system` varchar(30) NOT NULL,
  `user_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_legacy_user_code` (`source_system`,`legacy_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `legacy_user_map_tb` WRITE;
/*!40000 ALTER TABLE `legacy_user_map_tb` DISABLE KEYS */;
/*!40000 ALTER TABLE `legacy_user_map_tb` ENABLE KEYS */;
UNLOCK TABLES;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `menu_registered_permissions_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `menu_id` bigint NOT NULL,
  `permission_id` bigint NOT NULL,
  `create_date` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `permission_enabled` tinyint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_menu_registered` (`menu_id`,`permission_id`),
  KEY `idx_menu_reg_permission` (`permission_id`)
) ENGINE=InnoDB AUTO_INCREMENT=26 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `menu_registered_permissions_record` WRITE;
/*!40000 ALTER TABLE `menu_registered_permissions_record` DISABLE KEYS */;
INSERT INTO `menu_registered_permissions_record` VALUES (1,1,1,'2026-11-28 01:18:21.586464',1),(2,3,3,'2026-11-28 01:18:21.586464',1),(4,6,6,'2026-11-28 01:18:21.586464',1),(5,4,4,'2026-11-28 01:18:21.586464',1),(6,2,2,'2026-11-28 01:18:21.586464',1),(8,100,149,'2026-11-28 01:18:22.000000',1),(9,100,148,'2026-11-28 01:18:22.000000',1),(11,111,136,'2026-11-28 01:18:23.000000',1),(12,111,142,'2026-11-28 01:18:23.000000',1),(13,111,141,'2026-11-28 01:18:23.000000',1),(14,111,140,'2026-11-28 01:18:23.000000',1),(15,111,134,'2026-11-28 01:18:23.000000',1),(16,111,137,'2026-11-28 01:18:23.000000',1),(17,111,143,'2026-11-28 01:18:23.000000',1),(18,111,133,'2026-11-28 01:18:23.000000',1),(19,111,145,'2026-11-28 01:18:23.000000',1),(20,111,144,'2026-11-28 01:18:23.000000',1),(21,111,138,'2026-11-28 01:18:23.000000',1),(22,111,135,'2026-11-28 01:18:23.000000',1),(23,111,139,'2026-11-28 01:18:23.000000',1),(24,111,147,'2026-11-28 01:18:23.000000',1),(25,111,146,'2026-11-28 01:18:23.000000',1);
/*!40000 ALTER TABLE `menu_registered_permissions_record` ENABLE KEYS */;
UNLOCK TABLES;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `navigation_menu_tb` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `parent_id` bigint DEFAULT NULL,
  `title_key` varchar(50) NOT NULL,
  `icon` varchar(50) NOT NULL,
  `route` varchar(50) NOT NULL,
  `color` varchar(20) NOT NULL,
  `permission_id` varchar(255) DEFAULT NULL,
  `is_enabled` bit(1) NOT NULL DEFAULT b'1',
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_menu_title_key` (`title_key`),
  KEY `idx_menu_parent` (`parent_id`),
  KEY `idx_menu_route` (`route`)
) ENGINE=InnoDB AUTO_INCREMENT=112 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `navigation_menu_tb` WRITE;
/*!40000 ALTER TABLE `navigation_menu_tb` DISABLE KEYS */;
INSERT INTO `navigation_menu_tb` VALUES (1,NULL,'home','mdi-view-dashboard','/','indigo','1',0x01,'2026-11-28 01:18:21.571412'),(2,NULL,'userManagement','mdi-account-group','/manage/users','indigo','2',0x01,'2026-11-28 01:18:21.571412'),(3,NULL,'departmentManagement','mdi-office-building-cog','/manage/departments','indigo','3',0x01,'2026-11-28 01:18:21.573483'),(4,NULL,'roleManagement','mdi-shield-account','/permission/management','indigo','4',0x01,'2026-11-28 01:18:21.573483'),(6,NULL,'permissionManagement','mdi-key-chain','/user/permission/allocation','indigo','6',0x01,'2026-11-28 01:18:21.571412'),(100,NULL,'evidenceManagement','mdi-file-document-outline','/pmc/evidence','indigo','148',0x01,'2026-11-28 01:18:22.000000'),(101,NULL,'followUpTodo','mdi-phone-check-outline','/purchase/followups','teal','158',0x01,'2026-11-28 01:18:22.000000'),(102,NULL,'customerOrderReview','mdi-cart-arrow-down','/pmc/customer-orders','indigo','168',0x01,'2026-11-28 01:18:22.000000'),(103,NULL,'placementTasks','mdi-cart-arrow-right','/purchase/placements','deep-orange','158',0x01,'2026-11-28 01:18:22.000000'),(104,NULL,'purchaseExceptions','mdi-alert-octagon-outline','/purchase/exceptions','red','157',0x01,'2026-11-28 01:18:22.000000'),(105,NULL,'pmcDashboard','mdi-view-dashboard-outline','/pmc/dashboard','indigo','150',0x01,'2026-11-28 01:18:22.000000'),(106,NULL,'purchaseDashboard','mdi-view-dashboard-variant-outline','/purchase/dashboard','deep-orange','151',0x01,'2026-11-28 01:18:22.000000'),(108,NULL,'followUpTemplates','mdi-calendar-clock-outline','/purchase/followup-templates','teal','171',0x01,'2026-11-28 01:18:22.000000'),(109,NULL,'followUpPlanGroups','mdi-shape-outline','/purchase/followup-plan-groups','teal','171',0x01,'2026-11-28 01:18:23.000000'),(110,NULL,'quotations','mdi-calculator-variant-outline','/purchase/quotations','teal','174',0x01,'2026-11-28 01:18:23.000000'),(111,NULL,'orderAuditTaskList','mdi-clipboard-check-outline','/pmc/order-audit-task','indigo','133',0x01,'2026-11-28 01:18:23.784084');
/*!40000 ALTER TABLE `navigation_menu_tb` ENABLE KEYS */;
UNLOCK TABLES;
SET @saved_cs_client     = @@character_set_client;
/*!50503 SET character_set_client = utf8mb4 */;
/*!50001 CREATE VIEW `navigation_permission_view` AS SELECT 
 1 AS `id`,
 1 AS `nav_id`,
 1 AS `nav_key`,
 1 AS `route`,
 1 AS `u_id`,
 1 AS `u_name`,
 1 AS `p_id`,
 1 AS `p_name`,
 1 AS `p_val`*/;
SET character_set_client = @saved_cs_client;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `notification_tb` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `biz_id` bigint DEFAULT NULL,
  `biz_type` varchar(50) DEFAULT NULL,
  `content` varchar(1000) DEFAULT NULL,
  `content_i18n` varchar(4000) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `priority` enum('ERGENT','HIGH','NORMAL') NOT NULL,
  `read_at` datetime(6) DEFAULT NULL,
  `recipient_id` bigint NOT NULL,
  `title` varchar(200) NOT NULL,
  `title_i18n` varchar(1000) DEFAULT NULL,
  `type` varchar(32) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_notification_recipient_read` (`recipient_id`,`read_at`),
  KEY `idx_notification_biz` (`biz_type`,`biz_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `notification_tb` WRITE;
/*!40000 ALTER TABLE `notification_tb` DISABLE KEYS */;
/*!40000 ALTER TABLE `notification_tb` ENABLE KEYS */;
UNLOCK TABLES;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `page_permission_approvals_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `permission_id` bigint NOT NULL,
  `menu_id` bigint NOT NULL,
  `create_date` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `permission_enabled` tinyint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_page_approval` (`user_id`,`menu_id`,`permission_id`),
  KEY `idx_approval_user` (`user_id`),
  KEY `idx_approval_menu_perm` (`menu_id`,`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `page_permission_approvals_record` WRITE;
/*!40000 ALTER TABLE `page_permission_approvals_record` DISABLE KEYS */;
/*!40000 ALTER TABLE `page_permission_approvals_record` ENABLE KEYS */;
UNLOCK TABLES;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `permission_tb` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `permission_code` varchar(50) NOT NULL,
  `is_enabled` int NOT NULL DEFAULT '1',
  `create_date` date DEFAULT NULL,
  `permission_name` varchar(50) DEFAULT NULL,
  `val` bigint DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_permission_code` (`permission_code`),
  KEY `idx_permission_enabled` (`is_enabled`,`permission_code`)
) ENGINE=InnoDB AUTO_INCREMENT=176 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `permission_tb` WRITE;
/*!40000 ALTER TABLE `permission_tb` DISABLE KEYS */;
INSERT INTO `permission_tb` VALUES (1,'menu.dashboard',1,'2026-11-28','Dashboard',1),(2,'menu.userManagement',1,'2026-11-28','User Management',2),(3,'menu.departmentManagement',1,'2026-11-28','Department Management',3),(4,'menu.roleManagement',1,'2026-11-28','Role Management',4),(5,'menu.menuManagement',1,'2026-11-28','Menu Management',5),(6,'menu.permissionManagement',1,'2026-11-28','Permission Management',6),(100,'product',1,'2026-11-28',NULL,NULL),(101,'menu.userManagement.delete',1,'2026-11-28',NULL,NULL),(102,'menu.userManagement.update',1,'2026-11-28',NULL,NULL),(103,'menu.userManagement.create',1,'2026-11-28',NULL,NULL),(104,'menu.userManagement.read',1,'2026-11-28',NULL,NULL),(105,'menu.departmentManagement.delete',1,'2026-11-28',NULL,NULL),(106,'menu.departmentManagement.update',1,'2026-11-28',NULL,NULL),(107,'menu.departmentManagement.create',1,'2026-11-28',NULL,NULL),(108,'menu.departmentManagement.read',1,'2026-11-28',NULL,NULL),(109,'menu.roleManagement.delete',1,'2026-11-28',NULL,NULL),(110,'menu.roleManagement.update',1,'2026-11-28',NULL,NULL),(111,'menu.roleManagement.create',1,'2026-11-28',NULL,NULL),(112,'menu.roleManagement.read',1,'2026-11-28',NULL,NULL),(113,'menu.menuManagement.delete',1,'2026-11-28',NULL,NULL),(114,'menu.menuManagement.update',1,'2026-11-28',NULL,NULL),(115,'menu.menuManagement.create',1,'2026-11-28',NULL,NULL),(116,'menu.menuManagement.read',1,'2026-11-28',NULL,NULL),(117,'menu.permissionManagement.delete',1,'2026-11-28',NULL,NULL),(118,'menu.permissionManagement.update',1,'2026-11-28',NULL,NULL),(119,'menu.permissionManagement.create',1,'2026-11-28',NULL,NULL),(120,'menu.permissionManagement.read',1,'2026-11-28',NULL,NULL),(121,'product.delete',1,'2026-11-28',NULL,NULL),(122,'product.update',1,'2026-11-28',NULL,NULL),(123,'product.create',1,'2026-11-28',NULL,NULL),(124,'product.read',1,'2026-11-28',NULL,NULL),(132,'product.import',1,'2026-11-28',NULL,NULL),(133,'menu.order-audit-task.read',1,'2026-11-28',NULL,NULL),(134,'menu.order-audit-task.create',1,'2026-11-28',NULL,NULL),(135,'menu.order-audit-task.update',1,'2026-11-28',NULL,NULL),(136,'menu.order-audit-task.accept',1,'2026-11-28',NULL,NULL),(137,'menu.order-audit-task.execute',1,'2026-11-28',NULL,NULL),(138,'menu.order-audit-task.submit',1,'2026-11-28',NULL,NULL),(139,'menu.order-audit-task.verify',1,'2026-11-28',NULL,NULL),(140,'menu.order-audit-task.confirm',1,'2026-11-28',NULL,NULL),(141,'menu.order-audit-task.change.request',1,'2026-11-28',NULL,NULL),(142,'menu.order-audit-task.approve',1,'2026-11-28',NULL,NULL),(143,'menu.order-audit-task.export',1,'2026-11-28',NULL,NULL),(144,'menu.order-audit-task.rules.configure',1,'2026-11-28',NULL,NULL),(145,'menu.order-audit-task.rules.approve',1,'2026-11-28',NULL,NULL),(146,'pmc.order-audit.scope.team',1,'2026-11-28',NULL,NULL),(147,'pmc.order-audit.scope.all',1,'2026-11-28',NULL,NULL),(148,'menu.evidence.read',1,'2026-11-28',NULL,NULL),(149,'menu.evidence.create',1,'2026-11-28',NULL,NULL),(150,'menu.pmcDashboard',1,'2026-11-28',NULL,NULL),(151,'menu.purchaseDashboard',1,'2026-11-28',NULL,NULL),(152,'menu.purchaseOrder',1,'2026-11-28',NULL,NULL),(153,'menu.purchaseException',1,'2026-11-28',NULL,NULL),(157,'menu.purchaseException.read',1,'2026-11-28',NULL,NULL),(158,'menu.purchaseOrder.read',1,'2026-11-28',NULL,NULL),(159,'menu.purchaseException.create',1,'2026-11-28',NULL,NULL),(160,'menu.purchaseOrder.create',1,'2026-11-28',NULL,NULL),(161,'menu.purchaseException.update',1,'2026-11-28',NULL,NULL),(162,'menu.purchaseOrder.update',1,'2026-11-28',NULL,NULL),(164,'data.purchaseOrder.all',1,'2026-11-28',NULL,NULL),(165,'data.purchaseException.all',1,'2026-11-28',NULL,NULL),(167,'menu.customerOrder',1,'2026-11-28','Customer Orders to Audit',NULL),(168,'menu.customerOrder.read',1,'2026-11-28',NULL,NULL),(169,'menu.customerOrder.update',1,'2026-11-28',NULL,NULL),(170,'menu.followupTemplate',1,'2026-11-28',NULL,NULL),(171,'menu.followupTemplate.read',1,'2026-11-28',NULL,NULL),(172,'menu.followupTemplate.update',1,'2026-11-28',NULL,NULL),(173,'menu.quotation',1,'2026-11-28',NULL,NULL),(174,'menu.quotation.read',1,'2026-11-28',NULL,NULL),(175,'menu.quotation.update',1,'2026-11-28',NULL,NULL);
/*!40000 ALTER TABLE `permission_tb` ENABLE KEYS */;
UNLOCK TABLES;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pmc_order_audit_evidence` (
  `id` varchar(36) NOT NULL,
  `byte_size` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `created_by` varchar(20) NOT NULL,
  `file_name` varchar(128) NOT NULL,
  `purpose` varchar(1000) NOT NULL,
  `request_hash` varchar(64) NOT NULL,
  `storage_key` varchar(64) NOT NULL,
  `task_id` varchar(36) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `ix_pmc_evidence_task` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `pmc_order_audit_evidence` WRITE;
/*!40000 ALTER TABLE `pmc_order_audit_evidence` DISABLE KEYS */;
/*!40000 ALTER TABLE `pmc_order_audit_evidence` ENABLE KEYS */;
UNLOCK TABLES;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pmc_order_audit_operation` (
  `id` varchar(160) NOT NULL,
  `request_hash` varchar(64) NOT NULL,
  `response` longtext NOT NULL,
  `task_id` varchar(36) DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `pmc_order_audit_operation` WRITE;
/*!40000 ALTER TABLE `pmc_order_audit_operation` DISABLE KEYS */;
/*!40000 ALTER TABLE `pmc_order_audit_operation` ENABLE KEYS */;
UNLOCK TABLES;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pmc_order_audit_rule` (
  `id` int NOT NULL,
  `document` longtext NOT NULL,
  `row_version` bigint NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `pmc_order_audit_rule` WRITE;
/*!40000 ALTER TABLE `pmc_order_audit_rule` DISABLE KEYS */;
INSERT INTO `pmc_order_audit_rule` VALUES (1,'{\"version\":0,\"requestedBy\":null,\"approvedBy\":null,\"approvedAt\":null,\"draft\":null,\"approved\":null,\"history\":[]}',0);
/*!40000 ALTER TABLE `pmc_order_audit_rule` ENABLE KEYS */;
UNLOCK TABLES;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pmc_order_audit_task` (
  `id` varchar(36) NOT NULL,
  `accepted_at` datetime(6) DEFAULT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  `created_by` varchar(20) NOT NULL,
  `department_id` varchar(20) DEFAULT NULL,
  `document` longtext NOT NULL,
  `effective_due_at` datetime(6) DEFAULT NULL,
  `next_owner_user_id` varchar(20) DEFAULT NULL,
  `order_number` varchar(50) NOT NULL,
  `owner_user_id` varchar(20) DEFAULT NULL,
  `review_round` int NOT NULL,
  `row_version` bigint NOT NULL,
  `status` varchar(24) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_pmc_order_round` (`order_number`,`review_round`),
  KEY `ix_pmc_audit_due` (`status`,`effective_due_at`),
  KEY `ix_pmc_audit_owner` (`owner_user_id`),
  KEY `ix_pmc_audit_dept` (`department_id`),
  KEY `ix_pmc_audit_creator` (`created_by`),
  KEY `ix_pmc_audit_next_owner` (`next_owner_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `pmc_order_audit_task` WRITE;
/*!40000 ALTER TABLE `pmc_order_audit_task` DISABLE KEYS */;
/*!40000 ALTER TABLE `pmc_order_audit_task` ENABLE KEYS */;
UNLOCK TABLES;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `pmc_personal_evidence` (
  `id` varchar(36) NOT NULL,
  `byte_size` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `created_by` varchar(20) NOT NULL,
  `file_name` varchar(128) NOT NULL,
  `purpose` varchar(1000) NOT NULL,
  `request_hash` varchar(64) NOT NULL,
  `storage_key` varchar(64) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `ix_personal_evidence_owner` (`created_by`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `pmc_personal_evidence` WRITE;
/*!40000 ALTER TABLE `pmc_personal_evidence` DISABLE KEYS */;
/*!40000 ALTER TABLE `pmc_personal_evidence` ENABLE KEYS */;
UNLOCK TABLES;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `po_followup_checkpoint_tb` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `created_at` datetime(6) NOT NULL,
  `done_record_id` bigint DEFAULT NULL,
  `plan_group_id` bigint DEFAULT NULL,
  `purchase_order_id` bigint NOT NULL,
  `reminded_at` datetime(6) DEFAULT NULL,
  `seq` int NOT NULL,
  `stage` enum('DAILY','INITIAL','INTENSIFIED','NOTIFY','OVERDUE') NOT NULL,
  `status` enum('CANCELLED','DONE','MISSED','OPEN') NOT NULL,
  `window_end` date NOT NULL,
  `window_start` date NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_checkpoint_po` (`purchase_order_id`,`seq`),
  KEY `idx_checkpoint_po_group` (`purchase_order_id`,`plan_group_id`),
  KEY `idx_checkpoint_status_end` (`status`,`window_end`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `po_followup_checkpoint_tb` WRITE;
/*!40000 ALTER TABLE `po_followup_checkpoint_tb` DISABLE KEYS */;
/*!40000 ALTER TABLE `po_followup_checkpoint_tb` ENABLE KEYS */;
UNLOCK TABLES;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `po_followup_record_tb` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `checkpoint_id` bigint DEFAULT NULL,
  `followed_up_at` datetime(6) NOT NULL,
  `material_status` enum('AT_RISK','DELAYED','ON_TRACK') NOT NULL,
  `missing_order_found` bit(1) NOT NULL,
  `note` varchar(500) DEFAULT NULL,
  `plan_group_id` bigint DEFAULT NULL,
  `purchase_order_id` bigint NOT NULL,
  `recorded_by` bigint NOT NULL,
  `supplier_promised_date` date DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_followup_record_po` (`purchase_order_id`,`followed_up_at`),
  KEY `idx_followup_record_user` (`recorded_by`,`followed_up_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `po_followup_record_tb` WRITE;
/*!40000 ALTER TABLE `po_followup_record_tb` DISABLE KEYS */;
/*!40000 ALTER TABLE `po_followup_record_tb` ENABLE KEYS */;
UNLOCK TABLES;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `po_followup_track_tb` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `is_active` bit(1) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `followup_rule_id` bigint DEFAULT NULL,
  `plan_group_id` bigint NOT NULL,
  `purchase_order_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_track_po_group` (`purchase_order_id`,`plan_group_id`),
  KEY `idx_track_group` (`plan_group_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `po_followup_track_tb` WRITE;
/*!40000 ALTER TABLE `po_followup_track_tb` DISABLE KEYS */;
/*!40000 ALTER TABLE `po_followup_track_tb` ENABLE KEYS */;
UNLOCK TABLES;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `po_placement_task_tb` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `assigned_at` datetime(6) DEFAULT NULL,
  `assigned_by` bigint DEFAULT NULL,
  `assignee_id` bigint DEFAULT NULL,
  `audit_result` varchar(16) DEFAULT NULL,
  `audit_round` int DEFAULT NULL,
  `audit_task_id` varchar(36) DEFAULT NULL,
  `audit_verified_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `deadline_at` datetime(6) NOT NULL,
  `done_at` datetime(6) DEFAULT NULL,
  `done_by` bigint DEFAULT NULL,
  `done_po_no` varchar(50) DEFAULT NULL,
  `done_source` varchar(16) DEFAULT NULL,
  `order_no` varchar(50) NOT NULL,
  `order_type` enum('OTHER','PRODUCTION','SAMPLE') DEFAULT NULL,
  `overdue_notified_at` datetime(6) DEFAULT NULL,
  `reminded_at` datetime(6) DEFAULT NULL,
  `source` enum('ORDER_AUDIT','PURCHASE_APPLICATION','PURCHASE_PLAN') DEFAULT NULL,
  `source_no` varchar(50) DEFAULT NULL,
  `source_note` varchar(200) DEFAULT NULL,
  `started_at` datetime(6) DEFAULT NULL,
  `status` enum('DONE','OPEN','SKIPPED') NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_placement_audit_task` (`audit_task_id`),
  UNIQUE KEY `uq_placement_source_doc` (`source`,`source_no`,`order_no`),
  KEY `idx_placement_status_deadline` (`status`,`deadline_at`),
  KEY `idx_placement_assignee` (`assignee_id`,`status`),
  KEY `idx_placement_order_no` (`order_no`),
  KEY `idx_placement_type_status` (`order_type`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `po_placement_task_tb` WRITE;
/*!40000 ALTER TABLE `po_placement_task_tb` DISABLE KEYS */;
/*!40000 ALTER TABLE `po_placement_task_tb` ENABLE KEYS */;
UNLOCK TABLES;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_attribute_tb` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `variant_id` bigint NOT NULL,
  `attr_def_id` bigint NOT NULL,
  `attribute_value` varchar(255) NOT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  `active` tinyint(1) NOT NULL DEFAULT '1',
  `created_by` bigint NOT NULL,
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_by` bigint DEFAULT NULL,
  `update_time` datetime(6) DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_attr_variant_def` (`variant_id`,`attr_def_id`),
  UNIQUE KEY `uk_variant_attribute` (`variant_id`,`attr_def_id`),
  KEY `idx_attr_def` (`attr_def_id`),
  KEY `idx_product_attribute_variant` (`variant_id`),
  KEY `idx_product_attribute_definition` (`attr_def_id`),
  CONSTRAINT `fk_attr_definition` FOREIGN KEY (`attr_def_id`) REFERENCES `attribute_def_tb` (`attr_def_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_attr_variant` FOREIGN KEY (`variant_id`) REFERENCES `product_variant_tb` (`variant_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `product_attribute_tb` WRITE;
/*!40000 ALTER TABLE `product_attribute_tb` DISABLE KEYS */;
/*!40000 ALTER TABLE `product_attribute_tb` ENABLE KEYS */;
UNLOCK TABLES;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_audit_log_tb` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `change_group_id` char(36) NOT NULL,
  `operation` enum('CREATE','UPDATE','STATUS_CHANGE','DELETE') NOT NULL,
  `affected_table` varchar(64) NOT NULL,
  `affected_row` varchar(64) NOT NULL,
  `affected_column` varchar(64) NOT NULL,
  `before_val` text,
  `after_val` text,
  `edited_by` bigint NOT NULL,
  `edit_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `idx_audit_target` (`affected_table`,`affected_row`),
  KEY `idx_audit_group` (`change_group_id`),
  KEY `idx_audit_actor` (`edited_by`,`edit_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `product_audit_log_tb` WRITE;
/*!40000 ALTER TABLE `product_audit_log_tb` DISABLE KEYS */;
/*!40000 ALTER TABLE `product_audit_log_tb` ENABLE KEYS */;
UNLOCK TABLES;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_barcode_tb` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `variant_id` bigint NOT NULL,
  `barcode_value` varchar(64) NOT NULL,
  `barcode_type` enum('EAN13','EAN8','UPC_A','UPC_E','CODE128','QR') NOT NULL,
  `region` enum('US','EU','CN','JP') DEFAULT NULL,
  `is_primary` tinyint(1) NOT NULL DEFAULT '0',
  `active` tinyint(1) NOT NULL DEFAULT '1',
  `created_by` bigint NOT NULL,
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_by` bigint DEFAULT NULL,
  `update_time` datetime(6) DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_barcode_value` (`barcode_value`),
  UNIQUE KEY `idx_barcode_value` (`barcode_value`),
  KEY `idx_barcode_variant` (`variant_id`),
  KEY `idx_barcode_region` (`region`),
  CONSTRAINT `fk_barcode_variant` FOREIGN KEY (`variant_id`) REFERENCES `product_variant_tb` (`variant_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `product_barcode_tb` WRITE;
/*!40000 ALTER TABLE `product_barcode_tb` DISABLE KEYS */;
/*!40000 ALTER TABLE `product_barcode_tb` ENABLE KEYS */;
UNLOCK TABLES;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_category_rel_tb` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `product_id` bigint NOT NULL,
  `cate_id` bigint NOT NULL,
  `is_primary` tinyint(1) NOT NULL DEFAULT '0',
  `active` tinyint(1) NOT NULL DEFAULT '1',
  `created_by` bigint NOT NULL,
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_by` bigint DEFAULT NULL,
  `update_time` datetime(6) DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_cate` (`product_id`,`cate_id`),
  UNIQUE KEY `uk_product_category` (`product_id`,`cate_id`),
  KEY `idx_pc_cate_product` (`cate_id`,`product_id`),
  KEY `idx_product_category_product` (`product_id`),
  KEY `idx_product_category_category` (`cate_id`),
  CONSTRAINT `fk_pc_category` FOREIGN KEY (`cate_id`) REFERENCES `category_tb` (`cate_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_pc_product` FOREIGN KEY (`product_id`) REFERENCES `product_tb` (`product_id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `product_category_rel_tb` WRITE;
/*!40000 ALTER TABLE `product_category_rel_tb` DISABLE KEYS */;
/*!40000 ALTER TABLE `product_category_rel_tb` ENABLE KEYS */;
UNLOCK TABLES;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_image_tb` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `variant_id` bigint NOT NULL,
  `appendix_id` bigint NOT NULL,
  `alt_text` varchar(255) DEFAULT NULL,
  `sort_order` int NOT NULL DEFAULT '0',
  `is_primary` tinyint(1) NOT NULL DEFAULT '0',
  `created_by` bigint NOT NULL,
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_by` bigint DEFAULT NULL,
  `update_time` datetime(6) DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_image_variant_file` (`variant_id`,`appendix_id`),
  UNIQUE KEY `uk_variant_appendix` (`variant_id`,`appendix_id`),
  KEY `idx_image_variant_order` (`variant_id`,`sort_order`),
  KEY `idx_image_appendix` (`appendix_id`),
  KEY `idx_product_image_variant` (`variant_id`),
  KEY `idx_product_image_appendix` (`appendix_id`),
  CONSTRAINT `fk_image_appendix` FOREIGN KEY (`appendix_id`) REFERENCES `appendix_tb` (`appendix_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_image_variant` FOREIGN KEY (`variant_id`) REFERENCES `product_variant_tb` (`variant_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `product_image_tb` WRITE;
/*!40000 ALTER TABLE `product_image_tb` DISABLE KEYS */;
/*!40000 ALTER TABLE `product_image_tb` ENABLE KEYS */;
UNLOCK TABLES;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_supplier_code_tb` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `variant_id` bigint NOT NULL,
  `supplier_id` bigint NOT NULL,
  `supplier_part_no` varchar(64) NOT NULL,
  `is_preferred` tinyint(1) NOT NULL DEFAULT '0',
  `last_purchase_cost` decimal(19,4) DEFAULT NULL,
  `lead_time_days` int DEFAULT NULL,
  `active` tinyint(1) NOT NULL DEFAULT '1',
  `created_by` bigint NOT NULL,
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_by` bigint DEFAULT NULL,
  `update_time` datetime(6) DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_supplier_part` (`supplier_id`,`supplier_part_no`),
  UNIQUE KEY `uk_variant_supplier` (`variant_id`,`supplier_id`),
  UNIQUE KEY `uk_supplier_part_no` (`supplier_id`,`supplier_part_no`),
  KEY `idx_product_supplier_variant` (`variant_id`),
  KEY `idx_product_supplier_supplier` (`supplier_id`),
  KEY `idx_product_supplier_part_no` (`supplier_part_no`),
  CONSTRAINT `fk_psc_supplier` FOREIGN KEY (`supplier_id`) REFERENCES `supplier_tb` (`supplier_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_psc_variant` FOREIGN KEY (`variant_id`) REFERENCES `product_variant_tb` (`variant_id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_psc_cost_non_negative` CHECK (((`last_purchase_cost` is null) or (`last_purchase_cost` >= 0))),
  CONSTRAINT `ck_psc_lead_time` CHECK (((`lead_time_days` is null) or (`lead_time_days` >= 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `product_supplier_code_tb` WRITE;
/*!40000 ALTER TABLE `product_supplier_code_tb` DISABLE KEYS */;
/*!40000 ALTER TABLE `product_supplier_code_tb` ENABLE KEYS */;
UNLOCK TABLES;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_tb` (
  `product_id` bigint NOT NULL AUTO_INCREMENT,
  `product_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `product_name` varchar(150) NOT NULL,
  `product_description` varchar(2000) DEFAULT NULL,
  `type` enum('STOCKABLE','SERVICE','CONSUMABLE') NOT NULL,
  `configurable` tinyint(1) NOT NULL DEFAULT '0',
  `status` enum('DRAFT','ACTIVE','DISCONTINUED') NOT NULL DEFAULT 'DRAFT',
  `base_uom_id` bigint NOT NULL,
  `standard_cost` decimal(19,4) DEFAULT NULL,
  `list_price` decimal(19,4) DEFAULT NULL,
  `currency` char(3) DEFAULT NULL,
  `tax_category` varchar(32) DEFAULT NULL,
  `version` int NOT NULL DEFAULT '0',
  `created_by` bigint NOT NULL,
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_by` bigint DEFAULT NULL,
  `update_time` datetime(6) DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP(6),
  `is_enabled` bit(1) DEFAULT NULL,
  PRIMARY KEY (`product_id`),
  UNIQUE KEY `uk_product_code` (`product_code`),
  UNIQUE KEY `idx_product_code` (`product_code`),
  KEY `idx_product_status_type` (`status`,`type`),
  KEY `idx_product_name` (`product_name`(50)),
  KEY `idx_product_base_uom` (`base_uom_id`),
  KEY `idx_product_type` (`type`),
  KEY `idx_product_status` (`status`),
  CONSTRAINT `fk_product_base_uom` FOREIGN KEY (`base_uom_id`) REFERENCES `uom_tb` (`uom_id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_product_cost_non_negative` CHECK (((`standard_cost` is null) or (`standard_cost` >= 0))),
  CONSTRAINT `ck_product_currency_present` CHECK ((((`standard_cost` is null) and (`list_price` is null)) or (`currency` is not null))),
  CONSTRAINT `ck_product_price_non_negative` CHECK (((`list_price` is null) or (`list_price` >= 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `product_tb` WRITE;
/*!40000 ALTER TABLE `product_tb` DISABLE KEYS */;
/*!40000 ALTER TABLE `product_tb` ENABLE KEYS */;
UNLOCK TABLES;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_uom_tb` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `product_id` bigint NOT NULL,
  `uom_id` bigint NOT NULL,
  `factor_to_base_uom` decimal(24,10) NOT NULL,
  `usage_type` enum('PURCHASE','SALE','BOTH') NOT NULL DEFAULT 'BOTH',
  `active` tinyint(1) NOT NULL DEFAULT '1',
  `created_by` bigint NOT NULL,
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_by` bigint DEFAULT NULL,
  `update_time` datetime(6) DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_uom` (`product_id`,`uom_id`),
  KEY `idx_product_uom_uom` (`uom_id`),
  KEY `idx_product_uom_product` (`product_id`),
  CONSTRAINT `fk_product_uom_product` FOREIGN KEY (`product_id`) REFERENCES `product_tb` (`product_id`) ON DELETE CASCADE,
  CONSTRAINT `fk_product_uom_uom` FOREIGN KEY (`uom_id`) REFERENCES `uom_tb` (`uom_id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_product_uom_factor` CHECK ((`factor_to_base_uom` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `product_uom_tb` WRITE;
/*!40000 ALTER TABLE `product_uom_tb` DISABLE KEYS */;
/*!40000 ALTER TABLE `product_uom_tb` ENABLE KEYS */;
UNLOCK TABLES;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `product_variant_tb` (
  `variant_id` bigint NOT NULL AUTO_INCREMENT,
  `product_id` bigint NOT NULL,
  `sku` varchar(64) NOT NULL,
  `variant_name` varchar(64) NOT NULL,
  `is_default` tinyint(1) NOT NULL DEFAULT '0',
  `description` varchar(2000) DEFAULT NULL,
  `standard_cost` decimal(19,4) DEFAULT NULL,
  `list_price` decimal(19,4) DEFAULT NULL,
  `currency` char(3) DEFAULT NULL,
  `net_weight` decimal(19,4) DEFAULT NULL,
  `weight_uom_id` bigint DEFAULT NULL,
  `length_mm` decimal(12,3) DEFAULT NULL,
  `width_mm` decimal(12,3) DEFAULT NULL,
  `height_mm` decimal(12,3) DEFAULT NULL,
  `active` tinyint(1) NOT NULL DEFAULT '1',
  `version` int NOT NULL DEFAULT '0',
  `created_by` bigint NOT NULL,
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_by` bigint DEFAULT NULL,
  `update_time` datetime(6) DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP(6),
  `height` decimal(12,3) DEFAULT NULL,
  `length` decimal(12,3) DEFAULT NULL,
  `size_uom_id` bigint DEFAULT NULL,
  `width` decimal(12,3) DEFAULT NULL,
  PRIMARY KEY (`variant_id`),
  UNIQUE KEY `uk_variant_sku` (`sku`),
  UNIQUE KEY `uk_variant_name` (`product_id`,`variant_name`),
  UNIQUE KEY `uk_variant_product_name` (`product_id`,`variant_name`),
  UNIQUE KEY `idx_variant_sku` (`sku`),
  KEY `idx_variant_product` (`product_id`,`active`),
  KEY `idx_variant_weight_uom` (`weight_uom_id`),
  KEY `idx_variant_name` (`variant_name`),
  KEY `idx_variant_size_uom` (`size_uom_id`),
  CONSTRAINT `fk_variant_product` FOREIGN KEY (`product_id`) REFERENCES `product_tb` (`product_id`) ON DELETE RESTRICT,
  CONSTRAINT `fk_variant_weight_uom` FOREIGN KEY (`weight_uom_id`) REFERENCES `uom_tb` (`uom_id`) ON DELETE RESTRICT,
  CONSTRAINT `ck_variant_cost_non_negative` CHECK (((`standard_cost` is null) or (`standard_cost` >= 0))),
  CONSTRAINT `ck_variant_price_non_negative` CHECK (((`list_price` is null) or (`list_price` >= 0))),
  CONSTRAINT `ck_variant_weight_has_uom` CHECK (((`net_weight` is null) or (`weight_uom_id` is not null))),
  CONSTRAINT `ck_variant_weight_non_negative` CHECK (((`net_weight` is null) or (`net_weight` >= 0)))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `product_variant_tb` WRITE;
/*!40000 ALTER TABLE `product_variant_tb` DISABLE KEYS */;
/*!40000 ALTER TABLE `product_variant_tb` ENABLE KEYS */;
UNLOCK TABLES;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `purchase_exception_approval_tb` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `decided_at` datetime(6) DEFAULT NULL,
  `decided_by` bigint DEFAULT NULL,
  `decision_comment` varchar(2000) DEFAULT NULL,
  `exception_id` bigint NOT NULL,
  `proposed_resolution` varchar(2000) NOT NULL,
  `requested_at` datetime(6) NOT NULL,
  `requested_by` bigint NOT NULL,
  `status` enum('APPROVED','PENDING','REJECTED') NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_pexc_appr_exception` (`exception_id`,`requested_at`),
  KEY `idx_pexc_appr_status` (`status`,`requested_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `purchase_exception_approval_tb` WRITE;
/*!40000 ALTER TABLE `purchase_exception_approval_tb` DISABLE KEYS */;
/*!40000 ALTER TABLE `purchase_exception_approval_tb` ENABLE KEYS */;
UNLOCK TABLES;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `purchase_exception_log_tb` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `action` varchar(16) NOT NULL,
  `comment` varchar(2000) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `exception_id` bigint NOT NULL,
  `from_status` varchar(16) DEFAULT NULL,
  `operator_id` bigint NOT NULL,
  `to_status` varchar(16) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_pexc_log_exception` (`exception_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `purchase_exception_log_tb` WRITE;
/*!40000 ALTER TABLE `purchase_exception_log_tb` DISABLE KEYS */;
/*!40000 ALTER TABLE `purchase_exception_log_tb` ENABLE KEYS */;
UNLOCK TABLES;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `purchase_exception_tb` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `category` enum('CAPACITY','DELAY','OTHER','PRICE','QUALITY','SHORTAGE') NOT NULL,
  `closed_at` datetime(6) DEFAULT NULL,
  `description` varchar(2000) NOT NULL,
  `exception_no` varchar(32) DEFAULT NULL,
  `handler_id` bigint DEFAULT NULL,
  `impact_date` date DEFAULT NULL,
  `lock_version` bigint NOT NULL,
  `po_no` varchar(50) NOT NULL,
  `purchase_order_id` bigint NOT NULL,
  `reported_at` datetime(6) NOT NULL,
  `reporter_id` bigint NOT NULL,
  `resolution` varchar(2000) DEFAULT NULL,
  `resolved_at` datetime(6) DEFAULT NULL,
  `severity` enum('HIGH','NORMAL','URGENT') NOT NULL,
  `status` varchar(16) NOT NULL,
  `supplier_code` varchar(50) DEFAULT NULL,
  `supplier_name` varchar(200) DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_pexc_reporter_status` (`reporter_id`,`status`),
  KEY `idx_pexc_status` (`status`,`reported_at`),
  KEY `idx_pexc_po` (`purchase_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `purchase_exception_tb` WRITE;
/*!40000 ALTER TABLE `purchase_exception_tb` DISABLE KEYS */;
/*!40000 ALTER TABLE `purchase_exception_tb` ENABLE KEYS */;
UNLOCK TABLES;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `purchase_order_line_tb` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `line_no` int NOT NULL,
  `material_code` varchar(60) DEFAULT NULL,
  `material_name` varchar(255) DEFAULT NULL,
  `plan_type_code` varchar(50) DEFAULT NULL,
  `purchase_order_id` bigint NOT NULL,
  `qty` double DEFAULT NULL,
  `received_qty` double DEFAULT NULL,
  `synced_at` datetime(6) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_po_line` (`purchase_order_id`,`line_no`),
  KEY `idx_po_line_plan_type` (`plan_type_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `purchase_order_line_tb` WRITE;
/*!40000 ALTER TABLE `purchase_order_line_tb` DISABLE KEYS */;
/*!40000 ALTER TABLE `purchase_order_line_tb` ENABLE KEYS */;
UNLOCK TABLES;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `purchase_order_tb` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `buyer_code` varchar(50) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `expected_delivery_date` date DEFAULT NULL,
  `external_po_no` varchar(50) NOT NULL,
  `followup_rule_id` bigint DEFAULT NULL,
  `main_followup_active` bit(1) DEFAULT NULL,
  `order_date` date NOT NULL,
  `purchaser_id` bigint DEFAULT NULL,
  `source_status` varchar(50) DEFAULT NULL,
  `source_system` varchar(30) NOT NULL,
  `source_updated_at` datetime(6) DEFAULT NULL,
  `status` enum('CANCELLED','CLOSED','PARTIALLY_RECEIVED','PLACED','RECEIVED') NOT NULL,
  `supplier_code` varchar(50) DEFAULT NULL,
  `supplier_name` varchar(255) DEFAULT NULL,
  `synced_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_po_source_no` (`source_system`,`external_po_no`),
  KEY `idx_po_purchaser_status` (`purchaser_id`,`status`),
  KEY `idx_po_status_order_date` (`status`,`order_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `purchase_order_tb` WRITE;
/*!40000 ALTER TABLE `purchase_order_tb` DISABLE KEYS */;
/*!40000 ALTER TABLE `purchase_order_tb` ENABLE KEYS */;
UNLOCK TABLES;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `quotation_rate_tb` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `base` varchar(10) DEFAULT NULL,
  `code` varchar(40) DEFAULT NULL,
  `is_enabled` bit(1) NOT NULL,
  `kind` varchar(10) NOT NULL,
  `min_amount` decimal(18,2) DEFAULT NULL,
  `rate_percent` decimal(9,4) NOT NULL,
  `sort_order` int NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `updated_by` bigint DEFAULT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `quotation_rate_tb` WRITE;
/*!40000 ALTER TABLE `quotation_rate_tb` DISABLE KEYS */;
/*!40000 ALTER TABLE `quotation_rate_tb` ENABLE KEYS */;
UNLOCK TABLES;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `role_department_tb` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_id` bigint NOT NULL,
  `department_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_department` (`role_id`,`department_id`),
  KEY `idx_role_dept_department` (`department_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `role_department_tb` WRITE;
/*!40000 ALTER TABLE `role_department_tb` DISABLE KEYS */;
/*!40000 ALTER TABLE `role_department_tb` ENABLE KEYS */;
UNLOCK TABLES;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `role_permission_tb` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_id` bigint NOT NULL,
  `permission_id` bigint NOT NULL,
  `menu_id` bigint DEFAULT NULL,
  `created_by` bigint NOT NULL,
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `create_date` date DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_permission` (`role_id`,`permission_id`),
  KEY `idx_role_perm_permission` (`permission_id`)
) ENGINE=InnoDB AUTO_INCREMENT=132 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `role_permission_tb` WRITE;
/*!40000 ALTER TABLE `role_permission_tb` DISABLE KEYS */;
INSERT INTO `role_permission_tb` VALUES (1,1,1,NULL,1,'2026-11-28 01:18:21.589382','2026-11-28'),(2,1,3,NULL,1,'2026-11-28 01:18:21.589382','2026-11-28'),(3,1,5,NULL,1,'2026-11-28 01:18:21.589382','2026-11-28'),(4,1,6,NULL,1,'2026-11-28 01:18:21.589382','2026-11-28'),(5,1,4,NULL,1,'2026-11-28 01:18:21.589382','2026-11-28'),(6,1,2,NULL,1,'2026-11-28 01:18:21.589382','2026-11-28'),(8,2,1,NULL,1,'2026-11-28 01:18:21.715822','2026-11-28'),(9,2,3,NULL,1,'2026-11-28 01:18:21.715822','2026-11-28'),(10,2,5,NULL,1,'2026-11-28 01:18:21.715822','2026-11-28'),(11,2,6,NULL,1,'2026-11-28 01:18:21.715822','2026-11-28'),(12,2,4,NULL,1,'2026-11-28 01:18:21.715822','2026-11-28'),(13,2,2,NULL,1,'2026-11-28 01:18:21.715822','2026-11-28'),(14,2,100,NULL,1,'2026-11-28 01:18:21.715822','2026-11-28'),(15,1,107,NULL,1,'2026-11-28 01:18:21.715822','2026-11-28'),(16,1,105,NULL,1,'2026-11-28 01:18:21.715822','2026-11-28'),(17,1,108,NULL,1,'2026-11-28 01:18:21.715822','2026-11-28'),(18,1,106,NULL,1,'2026-11-28 01:18:21.715822','2026-11-28'),(19,1,115,NULL,1,'2026-11-28 01:18:21.715822','2026-11-28'),(20,1,113,NULL,1,'2026-11-28 01:18:21.715822','2026-11-28'),(21,1,116,NULL,1,'2026-11-28 01:18:21.715822','2026-11-28'),(22,1,114,NULL,1,'2026-11-28 01:18:21.715822','2026-11-28'),(23,1,119,NULL,1,'2026-11-28 01:18:21.715822','2026-11-28'),(24,1,117,NULL,1,'2026-11-28 01:18:21.715822','2026-11-28'),(25,1,120,NULL,1,'2026-11-28 01:18:21.715822','2026-11-28'),(26,1,118,NULL,1,'2026-11-28 01:18:21.715822','2026-11-28'),(27,1,111,NULL,1,'2026-11-28 01:18:21.715822','2026-11-28'),(28,1,109,NULL,1,'2026-11-28 01:18:21.715822','2026-11-28'),(29,1,112,NULL,1,'2026-11-28 01:18:21.715822','2026-11-28'),(30,1,110,NULL,1,'2026-11-28 01:18:21.715822','2026-11-28'),(31,1,103,NULL,1,'2026-11-28 01:18:21.715822','2026-11-28'),(32,1,101,NULL,1,'2026-11-28 01:18:21.715822','2026-11-28'),(33,1,104,NULL,1,'2026-11-28 01:18:21.715822','2026-11-28'),(34,1,102,NULL,1,'2026-11-28 01:18:21.715822','2026-11-28'),(35,1,100,NULL,1,'2026-11-28 01:18:21.715822','2026-11-28'),(36,1,123,NULL,1,'2026-11-28 01:18:21.715822','2026-11-28'),(37,1,121,NULL,1,'2026-11-28 01:18:21.715822','2026-11-28'),(38,1,132,NULL,1,'2026-11-28 01:18:21.715822','2026-11-28'),(39,1,124,NULL,1,'2026-11-28 01:18:21.715822','2026-11-28'),(40,1,122,NULL,1,'2026-11-28 01:18:21.715822','2026-11-28'),(41,3,124,NULL,1,'2026-11-28 01:18:21.715822','2026-11-28'),(71,103,165,NULL,1,'2026-11-28 01:18:22.229525','2026-11-28'),(72,100,165,NULL,1,'2026-11-28 01:18:22.229525','2026-11-28'),(73,103,164,NULL,1,'2026-11-28 01:18:22.229525','2026-11-28'),(74,100,150,NULL,1,'2026-11-28 01:18:22.229525','2026-11-28'),(77,103,151,NULL,1,'2026-11-28 01:18:22.229525','2026-11-28'),(78,103,153,NULL,1,'2026-11-28 01:18:22.229525','2026-11-28'),(79,103,159,NULL,1,'2026-11-28 01:18:22.229525','2026-11-28'),(80,103,157,NULL,1,'2026-11-28 01:18:22.229525','2026-11-28'),(81,100,157,NULL,1,'2026-11-28 01:18:22.229525','2026-11-28'),(82,103,152,NULL,1,'2026-11-28 01:18:22.229525','2026-11-28'),(83,103,158,NULL,1,'2026-11-28 01:18:22.229525','2026-11-28'),(84,103,162,NULL,1,'2026-11-28 01:18:22.229525','2026-11-28'),(86,100,136,NULL,1,'2026-11-28 01:18:22.324687','2026-11-28'),(87,100,142,NULL,1,'2026-11-28 01:18:22.324687','2026-11-28'),(88,100,141,NULL,1,'2026-11-28 01:18:22.324687','2026-11-28'),(90,100,140,NULL,1,'2026-11-28 01:18:22.324687','2026-11-28'),(91,100,134,NULL,1,'2026-11-28 01:18:22.324687','2026-11-28'),(93,100,137,NULL,1,'2026-11-28 01:18:22.324687','2026-11-28'),(95,100,143,NULL,1,'2026-11-28 01:18:22.324687','2026-11-28'),(96,100,133,NULL,1,'2026-11-28 01:18:22.324687','2026-11-28'),(98,100,138,NULL,1,'2026-11-28 01:18:22.324687','2026-11-28'),(100,100,135,NULL,1,'2026-11-28 01:18:22.324687','2026-11-28'),(102,100,139,NULL,1,'2026-11-28 01:18:22.324687','2026-11-28'),(117,100,167,NULL,1,'2026-11-28 01:18:22.591800','2026-11-28'),(118,100,168,NULL,1,'2026-11-28 01:18:22.591800','2026-11-28'),(122,100,161,NULL,1,'2026-11-28 01:18:22.786107','2026-11-28'),(127,103,170,NULL,1,'2026-11-28 01:18:22.970356','2026-11-28'),(128,103,171,NULL,1,'2026-11-28 01:18:22.970356','2026-11-28'),(130,100,146,NULL,1,'2026-11-28 01:18:23.346683','2026-11-28'),(131,103,173,NULL,1,'2026-11-28 01:18:23.656742','2026-11-28');
/*!40000 ALTER TABLE `role_permission_tb` ENABLE KEYS */;
UNLOCK TABLES;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `role_tb` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_name` varchar(50) NOT NULL,
  `val` int DEFAULT NULL,
  `create_date` date DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_name` (`role_name`),
  UNIQUE KEY `uk_role_val` (`val`)
) ENGINE=InnoDB AUTO_INCREMENT=107 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `role_tb` WRITE;
/*!40000 ALTER TABLE `role_tb` DISABLE KEYS */;
INSERT INTO `role_tb` VALUES (1,'ROOT',1,'2026-11-28'),(2,'ADMIN',2,'2026-11-28'),(3,'USER',3,'2026-11-28'),(100,'pmc',10,'2026-11-28'),(103,'purchaser',13,'2026-11-28');
/*!40000 ALTER TABLE `role_tb` ENABLE KEYS */;
UNLOCK TABLES;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `supplier_tb` (
  `supplier_id` bigint NOT NULL AUTO_INCREMENT,
  `supplier_code` varchar(20) NOT NULL,
  `legal_name` varchar(256) NOT NULL,
  `trade_name` varchar(255) DEFAULT NULL,
  `status` enum('ACTIVE','HOLD','INACTIVE') NOT NULL DEFAULT 'ACTIVE',
  `tax_id` varchar(50) DEFAULT NULL,
  `created_by` bigint NOT NULL,
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_by` bigint DEFAULT NULL,
  `update_time` datetime(6) DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`supplier_id`),
  UNIQUE KEY `uk_supplier_code` (`supplier_code`),
  UNIQUE KEY `uk_supplier_legal` (`legal_name`),
  UNIQUE KEY `idx_supplier_code` (`supplier_code`),
  UNIQUE KEY `idx_supplier_legal_name` (`legal_name`),
  UNIQUE KEY `uk_supplier_tax_id` (`tax_id`),
  UNIQUE KEY `idx_supplier_tax_id` (`tax_id`),
  KEY `idx_supplier_status` (`status`),
  KEY `idx_supplier_trade` (`trade_name`),
  KEY `idx_supplier_trade_name` (`trade_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `supplier_tb` WRITE;
/*!40000 ALTER TABLE `supplier_tb` DISABLE KEYS */;
/*!40000 ALTER TABLE `supplier_tb` ENABLE KEYS */;
UNLOCK TABLES;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `uom_tb` (
  `uom_id` bigint NOT NULL AUTO_INCREMENT,
  `iso_code` varchar(8) NOT NULL,
  `unit_name` varchar(32) NOT NULL,
  `symbol` varchar(8) DEFAULT NULL,
  `dimension` enum('MASS','LENGTH','VOLUME','AREA','TIME','TEMP','DENS','VELOC','ACCEL','ENERGY','POWER','PRESS','COUNT') NOT NULL,
  `is_base_for_dimension` tinyint(1) NOT NULL DEFAULT '0',
  `factor_to_base` decimal(24,10) NOT NULL DEFAULT '1.0000000000',
  `description` varchar(2000) DEFAULT NULL,
  `active` tinyint(1) NOT NULL DEFAULT '1',
  `created_by` bigint NOT NULL,
  `create_time` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_by` bigint DEFAULT NULL,
  `update_time` datetime(6) DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`uom_id`),
  UNIQUE KEY `uk_uom_iso_code` (`iso_code`),
  UNIQUE KEY `uk_uom_unit_name` (`unit_name`),
  UNIQUE KEY `idx_uom_iso_code` (`iso_code`),
  UNIQUE KEY `idx_uom_unit_name` (`unit_name`),
  KEY `idx_uom_dimension` (`dimension`,`active`),
  CONSTRAINT `ck_uom_factor_positive` CHECK ((`factor_to_base` > 0))
) ENGINE=InnoDB AUTO_INCREMENT=16 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `uom_tb` WRITE;
/*!40000 ALTER TABLE `uom_tb` DISABLE KEYS */;
INSERT INTO `uom_tb` VALUES (1,'EA','Each','ea','COUNT',1,1.0000000000,NULL,1,1,'2026-11-28 01:18:05.117732',NULL,NULL),(2,'PC','Piece','pc','COUNT',0,1.0000000000,NULL,1,1,'2026-11-28 01:18:05.117732',NULL,NULL),(3,'BOX','Box','box','COUNT',0,1.0000000000,NULL,1,1,'2026-11-28 01:18:05.117732',NULL,NULL),(4,'CT','Carton','ct','COUNT',0,1.0000000000,NULL,1,1,'2026-11-28 01:18:05.117732',NULL,NULL),(5,'PAL','Pallet','pal','COUNT',0,1.0000000000,NULL,1,1,'2026-11-28 01:18:05.117732',NULL,NULL),(6,'KGM','Kilogram','kg','MASS',1,1.0000000000,NULL,1,1,'2026-11-28 01:18:05.117732',NULL,NULL),(7,'GRM','Gram','g','MASS',0,0.0010000000,NULL,1,1,'2026-11-28 01:18:05.117732',NULL,NULL),(8,'TNE','Tonne','t','MASS',0,1000.0000000000,NULL,1,1,'2026-11-28 01:18:05.117732',NULL,NULL),(9,'LBR','Pound','lb','MASS',0,0.4535923700,NULL,1,1,'2026-11-28 01:18:05.117732',NULL,NULL),(10,'MTR','Metre','m','LENGTH',1,1.0000000000,NULL,1,1,'2026-11-28 01:18:05.117732',NULL,NULL),(11,'MMT','Millimetre','mm','LENGTH',0,0.0010000000,NULL,1,1,'2026-11-28 01:18:05.117732',NULL,NULL),(12,'CMT','Centimetre','cm','LENGTH',0,0.0100000000,NULL,1,1,'2026-11-28 01:18:05.117732',NULL,NULL),(13,'INH','Inch','in','LENGTH',0,0.0254000000,NULL,1,1,'2026-11-28 01:18:05.117732',NULL,NULL),(14,'LTR','Litre','L','VOLUME',1,1.0000000000,NULL,1,1,'2026-11-28 01:18:05.117732',NULL,NULL),(15,'MLT','Millilitre','mL','VOLUME',0,0.0010000000,NULL,1,1,'2026-11-28 01:18:05.117732',NULL,NULL);
/*!40000 ALTER TABLE `uom_tb` ENABLE KEYS */;
UNLOCK TABLES;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `upload_session` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `appendix_id` bigint DEFAULT NULL,
  `chuck_size` bigint NOT NULL,
  `current_idx` int NOT NULL,
  `file_uuid` varchar(36) NOT NULL,
  `status` enum('FINISHED','MERGING','UPLOADING') DEFAULT NULL,
  `total_size` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `upload_session_file_uuid_index` (`file_uuid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `upload_session` WRITE;
/*!40000 ALTER TABLE `upload_session` DISABLE KEYS */;
/*!40000 ALTER TABLE `upload_session` ENABLE KEYS */;
UNLOCK TABLES;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_departments_tb` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `dept_id` bigint NOT NULL,
  `create_date` date DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_dept` (`user_id`,`dept_id`),
  KEY `idx_user_dept_dept` (`dept_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `user_departments_tb` WRITE;
/*!40000 ALTER TABLE `user_departments_tb` DISABLE KEYS */;
INSERT INTO `user_departments_tb` VALUES (1,1,1,'2026-11-28');
/*!40000 ALTER TABLE `user_departments_tb` ENABLE KEYS */;
UNLOCK TABLES;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_info_tb` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `real_name` varchar(50) DEFAULT NULL,
  `title` varchar(100) DEFAULT NULL,
  `birthday` date DEFAULT NULL,
  `hire_date` date DEFAULT NULL,
  `create_date` date DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_info_user` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `user_info_tb` WRITE;
/*!40000 ALTER TABLE `user_info_tb` DISABLE KEYS */;
INSERT INTO `user_info_tb` VALUES (1,1,'Root Administrator','System Administrator',NULL,'2026-11-28','2026-11-28');
/*!40000 ALTER TABLE `user_info_tb` ENABLE KEYS */;
UNLOCK TABLES;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_login_log_tb` (
  `id` varchar(36) NOT NULL,
  `user_id` bigint DEFAULT NULL,
  `login_ip` varchar(255) DEFAULT NULL,
  `login_time` datetime(6) DEFAULT NULL,
  `status` int DEFAULT NULL,
  `create_date` date DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_login_user_time` (`user_id`,`login_time`),
  KEY `idx_login_time` (`login_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `user_login_log_tb` WRITE;
/*!40000 ALTER TABLE `user_login_log_tb` DISABLE KEYS */;
/*!40000 ALTER TABLE `user_login_log_tb` ENABLE KEYS */;
UNLOCK TABLES;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_nav_menu` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `nav_id` bigint NOT NULL,
  `permission_id` bigint DEFAULT NULL,
  `user_id` bigint NOT NULL,
  `is_enabled` bit(1) NOT NULL DEFAULT b'1',
  `create_date` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_nav` (`user_id`,`nav_id`),
  KEY `idx_user_nav_nav` (`nav_id`,`is_enabled`)
) ENGINE=InnoDB AUTO_INCREMENT=24 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `user_nav_menu` WRITE;
/*!40000 ALTER TABLE `user_nav_menu` DISABLE KEYS */;
INSERT INTO `user_nav_menu` VALUES (1,1,NULL,1,0x01,'2026-11-28 01:18:21.584201'),(2,2,NULL,1,0x01,'2026-11-28 01:18:21.584201'),(4,6,NULL,1,0x01,'2026-11-28 01:18:21.584201'),(5,3,NULL,1,0x01,'2026-11-28 01:18:21.584201'),(6,4,NULL,1,0x01,'2026-11-28 01:18:21.584201'),(8,100,NULL,1,0x01,'2026-11-28 01:18:22.000000'),(9,101,NULL,1,0x01,'2026-11-28 01:18:23.786137'),(10,102,NULL,1,0x01,'2026-11-28 01:18:23.786137'),(11,103,NULL,1,0x01,'2026-11-28 01:18:23.786137'),(12,104,NULL,1,0x01,'2026-11-28 01:18:23.786137'),(13,105,NULL,1,0x01,'2026-11-28 01:18:23.786137'),(14,106,NULL,1,0x01,'2026-11-28 01:18:23.786137'),(15,108,NULL,1,0x01,'2026-11-28 01:18:23.786137'),(16,109,NULL,1,0x01,'2026-11-28 01:18:23.786137'),(17,110,NULL,1,0x01,'2026-11-28 01:18:23.786137'),(18,111,NULL,1,0x01,'2026-11-28 01:18:23.786137');
/*!40000 ALTER TABLE `user_nav_menu` ENABLE KEYS */;
UNLOCK TABLES;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_refresh_token_tb` (
  `id` varchar(32) NOT NULL,
  `user_id` bigint NOT NULL,
  `token_hash` varchar(50) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `revoked` bit(1) DEFAULT b'0',
  `create_date` date DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_refresh_token_hash` (`token_hash`),
  KEY `idx_refresh_user` (`user_id`,`revoked`),
  KEY `idx_refresh_expires` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `user_refresh_token_tb` WRITE;
/*!40000 ALTER TABLE `user_refresh_token_tb` DISABLE KEYS */;
/*!40000 ALTER TABLE `user_refresh_token_tb` ENABLE KEYS */;
UNLOCK TABLES;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_roles_tb` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `role_id` bigint NOT NULL,
  `create_date` date DEFAULT NULL,
  `user_enabled` tinyint NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_role` (`user_id`,`role_id`),
  KEY `idx_user_role_role` (`role_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `user_roles_tb` WRITE;
/*!40000 ALTER TABLE `user_roles_tb` DISABLE KEYS */;
INSERT INTO `user_roles_tb` VALUES (1,1,1,'2026-11-28',1);
/*!40000 ALTER TABLE `user_roles_tb` ENABLE KEYS */;
UNLOCK TABLES;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user_tb` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(50) NOT NULL,
  `display_name` varchar(50) DEFAULT NULL,
  `email` varchar(50) DEFAULT NULL,
  `password` varchar(255) NOT NULL,
  `status` bit(1) DEFAULT NULL,
  `current_session_id` varchar(36) DEFAULT NULL,
  `failed_attempted` tinyint DEFAULT '0',
  `locked_until` datetime(6) DEFAULT NULL,
  `create_date` date DEFAULT NULL,
  `is_enabled` tinyint DEFAULT '1',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_username` (`username`),
  UNIQUE KEY `uk_user_email` (`email`),
  KEY `idx_user_session` (`current_session_id`)
) ENGINE=InnoDB AUTO_INCREMENT=100 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `user_tb` WRITE;
/*!40000 ALTER TABLE `user_tb` DISABLE KEYS */;
INSERT INTO `user_tb` VALUES (1,'root','Root Administrator','root@example.com','$argon2id$v=19$m=65536,t=4,p=2$TypoRb1lFVSUiU9F0qWamqZmFHhRjlH+JYMNeMzFy1bKAtBUz+kvmBSQBr950HIFVzVSGQpsZ7xlaTmdiVf4/w$cVdU32sOSqIUpU554dXEKLg3U1HA+gZ77UoleMDkEygpe6upbvBcGb1pAWwbqnPvzco',0x01,NULL,0,NULL,'2026-11-28',1);
/*!40000 ALTER TABLE `user_tb` ENABLE KEYS */;
UNLOCK TABLES;
/*!50001 DROP VIEW IF EXISTS `navigation_permission_view`*/;
/*!50001 SET @saved_cs_client          = @@character_set_client */;
/*!50001 SET @saved_cs_results         = @@character_set_results */;
/*!50001 SET @saved_col_connection     = @@collation_connection */;
/*!50001 SET character_set_client      = utf8mb4 */;
/*!50001 SET character_set_results     = utf8mb4 */;
/*!50001 SET collation_connection      = utf8mb4_0900_ai_ci */;
/*!50001 CREATE ALGORITHM=UNDEFINED */

/*!50001 VIEW `navigation_permission_view` AS select `m`.`id` AS `id`,`n`.`id` AS `nav_id`,`n`.`title_key` AS `nav_key`,`n`.`route` AS `route`,`u`.`id` AS `u_id`,`u`.`username` AS `u_name`,`p`.`id` AS `p_id`,`p`.`permission_name` AS `p_name`,`p`.`val` AS `p_val` from (((`user_nav_menu` `m` left join `navigation_menu_tb` `n` on((`m`.`nav_id` = `n`.`id`))) left join `user_tb` `u` on((`m`.`user_id` = `u`.`id`))) left join `permission_tb` `p` on((`m`.`permission_id` = `p`.`id`))) */;
/*!50001 SET character_set_client      = @saved_cs_client */;
/*!50001 SET character_set_results     = @saved_cs_results */;
/*!50001 SET collation_connection      = @saved_col_connection */;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

