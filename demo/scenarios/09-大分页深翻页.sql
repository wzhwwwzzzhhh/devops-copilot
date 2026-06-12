-- ============================================
-- 场景 9: 大表分页深翻页
-- 模拟: LIMIT 100000, 20 导致大量回表扫描
-- ============================================

DROP TABLE IF EXISTS `big_log`;
CREATE TABLE `big_log` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `user_id` INT NOT NULL,
    `action` VARCHAR(100),
    `ip_address` VARCHAR(45),
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB;

-- 插入 20 万条数据
INSERT INTO `big_log` (`user_id`, `action`, `ip_address`, `created_at`)
SELECT
    FLOOR(RAND() * 10000),
    ELT(FLOOR(1 + RAND() * 5), 'LOGIN', 'LOGOUT', 'VIEW', 'EDIT', 'DELETE'),
    CONCAT(FLOOR(RAND() * 255), '.', FLOOR(RAND() * 255), '.', FLOOR(RAND() * 255), '.', FLOOR(RAND() * 255)),
    NOW() - INTERVAL FLOOR(RAND() * 90) DAY
FROM information_schema.COLUMNS a
CROSS JOIN information_schema.COLUMNS b
LIMIT 200000;

-- ❌ 深翻页（OFFSET 大导致扫描大量行）
EXPLAIN SELECT * FROM `big_log` ORDER BY `created_at` DESC LIMIT 100000, 20;
-- 预期: 扫描 + 排序大量行

-- ✅ 优化: 子查询分批 + 覆盖索引
EXPLAIN SELECT * FROM `big_log`
WHERE `id` < (SELECT `id` FROM `big_log` ORDER BY `created_at` DESC LIMIT 100000, 1)
ORDER BY `created_at` DESC LIMIT 20;

-- ✅ 优化: 游标分页（基于上一次的 id）
EXPLAIN SELECT * FROM `big_log` WHERE `id` > 150000 ORDER BY `id` ASC LIMIT 20;
