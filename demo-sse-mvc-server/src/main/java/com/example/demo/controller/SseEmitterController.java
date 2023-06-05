package com.example.demo.controller;

import com.example.demo.dao.SseEventRepository;
import com.example.demo.entity.SseEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
public class SseEmitterController{
    private static final String NAME_CUSTOM_EVENT = "custom-event";

    private ExecutorService nonBlockingService = Executors.newCachedThreadPool();
    private final SseEmitters emitters = new SseEmitters();

    @Autowired
    private SseEventRepository sseEventRepository;

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

    @GetMapping("/" + NAME_CUSTOM_EVENT)
    public SseEmitter getCustomEvent(@RequestHeader(value = "last-event-id", required = false) String lastEventId,
                                     @RequestParam(value = "prevEventId", required = false) String prevEventId) {
        log.info("enter getCustomEvent(), prevEventId={}", prevEventId);
        SseEmitter emitter = new SseEmitter(-1L);
        prevEventId = (lastEventId != null) ? lastEventId : prevEventId;
        resendEvents(NAME_CUSTOM_EVENT, prevEventId, emitter);
        return emitters.add(emitter);
    }

    @PostMapping("/" + NAME_CUSTOM_EVENT)
    public void publishCustomEvent(@RequestBody String message) {
        SseEvent sseEvent = new SseEvent(NAME_CUSTOM_EVENT, message);
        this.saveAndSendEvent(sseEvent);
    }

    private synchronized void saveAndSendEvent(SseEvent sseEvent) {
        sseEventRepository.save(sseEvent);
        SseEventBuilder event = SseEmitter.event()
                .id(String.valueOf(sseEvent.getId()))
                .name(sseEvent.getName())
                .reconnectTime(15000)
                .data(sseEvent.getData());
        emitters.send(event);
    }

    private synchronized void resendEvents(String eventName, String prevEventId, SseEmitter emitter) {
        int lastEventId = StringUtils.hasText(prevEventId) ? Integer.parseInt(prevEventId) : 0;
        List<SseEvent> sseEvents = sseEventRepository.findByNameAndIdAfterOrderById(eventName, lastEventId);
        for (SseEvent sseEvent : sseEvents) {
            SseEventBuilder event = SseEmitter.event()
                    .id(String.valueOf(sseEvent.getId()))
                    .name(eventName)
                    .reconnectTime(15000)
                    .data(sseEvent.getData());
            try {
                emitter.send(event);
            } catch (IOException e) {
                emitter.completeWithError(e);
                log.info("Emitter failed: {}, cause: {}", emitter, e.getMessage());
                break;
            }
        }
    }

}