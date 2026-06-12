-- ============================================
-- 场景 17: 临时表使用过多
-- 模拟: GROUP BY / DISTINCT 没有索引导致临时表
-- ============================================

-- 创建没有适合 GROUP BY 索引的表
DROP TABLE IF EXISTS `stats_log`;
CREATE TABLE `stats_log` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `event_type` VARCHAR(50),
    `user_id` INT,
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

INSERT INTO `stats_log` (`event_type`, `user_id`, `created_at`)
SELECT
    ELT(FLOOR(1 + RAND() * 20), 'page_view', 'click', 'scroll', 'submit', 'login', 'logout', 'error', 'warning', 'info', 'debug',
         'page_view', 'click', 'scroll', 'submit', 'login', 'logout', 'error', 'warning', 'info', 'debug'),
    FLOOR(RAND() * 5000),
    NOW() - INTERVAL FLOOR(RAND() * 30) DAY
FROM information_schema.COLUMNS
LIMIT 50000;

-- ❌ Using temporary; Using filesort
EXPLAIN SELECT `event_type`, COUNT(*) AS cnt
FROM `stats_log`
GROUP BY `event_type`
ORDER BY `cnt` DESC;

-- 优化: 添加联合索引
-- CREATE INDEX idx_event_type ON stats_log(event_type);
