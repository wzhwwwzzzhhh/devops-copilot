package com.devboss.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/** RAG 知识库服务：基于 ES 的向量检索与运维文档管理 */
@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);
    private static final int MAX_RESULTS = 3;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public RagService(@Qualifier("esRestTemplate") RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Value("${knowledge.es.uris:http://localhost:9200}")
    private String esUri;

    @Value("${knowledge.es.index:devops-knowledge}")
    private String index;

    @SuppressWarnings("unchecked")
    public String search(String question) {
        try {
            ObjectNode queryBody = objectMapper.createObjectNode();

            ObjectNode query = queryBody.putObject("query");
            ObjectNode bool = query.putObject("bool");
            ArrayNode should = bool.putArray("should");

            ObjectNode matchTitle = should.addObject().putObject("match");
            matchTitle.put("title", question);
            ObjectNode matchContent = should.addObject().putObject("match");
            matchContent.put("content", question);
            ObjectNode matchTags = should.addObject().putObject("match");
            matchTags.put("tags", question);

            queryBody.put("size", MAX_RESULTS);

            String url = esUri + "/" + index + "/_search";
            Map<String, Object> root = restTemplate.postForObject(url, queryBody, Map.class);

            if (root == null) {
                return "知识库检索无响应。";
            }

            Map<String, Object> hits = (Map<String, Object>) root.get("hits");
            if (hits == null) {
                return "知识库中暂无相关文档。";
            }

            java.util.List<Map<String, Object>> hitList =
                    (java.util.List<Map<String, Object>>) hits.get("hits");
            if (hitList == null || hitList.isEmpty()) {
                return "知识库中未找到相关文档。";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("【知识库检索结果】\n");
            for (Map<String, Object> hit : hitList) {
                Map<String, Object> source = (Map<String, Object>) hit.get("_source");
                if (source != null) {
                    double score = (double) hit.getOrDefault("_score", 0);
                    String title = (String) source.getOrDefault("title", "无标题");
                    String content = (String) source.getOrDefault("content", "");
                    sb.append("---\n");
                    sb.append("📄 ").append(title).append(" (相关度: ").append(String.format("%.2f", score)).append(")\n");
                    if (content.length() > 500) {
                        content = content.substring(0, 500) + "...";
                    }
                    sb.append(content).append("\n");
                }
            }

            log.info("知识库检索完成，命中 {} 条结果", hitList.size());
            return sb.toString();

        } catch (Exception e) {
            log.warn("知识库检索失败 ({}), 跳过知识检索", e.getMessage());
            return "知识库暂不可用。";
        }
    }

    @SuppressWarnings("unchecked")
    public boolean indexDocument(String docId, String title, String content, String[] tags) {
        try {
            ObjectNode doc = objectMapper.createObjectNode();
            doc.put("title", title);
            doc.put("content", content);
            ArrayNode tagsArray = doc.putArray("tags");
            for (String tag : tags) {
                tagsArray.add(tag);
            }

            if (!ensureIndexExists()) {
                log.error("索引不存在且无法创建，跳过文档写入");
                return false;
            }

            String url = esUri + "/" + index + "/_doc/" + docId;
            HttpEntity<ObjectNode> request = new HttpEntity<>(doc);
            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    request,
                    Map.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("文档已写入知识库: id={}, title={}", docId, title);
                return true;
            }
            log.error("文档写入失败: {}", response.getStatusCode());
            return false;

        } catch (Exception e) {
            log.error("文档写入知识库失败: id={}", docId, e);
            return false;
        }
    }

    private volatile boolean indexChecked = false;

    private boolean ensureIndexExists() {
        if (indexChecked) return true;

        try {
            String url = esUri + "/" + index;
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                indexChecked = true;
                log.debug("知识库索引已存在: {}", index);
                return true;
            }
        } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
            log.info("索引 {} 不存在，尝试创建...", index);
            if (createIndex()) {
                indexChecked = true;
                return true;
            }
            return false;
        } catch (org.springframework.web.client.ResourceAccessException e) {
            log.error("❌ 无法连接到 Elasticsearch: {}", esUri);
            return false;
        } catch (Exception e) {
            log.warn("检查索引状态失败: {}", e.getMessage());
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
            String url = esUri + "/" + index;

            ObjectNode mappings = objectMapper.createObjectNode();
            ObjectNode properties = mappings.putObject("properties");
            properties.putObject("title").put("type", "text");
            properties.putObject("content").put("type", "text");
            properties.putObject("tags").put("type", "keyword");

            ObjectNode body = objectMapper.createObjectNode();
            body.set("mappings", mappings);

            HttpEntity<ObjectNode> request = new HttpEntity<>(body);
            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.PUT,
                    request,
                    Map.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("知识库索引已创建: {}", index);
                Thread.sleep(1000);
                return true;
            }
            return false;
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            if (e.getStatusCode().value() == 400) {
                log.info("索引已存在: {}", index);
                return true;
            }
            log.warn("创建索引失败 (HTTP {}): {}", e.getStatusCode().value(), e.getMessage());
            return false;
        } catch (org.springframework.web.client.ResourceAccessException e) {
            log.warn("无法连接到 Elasticsearch ({}). 请确认 ES 服务已在 {} 启动并运行。", e.getMessage(), esUri);
            return false;
        } catch (Exception e) {
            log.warn("创建索引失败: {}", e.getMessage());
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public java.util.List<Map<String, Object>> listDocuments(int size) {
        try {
            ObjectNode queryBody = objectMapper.createObjectNode();
            queryBody.put("size", size);
            ObjectNode sort = queryBody.putObject("sort");
            sort.put("_score", "desc");

            String url = esUri + "/" + index + "/_search";
            Map<String, Object> root = restTemplate.postForObject(url, queryBody, Map.class);

            if (root == null) {
                return java.util.List.of();
            }

            Map<String, Object> hits = (Map<String, Object>) root.get("hits");
            if (hits == null) {
                return java.util.List.of();
            }

            java.util.List<Map<String, Object>> hitList =
                    (java.util.List<Map<String, Object>>) hits.get("hits");
            if (hitList == null || hitList.isEmpty()) {
                return java.util.List.of();
            }

            java.util.List<Map<String, Object>> docs = new java.util.ArrayList<>();
            for (Map<String, Object> hit : hitList) {
                Map<String, Object> doc = new java.util.HashMap<>();
                doc.put("docId", hit.get("_id"));
                Map<String, Object> source = (Map<String, Object>) hit.get("_source");
                if (source != null) {
                    doc.put("title", source.get("title"));
                    doc.put("content", source.get("content"));
                    doc.put("tags", source.get("tags"));
                }
                docs.add(doc);
            }
            return docs;

        } catch (Exception e) {
            log.warn("列出文档失败: {}", e.getMessage());
            return java.util.List.of();
        }
    }

    public boolean deleteDocument(String docId) {
        try {
            String url = esUri + "/" + index + "/_doc/" + docId;
            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.DELETE,
                    null,
                    Map.class
            );
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("文档已删除: id={}", docId);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.warn("删除文档失败: id={}, error={}", docId, e.getMessage());
            return false;
        }
    }
}
