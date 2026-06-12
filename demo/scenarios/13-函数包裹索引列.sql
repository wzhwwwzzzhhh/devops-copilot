-- ============================================
-- 场景 13: 函数包裹索引列
-- 模拟: 在 WHERE 条件中对索引列使用函数
-- ============================================

DROP TABLE IF EXISTS `date_test`;
CREATE TABLE `date_test` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(100),
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB;

INSERT INTO `date_test` (`name`, `created_at`)
SELECT
    CONCAT('record-', FLOOR(RAND() * 10000)),
    NOW() - INTERVAL FLOOR(RAND() * 365) DAY
FROM information_schema.COLUMNS
LIMIT 50000;

-- ❌ 函数包裹索引列 → 索引失效
EXPLAIN SELECT * FROM `date_test` WHERE DATE(`created_at`) = '2026-01-15';
-- 预期: type=ALL（全表扫描）

-- ✅ 优化: 使用范围查询
EXPLAIN SELECT * FROM `date_test`
WHERE `created_at` >= '2026-01-15 00:00:00'
  AND `created_at` < '2026-01-16 00:00:00';
-- 预期: type=range

-- ❌ 另一个常见错误
EXPLAIN SELECT * FROM `date_test` WHERE YEAR(`created_at`) = 2026;

-- ✅ 优化
EXPLAIN SELECT * FROM `date_test`
WHERE `created_at` >= '2026-01-01' AND `created_at` < '2027-01-01';
