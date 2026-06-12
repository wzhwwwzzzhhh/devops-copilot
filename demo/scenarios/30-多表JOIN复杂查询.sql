-- ============================================
-- 场景 30: 多表 JOIN 复杂查询
-- 模拟: 5 表 JOIN 导致性能灾难
-- ============================================

-- 创建 5 张关联表
DROP TABLE IF EXISTS `t_order_items`, `t_orders`, `t_products`, `t_categories`, `t_suppliers`;

CREATE TABLE `t_categories` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(100)
) ENGINE=InnoDB;

CREATE TABLE `t_suppliers` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(100)
) ENGINE=InnoDB;

CREATE TABLE `t_products` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(200),
    `category_id` INT,
    `supplier_id` INT,
    `price` DECIMAL(10,2),
    INDEX `idx_category` (`category_id`),
    INDEX `idx_supplier` (`supplier_id`)
) ENGINE=InnoDB;

CREATE TABLE `t_orders` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `order_no` VARCHAR(50),
    `user_id` INT,
    `total` DECIMAL(12,2),
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

CREATE TABLE `t_order_items` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `order_id` INT,
    `product_id` INT,
    `quantity` INT,
    `price` DECIMAL(10,2),
    INDEX `idx_order` (`order_id`),
    INDEX `idx_product` (`product_id`)
) ENGINE=InnoDB;

-- 插入少量数据
INSERT INTO `t_categories` VALUES (1, '电子产品'), (2, '服装'), (3, '食品');
INSERT INTO `t_suppliers` VALUES (1, '供应商A'), (2, '供应商B'), (3, '供应商C');
INSERT INTO `t_products` VALUES (1, '手机', 1, 1, 5999), (2, '电脑', 1, 2, 8999), (3, 'T恤', 2, 3, 99);
INSERT INTO `t_orders` VALUES (1, 'ORD001', 100, 6098, '2026-01-01'), (2, 'ORD002', 101, 99, '2026-01-02');
INSERT INTO `t_order_items` VALUES (1, 1, 1, 1, 5999), (2, 1, 2, 1, 99), (3, 2, 3, 1, 99);

-- ❌ 复杂 5 表 JOIN
EXPLAIN SELECT
    o.order_no,
    c.name AS category,
    p.name AS product,
    s.name AS supplier,
    oi.quantity,
    oi.price
FROM t_orders o
JOIN t_order_items oi ON o.id = oi.order_id
JOIN t_products p ON oi.product_id = p.id
JOIN t_categories c ON p.category_id = c.id
JOIN t_suppliers s ON p.supplier_id = s.id
WHERE o.created_at > '2026-01-01'
ORDER BY o.created_at DESC;

-- 优化: 确保 JOIN 字段都有索引
-- 如果数据量大，先过滤再 JOIN
-- 使用 STRAIGHT_JOIN 固定驱动表

-- 检查所有 JOIN 字段索引:
SELECT
    TABLE_NAME, COLUMN_NAME, INDEX_NAME
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND (TABLE_NAME, COLUMN_NAME) IN (
    ('t_orders', 'id'), ('t_order_items', 'order_id'), ('t_order_items', 'product_id'),
    ('t_products', 'id'), ('t_categories', 'id'), ('t_suppliers', 'id')
  );
