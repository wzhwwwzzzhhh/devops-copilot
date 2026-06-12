-- ============================================
-- 场景 22: 字符集不一致导致索引失效
-- 模拟: JOIN 时两张表的字符集不同
-- ============================================

DROP TABLE IF EXISTS `charset_a`;
CREATE TABLE `charset_a` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `code` VARCHAR(50) CHARACTER SET utf8mb4,
    `value` VARCHAR(100),
    INDEX `idx_code` (`code`)
) ENGINE=InnoDB;

DROP TABLE IF EXISTS `charset_b`;
CREATE TABLE `charset_b` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `code` VARCHAR(50) CHARACTER SET latin1,  -- 不同字符集！
    `value` VARCHAR(100)
) ENGINE=InnoDB;

INSERT INTO `charset_a` VALUES (1, 'ABC', 'val1'), (2, 'DEF', 'val2');
INSERT INTO `charset_b` VALUES (1, 'ABC', 'val1'), (2, 'DEF', 'val2');

-- ❌ 字符集不同，索引无法使用
EXPLAIN SELECT a.*, b.value AS b_value
FROM `charset_a` a
JOIN `charset_b` b ON a.code = b.code;
-- 预期: type=ALL 全表扫描（字符集转换导致索引失效）

-- 优化: 统一字符集
-- ALTER TABLE charset_b MODIFY code VARCHAR(50) CHARACTER SET utf8mb4;
