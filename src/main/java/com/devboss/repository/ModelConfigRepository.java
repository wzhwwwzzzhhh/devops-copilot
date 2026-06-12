package com.devboss.repository;

import com.devboss.entity.ModelConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ModelConfigRepository extends JpaRepository<ModelConfig, Long> {
    List<ModelConfig> findByModelType(String modelType);
    ModelConfig findByModelTypeAndIsCurrent(String modelType, int isCurrent);
}
