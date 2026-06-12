package com.devboss.repository;

import com.devboss.entity.ToolCallLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ToolCallLogRepository extends JpaRepository<ToolCallLog, Long> {
    List<ToolCallLog> findBySessionIdOrderByCreatedAtAsc(String sessionId);
    List<ToolCallLog> findByToolNameOrderByCreatedAtDesc(String toolName);
}
