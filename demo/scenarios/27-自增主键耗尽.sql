-- ============================================
-- 场景 27: 自增主键耗尽
-- 模拟: INT 自增主键接近最大值
-- ============================================

-- 检查当前自增值和最大值
SELECT
    TABLE_SCHEMA,
    TABLE_NAME,
    COLUMN_NAME,
    DATA_TYPE,
    IF(DATA_TYPE = 'int', 2147483647,
      IF(DATA_TYPE = 'bigint', 9223372036854775807,
        IF(DATA_TYPE = 'mediumint', 8388607,
          IF(DATA_TYPE = 'smallint', 32767,
            IF(DATA_TYPE = 'tinyint', 127, NULL))))) AS max_value,
    (SELECT AUTO_INCREMENT FROM information_schema.TABLES t
     WHERE t.TABLE_SCHEMA = c.TABLE_SCHEMA AND t.TABLE_NAME = c.TABLE_NAME) AS current_auto_increment,
    ROUND((SELECT AUTO_INCREMENT FROM information_schema.TABLES t
     WHERE t.TABLE_SCHEMA = c.TABLE_SCHEMA AND t.TABLE_NAME = c.TABLE_NAME) /
     IF(c.DATA_TYPE = 'int', 2147483647,
       IF(c.DATA_TYPE = 'bigint', 9223372036854775807, 1)) * 100, 2) AS usage_pct
FROM information_schema.COLUMNS c
WHERE c.EXTRA LIKE '%auto_increment%'
  AND c.TABLE_SCHEMA NOT IN ('mysql','performance_schema','information_schema','sys')
HAVING usage_pct > 80
ORDER BY usage_pct DESC;

-- 模拟: 创建接近耗尽的表
DROP TABLE IF EXISTS `near_full_autoincrement`;
CREATE TABLE `near_full_autoincrement` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `data` VARCHAR(100)
) ENGINE=InnoDB AUTO_INCREMENT=2147483640;

INSERT INTO `near_full_autoincrement` (`data`) VALUES ('test');
SELECT `id` FROM `near_full_autoincrement`;
-- 下一条插入会失败: Duplicate entry '2147483647' for key 'PRIMARY'

-- 优化: ALTER TABLE t MODIFY id BIGINT AUTO_INCREMENT;
