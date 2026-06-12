-- ============================================
-- 场景 16: 大表 JOIN 无索引
-- 模拟: 大表 JOIN 没有使用索引
-- ============================================

DROP TABLE IF EXISTS `users`;
CREATE TABLE `users` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(100),
    `email` VARCHAR(200)
) ENGINE=InnoDB;

DROP TABLE IF EXISTS `orders`;
CREATE TABLE `orders` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `user_id` INT NOT NULL,
    `amount` DECIMAL(10,2),
    `order_date` DATETIME DEFAULT CURRENT_TIMESTAMP
    -- 注意: user_id 没有索引
) ENGINE=InnoDB;

-- 插入数据
INSERT INTO `users` (`name`, `email`)
SELECT
    CONCAT('User-', FLOOR(RAND() * 10000)),
    CONCAT('user', FLOOR(RAND() * 10000), '@example.com')
FROM information_schema.COLUMNS
LIMIT 10000;

INSERT INTO `orders` (`user_id`, `amount`)
SELECT
    FLOOR(1 + RAND() * 10000),
    ROUND(RAND() * 1000, 2)
FROM information_schema.COLUMNS
CROSS JOIN information_schema.COLUMNS b
LIMIT 100000;

-- ❌ JOIN 无索引（orders.user_id 无索引）
EXPLAIN SELECT u.name, SUM(o.amount) AS total
FROM `users` u
JOIN `orders` o ON u.id = o.user_id
WHERE u.id BETWEEN 100 AND 200
GROUP BY u.id;

-- 优化: 在 orders.user_id 上建立索引
-- CREATE INDEX idx_user_id ON orders(user_id);
