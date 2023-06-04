package com.example.demo.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter.SseEventBuilder;

import java.io.IOException;
import java.time.LocalTime;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@Slf4j
public class SseEmitterController{
    private ExecutorService nonBlockingService = Executors.newCachedThreadPool();
    private final SseEmitters emitters = new SseEmitters();
    private int customEventId = 0;
    private HashMap<Integer, String> customEvents = new LinkedHashMap<>();

    //short-lasting event
    @GetMapping("/sse/short-lasting")
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
                            .data("Short-lasting event @ " + LocalTime.now().toString()));
                }
                //send "complete" event to let client close connection
                emitter.send(SseEmitter.event()
                        .name("complete")
                        .data("Complete @ " + new Date()));
                emitter.complete();
            } catch (Exception e) {
                log.info(e.getMessage());
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    //long-lasting period events
    @GetMapping("/sse/long-lasting")
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
                            .data("Long-lasting event @ " + LocalTime.now().toString());
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

    @GetMapping(value = "/sse/custom-event", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter listenCustomEvent(@RequestHeader(value = "last-event-id", required = false) String lastEventId) {
        log.info("enter listenCustomEvent(), lastEventId={}", lastEventId);
        SseEmitter emitter = new SseEmitter(-1L);
        resendCustomEvents(lastEventId, emitter);
        return emitters.add(emitter);
    }

    @PostMapping("/sse/custom-event")
    public void publishCustomEvent(@RequestBody String message) {
        log.info("enter publishCustomEvent()");
        this.sendCustomEvent(message);
    }

    private synchronized void sendCustomEvent(String message) {
        customEventId++;
        customEvents.put(customEventId, message);

        SseEventBuilder event = SseEmitter.event()
                .id(String.valueOf(customEventId))
                .name("custom-event")
                .reconnectTime(15000)
                .data("Custom event @ " + message);
        emitters.send(event);
    }

    private synchronized void resendCustomEvents(String lastEventId, SseEmitter emitter) {
        int prevEventId = (lastEventId == null) ? 0 : Integer.parseInt(lastEventId);
        if (prevEventId < customEventId) {
            for (int i = prevEventId + 1; i <= customEventId; i++) {
                SseEventBuilder event = SseEmitter.event()
                        .id(String.valueOf(i))
                        .name("custom-event")
                        .reconnectTime(15000)
                        .data("Custom event @ " + this.customEvents.get(i));
                try {
                    emitter.send(event);
                } catch (IOException e) {
                    log.error(e.getMessage(), e);
                    emitter.completeWithError(e);
                    break;
                }
            }
        }
    }

}