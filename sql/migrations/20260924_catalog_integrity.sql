-- Data-preserving catalog migration for MySQL 8. Run against the selected database.
-- Re-running is safe. Back up the database first; ALTER TABLE implicitly commits.

-- Older bootstrap scripts did not create this table; Hibernate created it on startup.
CREATE TABLE IF NOT EXISTS designer_like (
 id INT AUTO_INCREMENT PRIMARY KEY,
 member_id VARCHAR(30) NOT NULL,
 designer_id INT NOT NULL,
 created_at DATETIME(6),
 CONSTRAINT uq_designer_like UNIQUE (member_id, designer_id),
 CONSTRAINT fk_designer_like_member FOREIGN KEY (member_id) REFERENCES member (member_id) ON DELETE RESTRICT,
 CONSTRAINT fk_designer_like_designer FOREIGN KEY (designer_id) REFERENCES designer (designer_id) ON DELETE CASCADE
);

SET @catalog_fk = (SELECT CONSTRAINT_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'designer' AND COLUMN_NAME = 'salon_id' AND REFERENCED_TABLE_NAME = 'salon' LIMIT 1);
SET @catalog_rule = (SELECT DELETE_RULE FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'designer' AND CONSTRAINT_NAME = @catalog_fk);
SET @catalog_ddl = IF(@catalog_rule = 'RESTRICT', 'SELECT 1', CONCAT('ALTER TABLE `designer` ', IF(@catalog_fk IS NULL, '', CONCAT('DROP FOREIGN KEY `', REPLACE(@catalog_fk, '`', '``'), '`, ')), 'ADD CONSTRAINT `fk_catalog_designer_salon_id` FOREIGN KEY (`salon_id`) REFERENCES `salon` (`salon_id`) ON DELETE RESTRICT'));
PREPARE catalog_stmt FROM @catalog_ddl;
EXECUTE catalog_stmt;
DEALLOCATE PREPARE catalog_stmt;

SET @catalog_fk = (SELECT CONSTRAINT_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'salon_service' AND COLUMN_NAME = 'salon_id' AND REFERENCED_TABLE_NAME = 'salon' LIMIT 1);
SET @catalog_rule = (SELECT DELETE_RULE FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'salon_service' AND CONSTRAINT_NAME = @catalog_fk);
SET @catalog_ddl = IF(@catalog_rule = 'RESTRICT', 'SELECT 1', CONCAT('ALTER TABLE `salon_service` ', IF(@catalog_fk IS NULL, '', CONCAT('DROP FOREIGN KEY `', REPLACE(@catalog_fk, '`', '``'), '`, ')), 'ADD CONSTRAINT `fk_catalog_salon_service_salon_id` FOREIGN KEY (`salon_id`) REFERENCES `salon` (`salon_id`) ON DELETE RESTRICT'));
PREPARE catalog_stmt FROM @catalog_ddl;
EXECUTE catalog_stmt;
DEALLOCATE PREPARE catalog_stmt;

