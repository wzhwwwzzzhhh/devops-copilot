package com.devboss.controller;

import com.devboss.knowledge.RagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/knowledge")
public class KnowledgeController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeController.class);

    private final RagService ragService;

    public KnowledgeController(RagService ragService) {
        this.ragService = ragService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        String docId = (String) body.getOrDefault("docId", "doc-" + System.currentTimeMillis());
        String title = (String) body.get("title");
        String content = (String) body.get("content");
        @SuppressWarnings("unchecked")
        String[] tags = ((java.util.List<String>) body.getOrDefault("tags", java.util.List.of()))
                .toArray(new String[0]);

        if (title == null || content == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "title 和 content 不能为空"));
        }

        boolean ok = ragService.indexDocument(docId, title, content, tags);
        if (ok) {
            return ResponseEntity.ok(Map.of("docId", docId, "title", title, "status", "created"));
        }
        return ResponseEntity.internalServerError().body(Map.of("error", "文档写入失败"));
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> search(@RequestParam("q") String query) {
        String result = ragService.search(query);
        return ResponseEntity.ok(Map.of("query", query, "result", result));
    }

    @GetMapping("/list")
    public ResponseEntity<java.util.List<Map<String, Object>>> list(
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ragService.listDocuments(size));
    }

    @DeleteMapping("/{docId}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable String docId) {
        boolean success = ragService.deleteDocument(docId);
        if (success) {
            return ResponseEntity.ok(Map.of("message", "文档已删除", "docId", docId));
        }
        return ResponseEntity.internalServerError().body(Map.of("error", "删除失败"));
    }
}
