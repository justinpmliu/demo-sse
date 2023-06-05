package com.example.demo.dao;

import com.example.demo.entity.SseEvent;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface SseEventRepository extends CrudRepository<SseEvent, Integer> {
    List<SseEvent> findByNameAndIdAfterOrderById(String name, Integer id);
}
