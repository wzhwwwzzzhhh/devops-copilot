-- ============================================
-- 场景 23: 大事务导致 Binlog 延迟
-- 模拟: 一次 UPDATE 影响 100 万行
-- ============================================

-- 创建测试表
DROP TABLE IF EXISTS `big_transaction_test`;
CREATE TABLE `big_transaction_test` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `status` VARCHAR(20) DEFAULT 'OLD',
    `data` VARCHAR(100)
) ENGINE=InnoDB;

-- 插入 50 万行
INSERT INTO `big_transaction_test` (`data`)
SELECT REPEAT('X', 50)
FROM information_schema.COLUMNS a
CROSS JOIN information_schema.COLUMNS b
LIMIT 500000;

-- 大事务：一次更新大量行
START TRANSACTION;
UPDATE `big_transaction_test` SET `status` = 'NEW' WHERE `status` = 'OLD';
-- 影响行数大 → Binlog 大 → 复制延迟
COMMIT;

-- 检查 Binlog 文件大小
SHOW BINARY LOGS;

-- 优化: 分批更新
-- UPDATE big_transaction_test SET status = 'NEW' WHERE id BETWEEN 1 AND 10000;
-- UPDATE big_transaction_test SET status = 'NEW' WHERE id BETWEEN 10001 AND 20000;
-- ...
