-- ============================================
-- 场景 8: 锁等待超时
-- 模拟: 事务 A 持有锁，事务 B 等待超时
-- ============================================

-- 会话 1:
START TRANSACTION;
UPDATE `orders_no_index` SET `amount` = 999 WHERE `id` = 1;
-- 不提交，持有锁

-- 会话 2:
SET innodb_lock_wait_timeout = 5;  -- 5 秒超时
START TRANSACTION;
UPDATE `orders_no_index` SET `amount` = 888 WHERE `id` = 1;
-- 等待 5 秒后报错: Lock wait timeout exceeded

-- 查看锁等待:
SHOW VARIABLES LIKE 'innodb_lock_wait_timeout';
SELECT * FROM information_schema.INNODB_LOCK_WAITS;

-- 优化: 缩短事务执行时间；设置合理的 lock_wait_timeout
