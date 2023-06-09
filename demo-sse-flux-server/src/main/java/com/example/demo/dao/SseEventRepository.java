package com.example.demo.dao;

import com.example.demo.dao.entity.SseEvent;
import org.springframework.data.repository.CrudRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface SseEventRepository extends CrudRepository<SseEvent, Long> {
    List<SseEvent> findByNameAndIdAfterOrderById(String name, Long id);
    List<SseEvent> findByCreatedDttmBefore(LocalDateTime before);
}
