-- ============================================
-- 场景 10: 缺少主键的表
-- 模拟: 表没有显式主键，导致复制延迟和慢查询
-- ============================================

DROP TABLE IF EXISTS `no_pk_table`;
CREATE TABLE `no_pk_table` (
    `name` VARCHAR(100),
    `value` TEXT,
    `version` INT
) ENGINE=InnoDB;

INSERT INTO `no_pk_table` VALUES ('config1', 'value1', 1), ('config2', 'value2', 2);

-- InnoDB 会自动选择第一个唯一非空索引作为聚簇索引
-- 如果没有，则隐式创建 GEN_CLUST_INDEX

-- 查看缺少主键的表
SELECT t.TABLE_SCHEMA, t.TABLE_NAME, t.ENGINE
FROM information_schema.TABLES t
LEFT JOIN information_schema.KEY_COLUMN_USAGE k
    ON t.TABLE_SCHEMA = k.TABLE_SCHEMA
    AND t.TABLE_NAME = k.TABLE_NAME
    AND k.CONSTRAINT_NAME = 'PRIMARY'
WHERE t.TABLE_SCHEMA NOT IN ('mysql','performance_schema','information_schema','sys')
    AND k.COLUMN_NAME IS NULL
    AND t.TABLE_ROWS > 0;

-- 优化: ALTER TABLE no_pk_table ADD PRIMARY KEY (name);
