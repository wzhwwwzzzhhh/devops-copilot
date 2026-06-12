-- ============================================
-- 场景 3: 死锁
-- 模拟: 两个事务互相等待对方持有的锁
-- ============================================

-- 会话 1 (connection 1):
START TRANSACTION;
UPDATE `orders_no_index` SET `amount` = 200 WHERE `id` = 1;
-- 此时持有 id=1 的行锁
-- 等待会话 2 完成后:

-- 会话 2 (connection 2):
START TRANSACTION;
UPDATE `orders_no_index` SET `amount` = 300 WHERE `id` = 2;
-- 此时持有 id=2 的行锁

-- 会话 1 执行:
UPDATE `orders_no_index` SET `amount` = 400 WHERE `id` = 2;
-- 等待会话 2 释放 id=2 的锁

-- 会话 2 执行:
UPDATE `orders_no_index` SET `amount` = 500 WHERE `id` = 1;
-- 等待会话 1 释放 id=1 的锁 → DEADLOCK

-- 检查死锁:
-- SHOW ENGINE INNODB STATUS;
-- 优化: 保持一致的加锁顺序；使用索引减少锁范围
