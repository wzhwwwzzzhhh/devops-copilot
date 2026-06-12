package com.devboss.knowledge;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeSeeder {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeSeeder.class);

    private final RagService ragService;

    public KnowledgeSeeder(RagService ragService) {
        this.ragService = ragService;
    }

    @PostConstruct
    void init() {
        log.info("开始初始化知识库文档...");
        seedDocuments();
    }

    private void seedDocuments() {
        ragService.indexDocument("doc-001", "数据库连接池耗尽处理方案",
                """
                问题现象：应用报错 "Connection is not available, request timed out"，数据库连接池使用率达到100%。
                
                原因分析：
                1. 慢 SQL 导致连接长时间占用不释放
                2. 突发流量超过连接池上限
                3. 连接泄漏（未正确关闭连接）
                
                处理步骤：
                1. 紧急扩容连接池大小（如 50→100）
                2. 使用 SHOW PROCESSLIST 查找慢 SQL
                3. 为频繁查询的字段添加索引
                4. 对 /create 类接口添加限流
                
                预防措施：
                - 设置合理的连接池超时时间（建议 30s）
                - 开启慢查询日志 (long_query_time=1s)
                - 定期使用 pt-query-digest 分析慢 SQL
                """,
                new String[]{"数据库", "连接池", "慢SQL", "超时"});

        ragService.indexDocument("doc-002", "服务高错误率排查指南",
                """
                问题现象：接口错误率突然飙升超过阈值（>5%）
                
                排查步骤：
                1. 查看监控面板，确认错误率趋势和影响范围
                2. 检查错误日志，定位具体异常类型
                   - HTTP 5xx: 服务端异常
                   - Connection Timeout: 网络/连接池问题
                   - NullPointerException: 代码缺陷
                3. 检查最近部署记录，是否有新版本上线
                4. 检查依赖服务（数据库、缓存、下游服务）是否正常
                5. 如有慢 SQL，优先处理数据库问题
                
                应急处理：
                - 严重（>20%）：立即回滚最近变更
                - 中等（5-20%）：扩容 + 排查根因
                - 轻微（<5%）：监控观察，定位修复
                """,
                new String[]{"错误率", "告警", "排障", "SLA"});

        ragService.indexDocument("doc-003", "服务部署与回滚标准流程",
                """
                版本规范：
                - 生产版本: v2.x.x
                - 预发版本: v2.x.x-rc
                - 开发版本: v2.x.x-SNAPSHOT
                
                发布流程：
                1. 代码合并到 release 分支
                2. CI 自动构建镜像并推送仓库
                3. 预发环境验证（至少观察 30 分钟）
                4. 生产环境分批发布（先 1 台→50%→100%）
                5. 发布后监控观察 15 分钟
                
                回滚条件：
                - 错误率上升超过 5%
                - P99 延迟翻倍
                - 关键接口不可用
                
                回滚操作：
                1. 执行回滚到上一稳定版本
                2. 通知相关方确认
                3. 记录回滚原因和时间
                """,
                new String[]{"部署", "回滚", "发布", "CI/CD"});

        ragService.indexDocument("doc-004", "NullPointerException 处理指南",
                """
                问题现象：日志中出现 NullPointerException，服务返回 500 错误。
                
                常见原因：
                1. 从数据库/缓存查询的结果为 null，未做判空
                2. API 入参校验不严格
                3. 配置项未正确加载
                
                排查方法：
                1. 通过错误堆栈定位具体行号
                2. 检查对应代码中哪些变量可能为 null
                3. 查看调用方传入的参数是否完整
                
                修复建议：
                1. 增加入参校验（@NotNull, @Valid）
                2. 使用 Optional 或判空处理
                3. 添加防御性编程：Objects.requireNonNull()
                4. 完善单元测试覆盖边界情况
                """,
                new String[]{"NPE", "空指针", "代码缺陷", "异常"});

        ragService.indexDocument("doc-005", "系统健康巡检标准",
                """
                巡检周期：每日自动巡检
                
                检查项：
                1. 服务指标：CPU < 80%, 内存 < 85%, 错误率 < 1%
                2. 数据库：连接池使用率 < 70%, 无慢 SQL (>2s)
                3. 磁盘：使用率 < 80%
                4. 证书：SSL 证书有效期 > 30 天
                5. 日志：无连续 ERROR 日志
                
                异常分级：
                - CRITICAL: 服务不可用 / 数据丢失 / 严重安全事件
                - WARNING: 指标超阈值但服务正常
                - INFO: 常规提醒
                
                处理时效：
                - CRITICAL: 15 分钟内响应
                - WARNING: 1 小时内响应
                - INFO: 下一个工作日
                """,
                new String[]{"巡检", "健康检查", "运维", "监控"});

        log.info("知识库初始化完成");
    }
}
