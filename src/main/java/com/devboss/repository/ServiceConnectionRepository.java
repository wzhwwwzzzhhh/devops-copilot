package com.devboss.repository;

import com.devboss.entity.ServiceConnection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ServiceConnectionRepository extends JpaRepository<ServiceConnection, Long> {
    List<ServiceConnection> findByType(String type);
    List<ServiceConnection> findByStatus(String status);
    List<ServiceConnection> findByTypeAndStatus(String type, String status);
    List<ServiceConnection> findByTagsContaining(String tag);
}
