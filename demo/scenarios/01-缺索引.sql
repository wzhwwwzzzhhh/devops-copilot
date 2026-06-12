-- ============================================
-- 场景 1: 缺索引导致的慢查询
-- 模拟: WHERE 条件字段没有索引，导致全表扫描
-- ============================================

-- 创建测试表
DROP TABLE IF EXISTS `orders_no_index`;
CREATE TABLE `orders_no_index` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `user_id` INT NOT NULL,
    `amount` DECIMAL(10,2) NOT NULL,
    `status` VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- 插入大量测试数据
INSERT INTO `orders_no_index` (`user_id`, `amount`, `status`, `created_at`)
SELECT
    FLOOR(RAND() * 10000),
    ROUND(RAND() * 1000, 2),
    ELT(FLOOR(1 + RAND() * 5), 'PENDING', 'PAID', 'SHIPPED', 'COMPLETED', 'CANCELLED'),
    NOW() - INTERVAL FLOOR(RAND() * 365) DAY
FROM information_schema.COLUMNS a
CROSS JOIN information_schema.COLUMNS b
LIMIT 100000;

-- 慢查询 — status 字段没有索引
EXPLAIN SELECT * FROM `orders_no_index` WHERE `status` = 'PENDING' ORDER BY `created_at` DESC;
-- 预期: type=ALL (全表扫描), rows=100000, Extra="Using where; Using filesort"
-- 优化: CREATE INDEX idx_status ON orders_no_index(status);
