-- ============================================
-- 场景 14: 索引冗余
-- 模拟: 表上有大量重复或冗余索引
-- ============================================

-- 模拟冗余索引
DROP TABLE IF EXISTS `redundant_indexes`;
CREATE TABLE `redundant_indexes` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `user_id` INT NOT NULL,
    `status` VARCHAR(20),
    `type` VARCHAR(20),
    `created_at` DATETIME,
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_user_id_status` (`user_id`, `status`),
    INDEX `idx_user_id_type` (`user_id`, `type`),
    INDEX `idx_user_id_created_at` (`user_id`, `created_at`)
) ENGINE=InnoDB;

-- 前三个索引都以 user_id 开头，idx_user_id 被 idx_user_id_status 完全覆盖
-- 检查冗余索引:
SELECT TABLE_NAME, INDEX_NAME, GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX) AS columns
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'redundant_indexes'
GROUP BY TABLE_NAME, INDEX_NAME;

-- 优化: 删除冗余索引
-- DROP INDEX idx_user_id ON redundant_indexes;
-- DROP INDEX idx_user_id_type ON redundant_indexes;
-- DROP INDEX idx_user_id_created_at ON redundant_indexes;
-- 一个 idx_user_id_status 可以覆盖所有需要 user_id 的场景
