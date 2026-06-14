package com.devboss.service;

import com.devboss.entity.ModelConfig;
import com.devboss.repository.ModelConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/** 模型配置服务：管理和查询 LLM 模型参数配置 */
@Service
public class ModelConfigService {

    private static final Logger log = LoggerFactory.getLogger(ModelConfigService.class);

    private final ModelConfigRepository repository;

    public ModelConfigService(ModelConfigRepository repository) {
        this.repository = repository;
    }

    public List<ModelConfig> findAll() {
        return repository.findAll();
    }

    public List<ModelConfig> findByType(String modelType) {
        return repository.findByModelType(modelType);
    }

    public ModelConfig findById(Long id) {
        return repository.findById(id).orElse(null);
    }

    public ModelConfig getCurrentChatModel() {
        return repository.findByModelTypeAndIsCurrent("chat", 1);
    }

    public ModelConfig create(ModelConfig config) {
        ModelConfig saved = repository.save(config);
        log.info("模型配置已添加: id={}, name={}, model={}", saved.getId(), saved.getName(), saved.getModelName());
        return saved;
    }

    public ModelConfig setCurrent(Long id) {
        ModelConfig target = repository.findById(id).orElse(null);
        if (target == null) return null;

        List<ModelConfig> sameType = repository.findByModelType(target.getModelType());
        for (ModelConfig mc : sameType) {
            mc.setIsCurrent(mc.getId().equals(id) ? 1 : 0);
            repository.save(mc);
        }

        log.info("当前模型已切换: type={}, model={}", target.getModelType(), target.getModelName());
        return target;
    }

    public void delete(Long id) {
        repository.deleteById(id);
        log.info("模型配置已删除: id={}", id);
    }
}
