# DevOps Copilot - MySQL 运维能力说明

## 概述

DevOps Copilot 通过 `ServiceConnection` 中配置的 MySQL 连接，直接连到真实数据库，采集多项关键指标，再由 AI 模型进行分析诊断。支持单库和多实例场景。

---

## 采集的数据维度（5 类）

### 1. 连接池状态

| 指标 | 说明 |
|------|------|
| `active` | 当前活跃连接数 |
| `max` | 最大连接数（`max_connections`） |
| `usage_percent` | 连接池使用率，>80% 自动标记 `DEGRADED` |

**典型用途**：判断是否连接池耗尽导致服务不可用。

**Agent 示例输出**：
```
连接池使用率 3%（活跃 6/最大 151），状态 HEALTHY
```

---

### 2. 运行中查询（TOP 10）

查询 `information_schema.PROCESSLIST`，排除 `Sleep` 连接，按耗时降序取前 10 条。

| 字段 | 说明 |
|------|------|
| `id` | 连接 ID |
| `user` | 执行用户 |
| `host` | 客户端地址 |
| `db` | 目标数据库 |
| `time_sec` | 已执行秒数 |
| `state` | 当前状态（Sending data / Creating sort index 等） |
| `sql` | SQL 语句（截取前 200 字符） |

**典型用途**：实时发现长事务、慢查询、锁等待。

**Agent 示例输出**：
```
发现 1 条长时间运行查询（28s）：SELECT *, SLEEP(30) FROM messages
```

---

### 3. 数据库大小概览

查询 `information_schema.TABLES`，按数据库分组统计。

| 字段 | 说明 |
|------|------|
| `name` | 数据库名 |
| `size_mb` | 总大小（数据 + 索引，MB） |
| `table_count` | 表数量 |

**典型用途**：快速了解实例整体空间分布，发现异常膨胀的库。

**Agent 示例输出**：
```
devops_copilot: 0.5MB, 6 张表
mydb: 128MB, 123 张表
```

---

### 4. SQL 性能分析

从 `performance_schema.events_statements_summary_by_digest` 取 TOP 10 耗时 SQL。

| 字段 | 说明 |
|------|------|
| `sql_pattern` | SQL 模板（参数已抽象化，截取 120 字符） |
| `execution_count` | 累计执行次数 |
| `total_time_sec` | 累计总耗时（秒） |
| `avg_time_ms` | 单次平均耗时（毫秒） |
| `rows_examined_per_row_returned` | **扫描行数 / 返回行数** 比值 |
| `total_rows_examined` | 累计扫描行数 |
| `no_index_count` | 未使用索引的执行次数 |
| `warning`（条件触发） | 比值 >100 时标记 "大量扫描，缺少索引" |

**典型用途**：定位最耗时的 SQL、发现缺少索引的全表扫描。

**Agent 示例输出**：
```
发现慢查询：INSERT INTO messages（执行 49 次，平均 0.9ms）
⚠️ 大量扫描：SELECT content FROM messages WHERE ...（扫描/返回比 150:1，建议加索引）
```

---

### 5. 表结构健康检查

检查有数据的表（`TABLE_ROWS > 0`），按大小降序取前 15 张。

| 字段 | 说明 |
|------|------|
| `schema` / `table` | 库名 / 表名 |
| `engine` | 存储引擎 |
| `rows` | 行数 |
| `size_mb` | 表大小（MB） |
| `fragmentation_mb` | 碎片大小（>0 时显示） |
| `primary_key_column` | 主键列名 |
| `warning`（条件触发） | 没有主键时标记 "缺少主键" |

**典型用途**：发现没有主键的表（InnoDB 会使用隐藏主键，导致性能问题）。

**Agent 示例输出**：
```
⚠️ messages 表无主键，建议添加自增主键
```

---

## 用户提问示例

### 基础查询

| 问题 | 预期行为 |
|------|---------|
| "检查数据库状态" | 采集全部 5 类数据，生成综合报告 |
| "数据库连接池怎么样？" | 只关注连接池使用率 |
| "有没有慢查询？" | 分析 SQL 性能数据，给出索引建议 |
| "看看数据库有什么问题" | 综合诊断，标记 DEGRADED/无主键/缺少索引等问题 |

### 高级场景

| 场景 | 操作 | Agent 输出 |
|------|------|------------|
| 模拟慢查询 | 执行 `SELECT SLEEP(30)` | "发现 1 条查询已运行 28 秒，建议 kill" |
| 造大量数据 | `INSERT INTO messages ...` 插入数千行 | "messages 表行数增长到 N，建议添加索引" |
| 模拟连接池满 | 开启多个连接不关闭 | "连接池使用率已达 XX%，建议排查连接泄漏" |

---

## 技术实现

### 数据采集

```
用户提问 → StartNode → ReActNode → LLM 决策
                                    ↓ (LLM 选择 check_db_status)
                              CallToolNode → DatabaseService.getDbStatus()
                                                  ↓ (有 MySQL 连接?)
                                              ┌────┴────┐
                                          真实 MySQL     Mock JSON
                                              │
                              queryRealMysql() 采集 5 类数据
                              ─────────────────────────────
                              1. SHOW STATUS (连接池)
                              2. PROCESSLIST (运行中查询)
                              3. information_schema.TABLES (库大小)
                              4. performance_schema.events_statements_summary (SQL 分析)
                              5. information_schema.TABLES + KEY_COLUMN_USAGE (表健康)
```

### 降级策略

- 有 `type=mysql` 的服务连接 → 优先连真实数据库
- 连接失败（网络/认证） → 自动降级读 Mock JSON
- 无 MySQL 连接 → 直接读 Mock

### 安全说明

- 连接使用 `?useSSL=false&connectTimeout=5000`，5 秒超时断开
- JDBC 直连，仅执行 **查询语句**（`SELECT` / `SHOW`），不执行任何 DDL/DML
- 连接池监控仅读取 `SHOW STATUS` 和 `information_schema` 等系统视图

---

## 规划中的功能

- **图形化展示**：将查询结果以图表形式（饼图/柱状图/列表）在前端展示
- **历史趋势**：定期采集数据库指标，绘制连接池使用率趋势图
- **告警触发**：当连接池超过阈值、发现慢查询时主动推送告警
- **更多数据库**：支持 PostgreSQL、Redis 等
