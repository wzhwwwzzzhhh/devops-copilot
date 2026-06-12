package com.devboss.controller;

import com.devboss.entity.ModelConfig;
import com.devboss.service.ModelConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/models")
public class ModelController {

    private static final Logger log = LoggerFactory.getLogger(ModelController.class);

    private final ModelConfigService service;

    public ModelController(ModelConfigService service) {
        this.service = service;
    }

    @GetMapping
    public List<ModelConfig> list(@RequestParam(required = false) String type) {
        if (type != null && !type.isEmpty()) {
            return service.findByType(type);
        }
        return service.findAll();
    }

    @GetMapping("/current")
    public ResponseEntity<ModelConfig> currentChatModel() {
        ModelConfig current = service.getCurrentChatModel();
        if (current == null) return ResponseEntity.ok().build();
        return ResponseEntity.ok(current);
    }

    @PostMapping
    public ModelConfig create(@RequestBody ModelConfig config) {
        log.info("添加模型配置: name={}, provider={}, model={}", config.getName(), config.getProvider(), config.getModelName());
        return service.create(config);
    }

    @PutMapping("/{id}/activate")
    public ResponseEntity<ModelConfig> activate(@PathVariable Long id) {
        ModelConfig activated = service.setCurrent(id);
        if (activated == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(activated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/providers")
    public List<Map<String, String>> providers() {
        return List.of(
                Map.of("provider", "ollama", "desc", "Ollama（本地部署）"),
                Map.of("provider", "openai", "desc", "OpenAI 兼容接口（通义千问/DeepSeek 等）")
        );
    }
}
