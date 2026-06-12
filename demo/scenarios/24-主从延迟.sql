-- ============================================
-- 场景 24: 主从延迟模拟
-- 模拟: 大量写操作导致主从延迟
-- ============================================

-- 检查主从延迟（在从库执行）
SHOW SLAVE STATUS\G
-- 关键字段:
--   Seconds_Behind_Master: 延迟秒数
--   Slave_IO_Running: Yes
--   Slave_SQL_Running: Yes
--   Relay_Log_Space: relay log 大小

-- 模拟: 在主库批量写入
DROP TABLE IF EXISTS `replication_test`;
CREATE TABLE `replication_test` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `data` VARCHAR(1000),
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

INSERT INTO `replication_test` (`data`)
SELECT REPEAT('X', 1000)
FROM information_schema.COLUMNS a
CROSS JOIN information_schema.COLUMNS b
CROSS JOIN information_schema.COLUMNS c
LIMIT 1000000;

-- 主从延迟常见原因:
-- 1. 从库写入慢（写入量超出从库处理能力）
-- 2. 大事务
-- 3. 从库 DDL 操作（如 ALTER TABLE）
-- 4. 从库硬件配置低于主库

-- 优化:
-- 1. 并行复制: SET GLOBAL slave_parallel_workers = 4;
-- 2. 监控 Seconds_Behind_Master
-- 3. 读写分离，读操作分散到多个从库
