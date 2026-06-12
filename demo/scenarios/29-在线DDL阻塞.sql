-- ============================================
-- 场景 29: 在线 DDL 阻塞写入
-- 模拟: ALTER TABLE 添加索引导致锁表
-- ============================================

-- 创建大表
DROP TABLE IF EXISTS `ddl_block_test`;
CREATE TABLE `ddl_block_test` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `user_id` INT NOT NULL,
    `data` VARCHAR(100),
    `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

INSERT INTO `ddl_block_test` (`user_id`, `data`)
SELECT
    FLOOR(RAND() * 100000),
    REPEAT('X', 50)
FROM information_schema.COLUMNS a
CROSS JOIN information_schema.COLUMNS b
LIMIT 200000;

-- 会话 1: 执行 DDL（传统方式，会锁表）
-- ALTER TABLE ddl_block_test ADD INDEX idx_user(user_id);
-- 注意: 在 MySQL 5.6+，默认使用 ALGORITHM=INPLACE, LOCK=NONE

-- 会话 2: 尝试写入（如果 DDL 锁表，会被阻塞）
INSERT INTO `ddl_block_test` (`user_id`, `data`) VALUES (1, 'blocked?');
-- 查看是否有锁等待:
SHOW PROCESSLIST;

-- 🛠 安全的 DDL 方式:
-- ALTER TABLE ddl_block_test ADD INDEX idx_user(user_id), ALGORITHM=INPLACE, LOCK=NONE;
-- 使用 gh-ost / pt-online-schema-change 做无锁 DDL

-- 检查 DDL 进度:
SELECT * FROM information_schema.INNODB_ALTER_TABLE_PROGRESS;
