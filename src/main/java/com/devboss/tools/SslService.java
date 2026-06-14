package com.devboss.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** SSL 证书检查服务：域名证书有效期与状态批量检测 */
@Service
public class SslService {

    private static final Logger log = LoggerFactory.getLogger(SslService.class);
    private final ObjectMapper objectMapper;

    public SslService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 模拟指定域名的 SSL 证书检查
     */
    public String checkCert(String domain) {
        try {
            ObjectNode cert = objectMapper.createObjectNode();
            cert.put("domain", domain);
            cert.put("issuer", "Let's Encrypt");
            cert.put("valid_from", "2026-01-01T00:00:00Z");
            cert.put("valid_to", "2026-12-31T23:59:59Z");
            cert.put("days_remaining", 180);
            cert.put("san_count", 3);
            cert.put("algorithm", "SHA-256withRSA");
            cert.put("is_expired", false);
            cert.put("is_expiring_soon", false);

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(cert);
        } catch (Exception e) {
            log.error("检查 SSL 证书失败, domain: {}", domain, e);
            return "{\"error\": \"检查 SSL 证书失败: " + e.getMessage() + "\"}";
        }
    }

    /**
     * 模拟检查所有已配置域名的 SSL 证书
     */
    public String checkDomains() {
        try {
            ArrayNode domains = objectMapper.createArrayNode();

            // example.com: 180 days remaining (OK)
            ObjectNode d1 = domains.addObject();
            d1.put("domain", "example.com");
            d1.put("issuer", "Let's Encrypt");
            d1.put("valid_from", "2026-01-01T00:00:00Z");
            d1.put("valid_to", "2026-12-31T23:59:59Z");
            d1.put("days_remaining", 180);
            d1.put("san_count", 5);
            d1.put("algorithm", "SHA-256withRSA");
            d1.put("status", "ok");

            // api.example.com: 45 days remaining (warning - expires in < 60d)
            ObjectNode d2 = domains.addObject();
            d2.put("domain", "api.example.com");
            d2.put("issuer", "Let's Encrypt");
            d2.put("valid_from", "2025-10-15T00:00:00Z");
            d2.put("valid_to", "2026-07-27T23:59:59Z");
            d2.put("days_remaining", 45);
            d2.put("san_count", 3);
            d2.put("algorithm", "SHA-256withRSA");
            d2.put("status", "warning");

            // admin.example.com: 12 days remaining (critical - expires in < 30d)
            ObjectNode d3 = domains.addObject();
            d3.put("domain", "admin.example.com");
            d3.put("issuer", "DigiCert");
            d3.put("valid_from", "2025-06-01T00:00:00Z");
            d3.put("valid_to", "2026-06-24T23:59:59Z");
            d3.put("days_remaining", 12);
            d3.put("san_count", 2);
            d3.put("algorithm", "SHA-384withECDSA");
            d3.put("status", "critical");

            // blog.example.com: 200 days remaining (OK)
            ObjectNode d4 = domains.addObject();
            d4.put("domain", "blog.example.com");
            d4.put("issuer", "Let's Encrypt");
            d4.put("valid_from", "2026-02-01T00:00:00Z");
            d4.put("valid_to", "2026-12-29T23:59:59Z");
            d4.put("days_remaining", 200);
            d4.put("san_count", 1);
            d4.put("algorithm", "SHA-256withRSA");
            d4.put("status", "ok");

            // *.example.com: 90 days remaining (warning - wildcard)
            ObjectNode d5 = domains.addObject();
            d5.put("domain", "*.example.com");
            d5.put("issuer", "Let's Encrypt");
            d5.put("valid_from", "2026-03-01T00:00:00Z");
            d5.put("valid_to", "2026-09-10T23:59:59Z");
            d5.put("days_remaining", 90);
            d5.put("san_count", 1);
            d5.put("algorithm", "SHA-256withRSA");
            d5.put("status", "warning");

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(domains);
        } catch (Exception e) {
            log.error("检查所有域名 SSL 证书失败", e);
            return "{\"error\": \"检查所有域名 SSL 证书失败: " + e.getMessage() + "\"}";
        }
    }

    /**
     * 获取 SSL 全量状态
     * 组合域名列表和汇总信息
     */
    public String getFullStatus() {
        try {
            ObjectNode result = objectMapper.createObjectNode();
            result.put("configured", true);

            // Domains
            String domainsJson = checkDomains();
            ArrayNode domains = (ArrayNode) objectMapper.readTree(domainsJson);
            result.set("domains", domains);

            // Summary
            ObjectNode summary = result.putObject("summary");
            int total = domains.size();
            int ok = 0;
            int warning = 0;
            int critical = 0;
            int expired = 0;

            for (int i = 0; i < domains.size(); i++) {
                String status = domains.get(i).path("status").asText("ok");
                switch (status) {
                    case "critical":
                        critical++;
                        break;
                    case "warning":
                        warning++;
                        break;
                    case "expired":
                        expired++;
                        break;
                    default:
                        ok++;
                        break;
                }
            }

            summary.put("total", total);
            summary.put("ok", ok);
            summary.put("warning", warning);
            summary.put("critical", critical);
            summary.put("expired", expired);

            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (Exception e) {
            log.error("获取 SSL 全量状态失败", e);
            return "{\"error\": \"获取 SSL 全量状态失败: " + e.getMessage() + "\"}";
        }
    }
}
