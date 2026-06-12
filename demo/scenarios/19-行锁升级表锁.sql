-- ============================================
-- 场景 19: 行锁升级为表锁
-- 模拟: 没有索引的字段更新导致行锁升级表锁
-- ============================================

DROP TABLE IF EXISTS `lock_test`;
CREATE TABLE `lock_test` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `group_id` INT NOT NULL,  -- 没有索引
    `value` INT
) ENGINE=InnoDB;

INSERT INTO `lock_test` (`group_id`, `value`)
VALUES (1, 100), (1, 200), (2, 300), (2, 400);

-- 会话 1:
START TRANSACTION;
UPDATE `lock_test` SET `value` = 999 WHERE `group_id` = 1;  -- group_id 无索引，可能锁全表

-- 会话 2:
UPDATE `lock_test` SET `value` = 888 WHERE `group_id` = 2;  -- 被阻塞！尽管是不同的 group

-- 因为 group_id 没有索引，InnoDB 需要全表扫描找到匹配行
-- 所有扫描过的行都会被加锁

-- 检查锁情况:
-- SELECT * FROM performance_schema.data_locks;

-- 优化: 为 group_id 加索引
-- CREATE INDEX idx_group ON lock_test(group_id);
