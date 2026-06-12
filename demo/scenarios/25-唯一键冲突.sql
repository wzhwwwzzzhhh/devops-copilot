-- ============================================
-- 场景 25: 唯一键冲突
-- 模拟: INSERT ... ON DUPLICATE KEY 性能问题
-- ============================================

DROP TABLE IF EXISTS `unique_conflict`;
CREATE TABLE `unique_conflict` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `order_no` VARCHAR(50) NOT NULL UNIQUE,
    `status` VARCHAR(20),
    `version` INT DEFAULT 1
) ENGINE=InnoDB;

-- 插入初始数据
INSERT INTO `unique_conflict` (`order_no`, `status`)
SELECT
    CONCAT('ORD-', LPAD(ROW_NUMBER() OVER (), 8, '0')),
    'PAID'
FROM information_schema.COLUMNS
LIMIT 10000;

-- 并发插入相同 order_no 会触发唯一键冲突
-- 会话 1:
INSERT INTO `unique_conflict` (`order_no`, `status`) VALUES ('ORD-00001000', 'PAID')
ON DUPLICATE KEY UPDATE `status` = 'PAID', `version` = `version` + 1;

-- 如果插入频繁冲突，建议使用 INSERT IGNORE 或先 SELECT 再判断
-- 检查死锁日志:
-- SHOW ENGINE INNODB STATUS;

-- 优化: 使用 INSERT ... ON DUPLICATE KEY UPDATE 确保原子性
-- 批量插入时按 order_no 排序，减少间隙锁冲突