SET @catalog_fk = (SELECT CONSTRAINT_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reservation' AND COLUMN_NAME = 'designer_id' AND REFERENCED_TABLE_NAME = 'designer' LIMIT 1);
SET @catalog_rule = (SELECT DELETE_RULE FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'reservation' AND CONSTRAINT_NAME = @catalog_fk);
SET @catalog_ddl = IF(@catalog_rule = 'RESTRICT', 'SELECT 1', CONCAT('ALTER TABLE `reservation` ', IF(@catalog_fk IS NULL, '', CONCAT('DROP FOREIGN KEY `', REPLACE(@catalog_fk, '`', '``'), '`, ')), 'ADD CONSTRAINT `fk_catalog_reservation_designer_id` FOREIGN KEY (`designer_id`) REFERENCES `designer` (`designer_id`) ON DELETE RESTRICT'));
PREPARE catalog_stmt FROM @catalog_ddl;
EXECUTE catalog_stmt;
DEALLOCATE PREPARE catalog_stmt;

SET @catalog_fk = (SELECT CONSTRAINT_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reservation' AND COLUMN_NAME = 'service_id' AND REFERENCED_TABLE_NAME = 'salon_service' LIMIT 1);
SET @catalog_rule = (SELECT DELETE_RULE FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'reservation' AND CONSTRAINT_NAME = @catalog_fk);
SET @catalog_ddl = IF(@catalog_rule = 'RESTRICT', 'SELECT 1', CONCAT('ALTER TABLE `reservation` ', IF(@catalog_fk IS NULL, '', CONCAT('DROP FOREIGN KEY `', REPLACE(@catalog_fk, '`', '``'), '`, ')), 'ADD CONSTRAINT `fk_catalog_reservation_service_id` FOREIGN KEY (`service_id`) REFERENCES `salon_service` (`service_id`) ON DELETE RESTRICT'));
PREPARE catalog_stmt FROM @catalog_ddl;
EXECUTE catalog_stmt;
DEALLOCATE PREPARE catalog_stmt;

SET @catalog_fk = (SELECT CONSTRAINT_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'review' AND COLUMN_NAME = 'designer_id' AND REFERENCED_TABLE_NAME = 'designer' LIMIT 1);
SET @catalog_rule = (SELECT DELETE_RULE FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'review' AND CONSTRAINT_NAME = @catalog_fk);
SET @catalog_ddl = IF(@catalog_rule = 'RESTRICT', 'SELECT 1', CONCAT('ALTER TABLE `review` ', IF(@catalog_fk IS NULL, '', CONCAT('DROP FOREIGN KEY `', REPLACE(@catalog_fk, '`', '``'), '`, ')), 'ADD CONSTRAINT `fk_catalog_review_designer_id` FOREIGN KEY (`designer_id`) REFERENCES `designer` (`designer_id`) ON DELETE RESTRICT'));
PREPARE catalog_stmt FROM @catalog_ddl;
EXECUTE catalog_stmt;
DEALLOCATE PREPARE catalog_stmt;

SET @catalog_fk = (SELECT CONSTRAINT_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'designer_like' AND COLUMN_NAME = 'designer_id' AND REFERENCED_TABLE_NAME = 'designer' LIMIT 1);
SET @catalog_rule = (SELECT DELETE_RULE FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'designer_like' AND CONSTRAINT_NAME = @catalog_fk);
SET @catalog_ddl = IF(@catalog_rule = 'CASCADE', 'SELECT 1', CONCAT('ALTER TABLE `designer_like` ', IF(@catalog_fk IS NULL, '', CONCAT('DROP FOREIGN KEY `', REPLACE(@catalog_fk, '`', '``'), '`, ')), 'ADD CONSTRAINT `fk_catalog_designer_like_designer_id` FOREIGN KEY (`designer_id`) REFERENCES `designer` (`designer_id`) ON DELETE CASCADE'));
PREPARE catalog_stmt FROM @catalog_ddl;
EXECUTE catalog_stmt;
DEALLOCATE PREPARE catalog_stmt;

SET @catalog_fk = (SELECT CONSTRAINT_NAME FROM information_schema.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'salon_like' AND COLUMN_NAME = 'salon_id' AND REFERENCED_TABLE_NAME = 'salon' LIMIT 1);
SET @catalog_rule = (SELECT DELETE_RULE FROM information_schema.REFERENTIAL_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'salon_like' AND CONSTRAINT_NAME = @catalog_fk);
SET @catalog_ddl = IF(@catalog_rule = 'CASCADE', 'SELECT 1', CONCAT('ALTER TABLE `salon_like` ', IF(@catalog_fk IS NULL, '', CONCAT('DROP FOREIGN KEY `', REPLACE(@catalog_fk, '`', '``'), '`, ')), 'ADD CONSTRAINT `fk_catalog_salon_like_salon_id` FOREIGN KEY (`salon_id`) REFERENCES `salon` (`salon_id`) ON DELETE CASCADE'));
PREPARE catalog_stmt FROM @catalog_ddl;
EXECUTE catalog_stmt;
DEALLOCATE PREPARE catalog_stmt;

-- Remove legacy uniqueness across providers; keep uniqueness within each provider.
SET @catalog_drop_indexes = (SELECT GROUP_CONCAT(CONCAT('DROP INDEX `', REPLACE(INDEX_NAME, '`', '``'), '`') SEPARATOR ', ') FROM (SELECT INDEX_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'salon' AND NON_UNIQUE = 0 AND INDEX_NAME <> 'PRIMARY' GROUP BY INDEX_NAME HAVING GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) = 'external_id') legacy_indexes);
SET @catalog_ddl = IF(@catalog_drop_indexes IS NULL, 'SELECT 1', CONCAT('ALTER TABLE salon ', @catalog_drop_indexes));
PREPARE catalog_stmt FROM @catalog_ddl;
EXECUTE catalog_stmt;
DEALLOCATE PREPARE catalog_stmt;
SET @catalog_composite = (SELECT COUNT(*) FROM (SELECT INDEX_NAME FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'salon' AND NON_UNIQUE = 0 GROUP BY INDEX_NAME HAVING GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) IN ('source_type,external_id', 'external_id,source_type')) composite_indexes);
SET @catalog_ddl = IF(@catalog_composite > 0, 'SELECT 1', 'ALTER TABLE salon ADD CONSTRAINT uq_salon_external UNIQUE (source_type, external_id)');
PREPARE catalog_stmt FROM @catalog_ddl;
EXECUTE catalog_stmt;
DEALLOCATE PREPARE catalog_stmt;
