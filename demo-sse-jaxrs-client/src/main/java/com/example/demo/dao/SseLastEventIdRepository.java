package com.example.demo.dao;

import com.example.demo.dao.entity.SseLastEventId;
import org.springframework.data.repository.CrudRepository;

public interface SseLastEventIdRepository extends CrudRepository<SseLastEventId, Long> {
    SseLastEventId findByName(String name);
}
