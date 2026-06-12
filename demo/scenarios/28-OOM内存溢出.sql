-- ============================================
-- 场景 28: MySQL OOM 内存溢出
-- 模拟: 并发大查询消耗所有内存
-- ============================================

-- 检查当前内存使用情况
SHOW VARIABLES LIKE '%buffer%';
SHOW VARIABLES LIKE '%cache%';
SHOW VARIABLES LIKE '%pool%';

-- 查看各功能内存分配:
SELECT
    VARIABLE_NAME,
    VARIABLE_VALUE,
    ROUND(VARIABLE_VALUE / 1024 / 1024, 1) AS size_mb
FROM performance_schema.global_variables
WHERE VARIABLE_NAME IN (
    'innodb_buffer_pool_size',
    'innodb_log_buffer_size',
    'key_buffer_size',
    'query_cache_size',
    'tmp_table_size',
    'max_heap_table_size',
    'sort_buffer_size',
    'join_buffer_size',
    'read_buffer_size',
    'read_rnd_buffer_size',
    'thread_cache_size'
);

-- 模拟大查询占用内存
DROP TABLE IF EXISTS `oom_test`;
CREATE TABLE `oom_test` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `data` LONGTEXT
) ENGINE=InnoDB;

-- 插入大对象
INSERT INTO `oom_test` (`data`)
SELECT REPEAT('X', 1000000)  -- 1MB 每行
FROM information_schema.COLUMNS
LIMIT 100;

-- 并发排序大查询会消耗大量 sort_buffer_size
-- SELECT * FROM oom_test ORDER BY id DESC;

-- 监控内存:
-- SHOW ENGINE INNODB STATUS;
-- 优化: 合理设置各 buffer 大小；限制大查询并发；启用 SWAP
