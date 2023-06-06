package com.example.demo.dao;

import com.example.demo.entity.SseLastEventId;
import org.springframework.data.repository.CrudRepository;

public interface SseLastEventIdRepository extends CrudRepository<SseLastEventId, Integer> {
    SseLastEventId findByName(String name);
}
