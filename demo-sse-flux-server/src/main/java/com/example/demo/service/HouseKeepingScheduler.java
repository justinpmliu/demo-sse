package com.example.demo.service;

import com.example.demo.dao.SseEventRepository;
import com.example.demo.dao.entity.SseEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class HouseKeepingScheduler {
    private final SseEventRepository sseEventRepository;

    @Value("${sse-server.housekeeping.history-in-hours}")
    private long historyInHours;

    @Scheduled(cron = "${sse-server.housekeeping.scheduler}")
    public void deleteSseEvents() {
        LocalDateTime before = LocalDateTime.now().minusHours(historyInHours);
        List<SseEvent> sseEvents = sseEventRepository.findByCreatedDttmBefore(before);
        if (!CollectionUtils.isEmpty(sseEvents)) {
            sseEventRepository.deleteAll(sseEvents);
            log.info("Deleted historical sseEvents: size={}", sseEvents.size());
        }
    }
}
