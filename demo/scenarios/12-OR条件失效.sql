-- ============================================
-- 场景 12: OR 条件导致索引失效
-- 模拟: OR 连接的多个条件无法使用复合索引
-- ============================================

DROP TABLE IF EXISTS `products`;
CREATE TABLE `products` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `sku` VARCHAR(50) NOT NULL,
    `name` VARCHAR(200),
    `category` VARCHAR(50),
    `price` DECIMAL(10,2),
    `status` VARCHAR(20) DEFAULT 'ACTIVE',
    INDEX `idx_sku` (`sku`),
    INDEX `idx_category` (`category`)
) ENGINE=InnoDB;

INSERT INTO `products` (`sku`, `name`, `category`, `price`, `status`)
SELECT
    CONCAT('SKU-', LPAD(ROW_NUMBER() OVER (), 6, '0')),
    CONCAT('Product-', FLOOR(RAND() * 10000)),
    ELT(FLOOR(1 + RAND() * 10), 'Electronics', 'Clothing', 'Books', 'Home', 'Sports'),
    ROUND(RAND() * 1000, 2),
    'ACTIVE'
FROM information_schema.COLUMNS
LIMIT 10000;

-- ❌ OR 导致索引失效（MySQL 可能选择全表扫描）
EXPLAIN SELECT * FROM `products` WHERE `sku` = 'SKU-001000' OR `category` = 'Electronics';

-- ✅ 优化: 使用 UNION ALL 分开
EXPLAIN SELECT * FROM `products` WHERE `sku` = 'SKU-001000'
UNION ALL
SELECT * FROM `products` WHERE `category` = 'Electronics' AND `sku` != 'SKU-001000';

-- ✅ 优化: 使用 IN（如果条件都是等值）
EXPLAIN SELECT * FROM `products` WHERE `category` IN ('Electronics', 'Clothing');
