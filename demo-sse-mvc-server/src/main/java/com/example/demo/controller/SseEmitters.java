package com.example.demo.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Component
class SseEmitters {

    private final Map<String, List<SseEmitter>> emittersMap = new ConcurrentHashMap<>();

    SseEmitter add(String name) {
        return add(name, new SseEmitter(-1L));
    }

    SseEmitter add(String name, SseEmitter emitter) {
        List<SseEmitter> emitters = emittersMap.computeIfAbsent(name, k -> new CopyOnWriteArrayList<>());
        emitters.add(emitter);

        emitter.onCompletion(() -> {
            log.info("Emitter completed: {}", emitter);
            emitters.remove(emitter);
        });
        emitter.onTimeout(() -> {
            log.info("Emitter timed out: {}", emitter);
            emitter.complete();
            emitters.remove(emitter);
        });

        return emitter;
    }

    void send(String name, SseEmitter.SseEventBuilder builder) {
        send(name, emitter -> emitter.send(builder));
    }

    void send(SseEmitter.SseEventBuilder builder) {
        emittersMap.forEach((k, v) -> send(k, emitter -> emitter.send(builder)));
    }

    private void send(String name, SseEmitterConsumer<SseEmitter> consumer) {
        List<SseEmitter> emitters = emittersMap.get(name);
        if (!CollectionUtils.isEmpty(emitters)) {
            List<SseEmitter> failedEmitters = new ArrayList<>();
            emitters.forEach(emitter -> {
                try {
                    consumer.accept(emitter);
                } catch (Exception e) {
                    emitter.completeWithError(e);
                    failedEmitters.add(emitter);
                    log.info("Emitter failed: {}, event name: {}, cause: {}", emitter, name, e.getMessage());
                }
            });
            emitters.removeAll(failedEmitters);
        }
    }

    @FunctionalInterface
    private interface SseEmitterConsumer<T> {
        void accept(T t) throws IOException;
    }
}
