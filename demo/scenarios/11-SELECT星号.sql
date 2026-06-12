-- ============================================
-- 场景 11: SELECT * 导致的大量数据传输
-- 模拟: 查询不必要的列导致网络和 IO 开销
-- ============================================

-- ❌ 不好的写法
EXPLAIN SELECT * FROM `big_log` WHERE `user_id` = 100;

-- ✅ 好的写法（只查需要的列）
EXPLAIN SELECT `id`, `action`, `created_at` FROM `big_log` WHERE `user_id` = 100;

-- 注意: 如果表有 30 列，SELECT * 会传输所有列数据
-- 优化: 明确列出需要的列，避免 SELECT *
-- 如果经常只查某些列，可以建立覆盖索引
DROP INDEX IF EXISTS `idx_user_action_time` ON `big_log`;
CREATE INDEX `idx_user_action_time` ON `big_log`(`user_id`, `action`, `created_at`);
