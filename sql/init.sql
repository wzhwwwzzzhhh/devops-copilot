-- ============================================================
-- DevOps Copilot - 数据库初始化脚本
-- 适用数据库: MySQL 8.0+
-- 说明: 先创建数据库，再执行此脚本
-- ============================================================

CREATE DATABASE IF NOT EXISTS devops_copilot
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE devops_copilot;

-- ============================================================
-- 1. 会话表 (conversations)
-- ============================================================
CREATE TABLE IF NOT EXISTS conversations (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    session_id      VARCHAR(64)     NOT NULL,
    title           VARCHAR(256)                                    COMMENT '会话标题，自动从消息提取',
    user_message    TEXT,
    service_name    VARCHAR(128),
    status          VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE INDEX uk_session_id (session_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话会话表';

-- ============================================================
-- 2. 消息表 (messages)
-- ============================================================
CREATE TABLE IF NOT EXISTS messages (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    session_id      VARCHAR(64)     NOT NULL,
    role            VARCHAR(16)     NOT NULL,
    content         TEXT            NOT NULL,
    tokens          INT             DEFAULT 0,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_session_id (session_id),
    INDEX idx_session_role (session_id, role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对话消息表';

-- ============================================================
-- 3. 工具调用日志表 (tool_call_logs)
-- ============================================================
CREATE TABLE IF NOT EXISTS tool_call_logs (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    session_id      VARCHAR(64)     NOT NULL,
    tool_name       VARCHAR(64)     NOT NULL,
    input_summary   VARCHAR(512),
    output_summary  TEXT,
    output_length   INT             DEFAULT 0,
    duration_ms     BIGINT          NOT NULL DEFAULT 0,
    status          VARCHAR(16)     NOT NULL DEFAULT 'SUCCESS',
    error_message   TEXT,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,

    INDEX idx_session_id (session_id),
    INDEX idx_tool_name (tool_name),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工具调用日志表';

-- ============================================================
-- 4. 知识库文档表 (knowledge_documents)
-- ============================================================
CREATE TABLE IF NOT EXISTS knowledge_documents (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    doc_id          VARCHAR(64)     NOT NULL,
    title           VARCHAR(256)    NOT NULL,
    doc_type        VARCHAR(32)     NOT NULL DEFAULT 'TEXT',
    file_name       VARCHAR(256),
    file_size       BIGINT          DEFAULT 0,
    chunk_count     INT             DEFAULT 0,
    status          VARCHAR(16)     NOT NULL DEFAULT 'PENDING',
    description     VARCHAR(512),
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE INDEX uk_doc_id (doc_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库文档记录表';

-- ============================================================
-- 5. 服务连接配置表 (service_connections) ★ 新增
-- 作用: 用户通过 API 注册自己的中间件信息
-- 类型: mysql / redis / es / prometheus / k8s / log / service
-- ============================================================
CREATE TABLE IF NOT EXISTS service_connections (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(128)    NOT NULL                       COMMENT '连接名称，如"生产MySQL"',
    type            VARCHAR(32)     NOT NULL                       COMMENT '连接类型: mysql/redis/es/prometheus/k8s/log/service',
    host            VARCHAR(256)    NOT NULL                       COMMENT '主机地址或URL',
    port            INT             DEFAULT 0                      COMMENT '端口号',
    username        VARCHAR(128)                                    COMMENT '用户名',
    password        VARCHAR(256)                                    COMMENT '密码/API Key',
    properties      TEXT                                            COMMENT '额外属性(JSON格式)，如数据库名、日志路径等',
    tags            VARCHAR(512)                                    COMMENT '标签(逗号分隔)，如"order-service,production"',
    status          VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE'       COMMENT '状态: ACTIVE/INACTIVE',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_type (type),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='服务连接配置表（用户注册的中间件信息）';

-- ============================================================
-- 6. 模型配置表 (model_configs) ★ 新增
-- 作用: 用户配置的 LLM 模型信息
-- ============================================================
CREATE TABLE IF NOT EXISTS model_configs (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(128)    NOT NULL                       COMMENT '模型别名，如"通义千问"',
    provider        VARCHAR(32)     NOT NULL DEFAULT 'ollama'      COMMENT '供应商: ollama/openai/custom',
    base_url        VARCHAR(256)    NOT NULL                       COMMENT 'API地址',
    api_key         VARCHAR(512)                                    COMMENT 'API Key',
    model_name      VARCHAR(128)   NOT NULL                        COMMENT '模型名，如 qwen2.5 / gpt-4',
    model_type      VARCHAR(32)     NOT NULL DEFAULT 'chat'        COMMENT '模型类型: chat/embedding/reasoning',
    is_current      TINYINT(1)      DEFAULT 0                      COMMENT '是否为当前使用: 1-是 0-否',
    status          VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_model_type (model_type),
    INDEX idx_is_current (is_current)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型配置表';

-- ============================================================
-- 验证查询
-- ============================================================
-- SHOW TABLES;
