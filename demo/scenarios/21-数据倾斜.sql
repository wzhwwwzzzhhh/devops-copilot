-- ============================================
-- 场景 21: 数据倾斜
-- 模拟: 某些值的数据量极大，导致查询不均衡
-- ============================================

DROP TABLE IF EXISTS `skewed_data`;
CREATE TABLE `skewed_data` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `city_id` INT NOT NULL,
    `population` INT,
    INDEX `idx_city` (`city_id`)
) ENGINE=InnoDB;

-- 大多数数据集中在少数城市
INSERT INTO `skewed_data` (`city_id`, `population`)
SELECT
    CASE
        WHEN RAND() < 0.7 THEN 1    -- 70% 数据在 city 1
        WHEN RAND() < 0.9 THEN 2    -- 20% 在 city 2
        ELSE FLOOR(3 + RAND() * 98)  -- 10% 分布在其余 98 个城市
    END,
    FLOOR(RAND() * 100000)
FROM information_schema.COLUMNS a
CROSS JOIN information_schema.COLUMNS b
LIMIT 100000;

-- 查询 city=1 仍然会扫描大量行
EXPLAIN SELECT * FROM `skewed_data` WHERE `city_id` = 1;
-- 虽然走了索引，但 70% 的行需要回表，优化器可能选择全表扫描

-- 优化: 分区表或覆盖索引
-- CREATE INDEX idx_city_pop ON skewed_data(city_id, population);
