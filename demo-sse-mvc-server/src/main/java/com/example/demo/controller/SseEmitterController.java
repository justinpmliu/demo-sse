package com.example.demo.controller;

import com.example.demo.bean.SseEventMessage;
import com.example.demo.dao.SseEventRepository;
import com.example.demo.entity.SseEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder;

import java.io.IOException;
import java.time.LocalTime;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/sse")
@Slf4j
@RequiredArgsConstructor
public class SseEmitterController{
    private final SseEmitters emitters;
    private final SseEventRepository sseEventRepository;

    private ExecutorService nonBlockingService = Executors.newCachedThreadPool();

    //short-lasting event
    @GetMapping("/short-lasting")
    public SseEmitter getShortLastingEvent(@RequestHeader(value = "last-event-id", required = false) String lastEventId) {
        log.info("enter getShortLastingEvent(), lastEventId={}", lastEventId);
        SseEmitter emitter = new SseEmitter();

        nonBlockingService.execute(() -> {
            try {
                int eventId = (lastEventId == null ? 0 : Integer.parseInt(lastEventId)) + 1;
                for (int i = eventId; i < eventId + 5; i++) {
                    emitter.send(SseEmitter.event()
                            .id(String.valueOf(i))
                            .name("message")
                            .data("short-lasting event @ " + LocalTime.now().toString()));
                }
                //send "complete" event to let client close connection
                emitter.send(SseEmitter.event()
                        .name("complete")
                        .data("complete @ " + new Date()));
                emitter.complete();
            } catch (Exception e) {
                log.info(e.getMessage());
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    //long-lasting period events
    @GetMapping("/long-lasting")
    public SseEmitter getLongLastingEvent(@RequestHeader(value = "last-event-id", required = false) String lastEventId) {
        log.info("enter getLongLastingEvent(), lastEventId={}", lastEventId);

        SseEmitter emitter = new SseEmitter(-1L);
        ExecutorService executor = Executors.newSingleThreadExecutor();

        executor.execute(() -> {
            try {
                int eventId = (lastEventId == null ? 0 : Integer.parseInt(lastEventId)) + 1;
                for (int i = eventId; true; i++) {
                    SseEventBuilder event = SseEmitter.event()
                            .id(String.valueOf(i))
                            .name("long-lasting-event")
                            .reconnectTime(15000)
                            .data("long-lasting event @ " + LocalTime.now().toString());
                    emitter.send(event);
                    Thread.sleep(2000);
                }
            } catch (Exception ex) {
                log.info(ex.getMessage());
                emitter.completeWithError(ex);
            }
        });
        return emitter;
    }

    @GetMapping("/subscribe")
    public SseEmitter subscribe(@RequestHeader(value = "last-event-id", required = false) String sseLastEventId,
                                     @RequestParam(value = "name") String name,
                                     @RequestParam(value = "lastEventId", required = false) String lastEventId) {
        log.info("enter subscribe(), name={}, sseLastEventId={}, lastEventId={}", name, sseLastEventId, lastEventId);
        SseEmitter emitter = new SseEmitter(-1L);
        lastEventId = (sseLastEventId != null) ? sseLastEventId : lastEventId;
        if (!resendEvents(name, lastEventId, emitter)) {
            this.sendComment(name, emitter, "Connected @ " + LocalTime.now().toString());
        }
        return emitters.add(name, emitter);
    }

    @PostMapping(value = "/publish")
    public void publish(@RequestBody SseEventMessage message) {
        SseEvent sseEvent = new SseEvent(message.getName(), message.getData());
        this.saveAndSendEvent(sseEvent);
    }

    private synchronized void saveAndSendEvent(SseEvent sseEvent) {
        sseEventRepository.save(sseEvent);
        SseEventBuilder event = SseEmitter.event()
                .id(String.valueOf(sseEvent.getId()))
                .name(sseEvent.getName())
                .reconnectTime(15000)
                .data(sseEvent.getData());
        emitters.send(sseEvent.getName(), event);
        log.info("sent {}", sseEvent);
    }

    private synchronized boolean resendEvents(String name, String prevEventId, SseEmitter emitter) {
        boolean isResent = false;
        int lastEventId = StringUtils.hasText(prevEventId) ? Integer.parseInt(prevEventId) : 0;
        List<SseEvent> sseEvents = sseEventRepository.findByNameAndIdAfterOrderById(name, lastEventId);
        for (SseEvent sseEvent : sseEvents) {
            SseEventBuilder event = SseEmitter.event()
                    .id(String.valueOf(sseEvent.getId()))
                    .name(name)
                    .reconnectTime(15000)
                    .data(sseEvent.getData());
            try {
                emitter.send(event);
                isResent = true;
            } catch (IOException e) {
                emitter.completeWithError(e);
                log.info("Emitter {} failed, event name: {}, cause: {}", emitter, name, e.getMessage());
                break;
            }
        }
        return isResent;
    }

    private void sendComment(String name, SseEmitter emitter, String comment) {
        SseEventBuilder event = SseEmitter.event()
                .reconnectTime(15000)
                .comment(comment);
        try {
            emitter.send(event);
        } catch (IOException e) {
            emitter.completeWithError(e);
            log.info("Emitter {} failed, event name: {}, cause: {}", emitter, name, e.getMessage());
        }
    }

}