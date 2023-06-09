package com.example.demo.controller;

import com.example.demo.bean.EventData;
import com.example.demo.dao.SseEventRepository;
import com.example.demo.dao.entity.SseEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder;

import java.io.IOException;
import java.time.LocalTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/sse-server")
@RequiredArgsConstructor
@Slf4j
public class SseServerController {
    private final SseEmitters emitters;
    private final SseEventRepository sseEventRepository;

    private static final long RECONNECT_TIME = 15000L;

    @GetMapping("/subscribe")
    public ResponseEntity<SseEmitter> subscribe(
            @RequestHeader(value = "last-event-id", required = false) String headerLastEventId,
            @RequestParam(value = "name") String name,
            @RequestParam(value = "lastEventId", required = false) String queryLastEventId) {

        log.info("Start subscribing [{}]: headerLastEventId={}, queryLastEventId={}", name, headerLastEventId, queryLastEventId);
        SseEmitter emitter = new SseEmitter(-1L);
        String lastEventId = (headerLastEventId != null) ? headerLastEventId : queryLastEventId;
        try {
            this.sendComment(emitter, String.format("Connected @ %s, lastEventId=%s", LocalTime.now().toString(), lastEventId));
            resendEvents(name, lastEventId, emitter);
            emitters.add(name, emitter);
            log.info("{} subscribed to [{}]: lastEventId={}", emitter, name, lastEventId);
            return ResponseEntity.ok(emitter);
        } catch (IOException e) {
            log.error(String.format("Failed to subscribe to [%s]", name), e);
            emitter.completeWithError(e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(emitter);
        }
    }

    @PostMapping(value = "/publish")
    public ResponseEntity<Map<String, Object>> publish(@RequestBody EventData eventData) {
        SseEvent sseEvent = new SseEvent(eventData.getName(), eventData.getRefId(), eventData.getData());
        this.saveAndSendEvent(sseEvent);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", sseEvent.getId());
        response.put("name", sseEvent.getName());
        if (StringUtils.hasText(sseEvent.getRefId())) {
            response.put("refId", sseEvent.getRefId());
        }
        response.put("createdDttm", sseEvent.getCreatedDttm());

        return ResponseEntity.ok(response);
    }

    private synchronized void saveAndSendEvent(SseEvent sseEvent) {
        sseEventRepository.save(sseEvent);
        emitters.send(sseEvent.getName(), this.buildEvent(sseEvent));
        log.info("Sent event to [{}]: id={}, refId={}", sseEvent.getName(), sseEvent.getId(), sseEvent.getRefId());
    }

    private synchronized void resendEvents(String name, String prevEventId, SseEmitter emitter) throws IOException {
        long lastEventId = StringUtils.hasText(prevEventId) ? Long.parseLong(prevEventId) : 0;
        List<SseEvent> sseEvents = sseEventRepository.findByNameAndIdAfterOrderById(name, lastEventId);
        for (SseEvent sseEvent : sseEvents) {
            emitter.send(this.buildEvent(sseEvent));
        }
    }

    private void sendComment(SseEmitter emitter, String comment) throws IOException {
        SseEventBuilder event = SseEmitter.event()
                .reconnectTime(RECONNECT_TIME)
                .comment(comment);
        emitter.send(event);
    }

    private SseEventBuilder buildEvent(SseEvent sseEvent) {
        return SseEmitter.event()
                .id(String.valueOf(sseEvent.getId()))
                .name(sseEvent.getName())
                .reconnectTime(RECONNECT_TIME)
                .data(Base64.getEncoder().encodeToString(sseEvent.getData().getBytes()));
    }
}