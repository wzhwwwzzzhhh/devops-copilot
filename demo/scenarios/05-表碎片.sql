-- ============================================
-- 场景 5: 表碎片过多
-- 模拟: 频繁 DELETE/UPDATE 导致表碎片
-- ============================================

-- 创建测试表并产生碎片
DROP TABLE IF EXISTS `frag_test`;
CREATE TABLE `frag_test` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `data` VARCHAR(1000) NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- 插入数据
INSERT INTO `frag_test` (`data`)
SELECT REPEAT('X', 500)
FROM information_schema.COLUMNS
LIMIT 10000;

-- 删除大部分数据，产生碎片
DELETE FROM `frag_test` WHERE `id` % 3 != 0;

-- 查看碎片大小
SELECT
    TABLE_SCHEMA, TABLE_NAME,
    ROUND(DATA_FREE / 1024 / 1024, 2) AS fragmentation_mb,
    ROUND((DATA_LENGTH + INDEX_LENGTH) / 1024 / 1024, 2) AS total_mb,
    ROUND(DATA_FREE / (DATA_LENGTH + INDEX_LENGTH) * 100, 1) AS frag_pct
FROM information_schema.TABLES
WHERE TABLE_NAME = 'frag_test';

-- 优化: OPTIMIZE TABLE frag_test;
