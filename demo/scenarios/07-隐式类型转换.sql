-- ============================================
-- 场景 7: 隐式类型转换导致索引失效
-- 模拟: WHERE 条件字段类型不匹配
-- ============================================

-- 创建表，user_id 是 VARCHAR
DROP TABLE IF EXISTS `users_varchar_id`;
CREATE TABLE `users_varchar_id` (
    `user_id` VARCHAR(20) NOT NULL PRIMARY KEY,
    `name` VARCHAR(100),
    `email` VARCHAR(200),
    INDEX `idx_email` (`email`)
) ENGINE=InnoDB;

INSERT INTO `users_varchar_id` VALUES ('10001', 'Alice', 'alice@example.com');
INSERT INTO `users_varchar_id` VALUES ('10002', 'Bob', 'bob@example.com');

-- ❌ 类型不匹配（用数字查询字符串字段）
EXPLAIN SELECT * FROM `users_varchar_id` WHERE `user_id` = 10001;
-- 预期: type=ALL（隐式转换导致索引失效）

-- ✅ 类型匹配
EXPLAIN SELECT * FROM `users_varchar_id` WHERE `user_id` = '10001';
-- 预期: type=const

-- 优化: 保持查询条件类型与字段类型一致
