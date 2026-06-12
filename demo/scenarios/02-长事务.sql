-- ============================================
-- 场景 2: 长事务不提交
-- 模拟: 事务长时间未提交，导致锁持有过久
-- ============================================

-- 开启一个事务但不提交
START TRANSACTION;
UPDATE `orders_no_index` SET `amount` = `amount` + 1 WHERE `id` = 1;
-- 注意: 不要执行 COMMIT 或 ROLLBACK

-- 在另一个会话中查看长事务:
-- SELECT * FROM information_schema.INNODB_TRX WHERE TIME_TO_SEC(TIMEDIFF(NOW(), trx_started)) > 60;
-- 优化: 设置合理的事务超时时间或排查业务代码
