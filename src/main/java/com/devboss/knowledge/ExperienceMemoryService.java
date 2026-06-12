package com.devboss.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 跨会话经验记忆服务
 *
 * 将每次已解决的问题（根因、修复 SQL、验证结果）存入 ES，
 * 下次 Agent 检查数据库时检索 ES 查是否有同类历史，
 * 有则直接提示"上次遇到过这个问题，方案是：..."
 */
@Service
public class ExperienceMemoryService {

    private static final Logger log = LoggerFactory.getLogger(ExperienceMemoryService.class);
    private static final int MAX_RESULTS = 5;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public ExperienceMemoryService(@Qualifier("esRestTemplate") RestTemplate restTemplate,
                                   ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Value("${knowledge.es.uris:http://localhost:9200}")
    private String esUri;

    @Value("${knowledge.es.experience-index:devops-experiences}")
    private String experienceIndex;

    /** 问题类型分类 */
    public enum ProblemType {
        CONNECTION_POOL("连接池耗尽"),
        DEADLOCK("死锁"),
        SLOW_QUERY("慢查询"),
        LOCK_WAIT("锁等待"),
        HIGH_ERROR_RATE("高错误率"),
        DISK_FULL("磁盘满"),
        MEMORY_HIGH("内存高"),
        DEPLOYMENT("部署问题"),
        NETWORK("网络问题"),
        UNKNOWN("未知问题");

        public final String label;
        ProblemType(String label) { this.label = label; }

        public static ProblemType fromString(String s) {
            if (s == null) return UNKNOWN;
            String lower = s.toLowerCase();
            if (lower.contains("连接池") || lower.contains("connection_pool")) return CONNECTION_POOL;
            if (lower.contains("死锁") || lower.contains("deadlock")) return DEADLOCK;
            if (lower.contains("慢查询") || lower.contains("slow_query") || lower.contains("slow sql")) return SLOW_QUERY;
            if (lower.contains("锁等待") || lower.contains("lock_wait")) return LOCK_WAIT;
            if (lower.contains("错误率") || lower.contains("error_rate")) return HIGH_ERROR_RATE;
            if (lower.contains("磁盘") || lower.contains("disk")) return DISK_FULL;
            if (lower.contains("内存") || lower.contains("memory") || lower.contains("oom")) return MEMORY_HIGH;
            if (lower.contains("部署") || lower.contains("deploy") || lower.contains("回滚")) return DEPLOYMENT;
            if (lower.contains("网络") || lower.contains("network") || lower.contains("timeout")) return NETWORK;
            return UNKNOWN;
        }
    }

    /**
     * 保存一次故障排查经验到 ES
     *
     * @param problemType  问题类型
     * @param rootCause    根因描述
     * @param fixAction    修复操作描述（如 "添加索引", "KILL 连接", "扩容"）
     * @param fixSql       修复使用的 SQL（可选）
     * @param serviceName  涉及的服务名
     * @param verification 验证结果（是否解决）
     * @param summary      经验总结（由 LLM 生成）
     */
    public boolean saveExperience(String problemType, String rootCause, String fixAction,
                                  String fixSql, String serviceName, boolean verification,
                                  String summary) {
        try {
            if (!ensureIndexExists()) {
                log.warn("经验索引不存在且无法创建，跳过保存");
                return false;
            }

            String docId = "exp-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 10000);

            ObjectNode doc = objectMapper.createObjectNode();
            doc.put("type", problemType);
            doc.put("type_label", ProblemType.fromString(problemType).label);
            doc.put("root_cause", rootCause != null ? rootCause : "");
            doc.put("fix_action", fixAction != null ? fixAction : "");
            doc.put("fix_sql", fixSql != null ? fixSql : "");
            doc.put("service", serviceName != null ? serviceName : "");
            doc.put("verification", verification);
            doc.put("summary", summary != null ? summary : "");
            doc.put("resolved_at", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            doc.put("hit_count", 1);

            String url = esUri + "/" + experienceIndex + "/_doc/" + docId;
            HttpEntity<ObjectNode> request = new HttpEntity<>(doc);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("✅ 经验已保存: type={}, id={}", problemType, docId);
                return true;
            }
            log.warn("经验保存失败: HTTP {}", response.getStatusCode());
            return false;

        } catch (Exception e) {
            log.warn("经验保存异常: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 根据问题描述搜索相似的历史经验
     *
     * @param problemDescription 用户报障或问题描述
     * @return 格式化后的经验文本，用于注入到 LLM Prompt
     */
    public String searchSimilar(String problemDescription) {
        try {
            ObjectNode queryBody = buildSearchQuery(problemDescription);
            String url = esUri + "/" + experienceIndex + "/_search";
            Map<String, Object> root = restTemplate.postForObject(url, queryBody, Map.class);
            if (root == null) return "";

            Map<String, Object> hits = (Map<String, Object>) root.get("hits");
            if (hits == null) return "";

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> hitList = (List<Map<String, Object>>) hits.get("hits");
            if (hitList == null || hitList.isEmpty()) return "";

            StringBuilder sb = new StringBuilder();
            sb.append("\n【📋 历史经验参考 — 以下问题曾出现过，供参考】\n");

            int count = 0;
            for (Map<String, Object> hit : hitList) {
                if (count >= MAX_RESULTS) break;
                @SuppressWarnings("unchecked")
                Map<String, Object> source = (Map<String, Object>) hit.get("_source");
                if (source == null) continue;

                double score = (double) hit.getOrDefault("_score", 0);
                if (score < 0.5) continue; // 低相关度跳过

                count++;
                String typeLabel = (String) source.getOrDefault("type_label", "");
                String rootCause = (String) source.getOrDefault("root_cause", "");
                String fixAction = (String) source.getOrDefault("fix_action", "");
                String fixSql = (String) source.getOrDefault("fix_sql", "");
                String service = (String) source.getOrDefault("service", "");
                String summary = (String) source.getOrDefault("summary", "");
                String resolvedAt = (String) source.getOrDefault("resolved_at", "");

                sb.append("--- 历史案例 ").append(count).append(" ---\n");
                sb.append("📌 问题类型: ").append(typeLabel).append("\n");
                if (!service.isEmpty()) sb.append("🔧 涉及服务: ").append(service).append("\n");
                sb.append("🔍 根因: ").append(rootCause).append("\n");
                sb.append("🛠 修复方案: ").append(fixAction).append("\n");
                if (!fixSql.isEmpty()) {
                    sb.append("💾 修复 SQL: ").append(fixSql).append("\n");
                }
                if (!summary.isEmpty()) {
                    sb.append("📝 经验总结: ").append(summary.length() > 200 ? summary.substring(0, 200) + "..." : summary).append("\n");
                }
                sb.append("⏱ 解决时间: ").append(resolvedAt).append("\n");
                sb.append("📊 相关度: ").append(String.format("%.2f", score)).append("\n");
            }

            if (count > 0) {
                sb.append("【💡 建议】以上为历史类似问题的处理经验，可参考上述方案进行排查\n");
                log.info("经验检索完成，命中 {} 条相关记录", count);
                return sb.toString();
            }
            return "";

        } catch (Exception e) {
            log.debug("经验检索失败 ({}), 跳过", e.getMessage());
            return "";
        }
    }

    /**
     * 增加经验的命中次数（每次被检索命中时调用）
     */
    public void incrementHitCount(String docId) {
        try {
            String url = esUri + "/" + experienceIndex + "/_update/" + docId;
            ObjectNode body = objectMapper.createObjectNode();
            body.putObject("script")
                    .put("source", "ctx._source.hit_count = (ctx._source.hit_count ?: 0) + 1");
            HttpEntity<ObjectNode> request = new HttpEntity<>(body);
            restTemplate.exchange(url, HttpMethod.POST, request, Map.class);
        } catch (Exception e) {
            log.debug("更新命中次数失败: {}", e.getMessage());
        }
    }

    /**
     * 构建 ES 搜索查询
     * 对问题描述进行多字段匹配
     */
    private ObjectNode buildSearchQuery(String question) {
        ObjectNode queryBody = objectMapper.createObjectNode();
        ObjectNode query = queryBody.putObject("query");
        ObjectNode bool = query.putObject("bool");

        // must: 多字段文本匹配
        ArrayNode must = bool.putArray("must");
        ObjectNode multiMatch = must.addObject().putObject("multi_match");
        multiMatch.put("query", question);
        ArrayNode fields = multiMatch.putArray("fields");
        fields.add("root_cause^3").add("summary^2").add("fix_action").add("type_label").add("type");
        multiMatch.put("minimum_should_match", "50%");

        queryBody.put("size", MAX_RESULTS);
        queryBody.putObject("sort").put("_score", "desc");

        return queryBody;
    }

    /** 检索指定类型的所有经验（用于管理界面） */
    public List<Map<String, Object>> listExperiences(int size) {
        try {
            ObjectNode queryBody = objectMapper.createObjectNode();
            queryBody.put("size", size);
            queryBody.putObject("sort").put("resolved_at", "desc");

            String url = esUri + "/" + experienceIndex + "/_search";
            Map<String, Object> root = restTemplate.postForObject(url, queryBody, Map.class);
            if (root == null) return List.of();

            Map<String, Object> hits = (Map<String, Object>) root.get("hits");
            if (hits == null) return List.of();

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> hitList = (List<Map<String, Object>>) hits.get("hits");
            if (hitList == null) return List.of();

            List<Map<String, Object>> docs = new ArrayList<>();
            for (Map<String, Object> hit : hitList) {
                Map<String, Object> doc = new java.util.HashMap<>();
                doc.put("docId", hit.get("_id"));
                @SuppressWarnings("unchecked")
                Map<String, Object> source = (Map<String, Object>) hit.get("_source");
                if (source != null) {
                    doc.put("type", source.get("type_label"));
                    doc.put("root_cause", source.get("root_cause"));
                    doc.put("fix_action", source.get("fix_action"));
                    doc.put("service", source.get("service"));
                    doc.put("resolved_at", source.get("resolved_at"));
                    doc.put("hit_count", source.get("hit_count"));
                }
                docs.add(doc);
            }
            return docs;

        } catch (Exception e) {
            log.warn("列出经验失败: {}", e.getMessage());
            return List.of();
        }
    }

    // ============ 索引管理 ============

    private volatile boolean indexChecked = false;

    private boolean ensureIndexExists() {
        if (indexChecked) return true;
        try {
            String url = esUri + "/" + experienceIndex;
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                indexChecked = true;
                log.debug("经验索引已存在: {}", experienceIndex);
                return true;
            }
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
            log.info("经验索引 {} 不存在，创建中...", experienceIndex);
            if (createIndex()) {
                indexChecked = true;
                return true;
            }
            return false;
        } catch (Exception e) {
            log.warn("经验索引检查失败: {}", e.getMessage());
            if (createIndex()) {
                indexChecked = true;
                return true;
            }
            return false;
        }
        if (createIndex()) {
            indexChecked = true;
            return true;
        }
        return false;
    }

    private boolean createIndex() {
        try {
            String url = esUri + "/" + experienceIndex;

            ObjectNode mappings = objectMapper.createObjectNode();
            ObjectNode properties = mappings.putObject("properties");
            properties.putObject("type").put("type", "keyword");
            properties.putObject("type_label").put("type", "text");
            properties.putObject("root_cause").put("type", "text");
            properties.putObject("fix_action").put("type", "text");
            properties.putObject("fix_sql").put("type", "keyword");
            properties.putObject("service").put("type", "keyword");
            properties.putObject("summary").put("type", "text");
            properties.putObject("resolved_at").put("type", "date");
            properties.putObject("hit_count").put("type", "integer");

            ObjectNode body = objectMapper.createObjectNode();
            body.set("mappings", mappings);

            HttpEntity<ObjectNode> request = new HttpEntity<>(body);
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.PUT, request, Map.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("✅ 经验索引已创建: {}", experienceIndex);
                Thread.sleep(1000);
                return true;
            }
            return false;
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            if (e.getStatusCode().value() == 400) {
                log.info("经验索引已存在: {}", experienceIndex);
                return true;
            }
            log.warn("创建经验索引失败: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.warn("创建经验索引失败: {}", e.getMessage());
            return false;
        }
    }
}
