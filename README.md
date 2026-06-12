# DevOps Copilot - 智能运维 AI Agent

> AI Agent 运维助手，输入报障信息自动排查根因，支持真实数据库连接检测与故障自愈。

## 快速开始

### 前置条件

- JDK 17+
- MySQL 8.0+
- LLM 服务（Ollama / DeepSeek / 通义千问等任一 OpenAI 兼容接口）

### 启动

```bash
# 1. 克隆
git clone https://github.com/wzhwwwzzzhhh/devops-copilot.git
cd devops-copilot

# 2. 配置数据库（自动建表）
# 在 src/main/resources/application.yaml 中配置 datasource

# 3. 启动后端
mvn spring-boot:run

# 4. 启动前端（开发模式，热更新）
cd frontend
npm install
npm run dev
# 访问 http://localhost:5173
```

### 部署

```bash
# 构建前端并输出到 Spring Boot 静态目录
cd frontend && npm run build && cd ..

# 启动（生产模式）
mvn spring-boot:run
# 访问 http://localhost:8080
```

## 核心能力

### ⚙️ AI 自主决策引擎

ReAct 图编排架构，13 个独立节点实现"检测→诊断→决策→执行→验证"闭环。LLM（Ollama / DeepSeek）自主决定调用工具顺序，不可用时自动降级为数据驱动分析。

### 📊 真实数据源接入

通过 JDBC 直连 MySQL，实时采集 6 类指标：

| 指标 | 来源 | 用途 |
|------|------|------|
| 连接池 | `SHOW STATUS` | 检测连接池是否耗尽 |
| 运行中查询 | `PROCESSLIST` | 发现长事务、锁等待 |
| 慢查询日志 | `mysql.slow_log` | 实时追踪慢 SQL |
| SQL 性能分析 | `performance_schema` | 定位高耗时 SQL |
| 数据库大小 | `information_schema` | 空间分布概览 |
| 表健康检查 | `information_schema` | 缺主键 / 碎片检测 |

配置优先，Mock 降级 —— 开箱即用，有环境一键对接。

### 🔒 人机协作安全机制

- 高危操作（扩容 / 回滚 / 重启 / KILL / ALTER TABLE）走 Y/N 审批流程
- SQL 执行白名单拦截，仅允许 `ADD INDEX` / `ANALYZE` / `KILL` 等 7 种操作
- 审批超时自动取消

### 🔄 故障自愈闭环

Agent 发现缺索引表 → 建议修复 → 用户审批 → 安全执行 → 再次验证。支持 `KILL` 长连接，覆盖"发现问题→修复问题→验证恢复"全链路。

### 🎨 可视化体系

每条 AI 回答支持图文混排（文字报告 + ECharts 仪表盘 / 柱状图 / 饼图），独立数据看板一键打开，30 分钟慢查询实时追踪。

## 系统架构

```
用户报障 → StartNode → ReActNode (LLM 决策)
                          ├─ TOOL → CallToolNode → 执行工具
                          └─ FINAL → GenerateReportNode → END
                                      ↕ (含审批)
                               AwaitingApprovalNode
                               ExecuteActionNode
```

### 工具列表

| 工具 | 说明 | 数据源 |
|------|------|--------|
| `check_db_status` | 检查数据库状态 | MySQL JDBC |
| `query_metrics` | 查询服务监控指标 | Mock / Prometheus |
| `query_logs` | 检索错误日志 | 本地文件 / 远程 |
| `query_traces` | 链路追踪 | Mock |
| `list_deployments` | 查看部署状态 | Mock / K8s |
| `execute_action` | 执行扩容/回滚/重启 | 有状态 Mock |
| `execute_sql` | 执行安全 SQL（加索引等） | MySQL JDBC |
| `kill_query` | 终止长时间查询 | MySQL JDBC |
| `search_knowledge` | 搜索运维知识库 | Elasticsearch |

## 技术栈

| 维度 | 选择 |
|------|------|
| 主语言 | Java 17 |
| 框架 | Spring Boot 3.4 + Spring AI |
| 前端 | Vite + React 18 + TypeScript + Ant Design + ECharts |
| 持久层 | MySQL 8.0 + Redis 7 |
| 知识库 | Elasticsearch 8.x |
| LLM | Ollama / DeepSeek / 任意 OpenAI 兼容接口 |
| 构建 | Maven + npm |
