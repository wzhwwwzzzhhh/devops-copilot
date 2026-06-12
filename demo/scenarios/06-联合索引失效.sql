-- ============================================
-- 场景 6: 联合索引最左前缀失效
-- 模拟: 查询条件不满足最左前缀原则
-- ============================================

-- 创建带联合索引的表
DROP TABLE IF EXISTS `user_orders`;
CREATE TABLE `user_orders` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `user_id` INT NOT NULL,
    `status` VARCHAR(20) NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_user_status_time` (`user_id`, `status`, `created_at`)
) ENGINE=InnoDB;

-- 插入测试数据
INSERT INTO `user_orders` (`user_id`, `status`, `created_at`)
SELECT
    FLOOR(RAND() * 1000),
    ELT(FLOOR(1 + RAND() * 3), 'PENDING', 'PAID', 'COMPLETED'),
    NOW() - INTERVAL FLOOR(RAND() * 30) DAY
FROM information_schema.COLUMNS
LIMIT 50000;

-- ✅ 走索引（最左前缀）
EXPLAIN SELECT * FROM `user_orders` WHERE `user_id` = 100;

-- ✅ 走索引（user_id + status）
EXPLAIN SELECT * FROM `user_orders` WHERE `user_id` = 100 AND `status` = 'PAID';

-- ❌ 索引失效（跳过了 user_id，直接查 status）
EXPLAIN SELECT * FROM `user_orders` WHERE `status` = 'PAID';
-- 预期: type=ALL (全表扫描)

-- ❌ 索引失效（跳过了 status）
EXPLAIN SELECT * FROM `user_orders` WHERE `user_id` = 100 AND `created_at` > '2025-01-01';
-- 预期: type=ref（用了 user_id 部分但无法用到 created_at）

-- 优化: 根据实际查询模式调整联合索引字段顺序
