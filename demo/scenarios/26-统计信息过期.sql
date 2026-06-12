-- ============================================
-- 场景 26: 统计信息过期
-- 模拟: MySQL 统计信息过期导致错误执行计划
-- ============================================

-- 创建表
DROP TABLE IF EXISTS `stats_expired`;
CREATE TABLE `stats_expired` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `type` INT NOT NULL,
    `data` VARCHAR(100),
    INDEX `idx_type` (`type`)
) ENGINE=InnoDB;

-- 插入数据：均匀分布
INSERT INTO `stats_expired` (`type`, `data`)
SELECT
    FLOOR(RAND() * 100),
    CONCAT('data-', FLOOR(RAND() * 10000))
FROM information_schema.COLUMNS
LIMIT 50000;

-- 然后删除 99% 的数据（导致统计信息严重过时）
DELETE FROM `stats_expired` WHERE `type` > 0 AND `id` <= 49500;

-- 此时统计信息还未更新

-- 查看统计信息
SELECT TABLE_NAME, ROWS, DATA_LENGTH
FROM information_schema.TABLES
WHERE TABLE_NAME = 'stats_expired';

-- 查看索引统计
SHOW INDEX FROM `stats_expired`;

-- 主动更新统计信息
ANALYZE TABLE `stats_expired`;

-- 统计信息过期的影响:
-- 优化器可能选择全表扫描而不是索引（实际数据很少）
-- 或者选择错误索引

-- 自动更新统计信息的阈值:
-- 当 affected_rows > 10% of table_rows 时自动更新
-- 也可以设置: SET GLOBAL innodb_stats_auto_recalc = ON;
