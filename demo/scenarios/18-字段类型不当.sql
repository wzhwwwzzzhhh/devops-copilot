-- ============================================
-- 场景 18: 不恰当的字段类型
-- 模拟: 使用 TEXT/BLOB 作为主键或索引
-- ============================================

DROP TABLE IF EXISTS `bad_schema`;
CREATE TABLE `bad_schema` (
    `id` VARCHAR(255) PRIMARY KEY,  -- UUID 做主键，过长
    `name` TEXT NOT NULL,            -- 可以用 VARCHAR
    `description` TEXT,
    `content` LONGTEXT,
    `status` VARCHAR(100),           -- 状态字段可以用 ENUM 或 TINYINT
    `created_at` DATETIME,
    INDEX `idx_status` (`status`),   -- VARCHAR(100) 索引太长
    INDEX `idx_name` (`name`(10))    -- TEXT 前缀索引，选择性可能不足
) ENGINE=InnoDB;

-- 检查字段类型使用情况:
SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, CHARACTER_MAXIMUM_LENGTH
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'bad_schema';

-- 优化建议:
-- 1. 主键用 INT/BIGINT 自增
-- 2. TEXT 只能做前缀索引，考虑 VARCHAR
-- 3. ENUM/TINYINT 代替 VARCHAR(100) 状态
-- 4. VARCHAR 尽量短
