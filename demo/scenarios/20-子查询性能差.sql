-- ============================================
-- 场景 20: 半连接（SEMI JOIN）优化缺失
-- 模拟: EXISTS / IN 子查询性能差
-- ============================================

DROP TABLE IF EXISTS `customers`;
CREATE TABLE `customers` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(100),
    `email` VARCHAR(200)
) ENGINE=InnoDB;

INSERT INTO `customers` (`name`, `email`)
SELECT
    CONCAT('Customer-', FLOOR(RAND() * 100000)),
    CONCAT('cust', FLOOR(RAND() * 100000), '@example.com')
FROM information_schema.COLUMNS
LIMIT 20000;

-- ❌ 不好的 IN 子查询
EXPLAIN SELECT * FROM `customers` WHERE `id` IN (
    SELECT `user_id` FROM `orders` WHERE `amount` > 500
);

-- 优化: 转换为 JOIN
EXPLAIN SELECT DISTINCT c.*
FROM `customers` c
JOIN `orders` o ON c.id = o.user_id
WHERE o.amount > 500;

-- 或者确保关联字段有索引
-- CREATE INDEX idx_user_id ON orders(user_id);
