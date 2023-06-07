package com.example.demo.consumer;

import com.example.demo.dao.SseLastEventIdRepository;
import com.example.demo.dao.entity.SseLastEventId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

@Component
@RequiredArgsConstructor
@Slf4j
public class CustomEventConsumer {
    private static final String BASE_URL = "http://localhost:8080/sse-server";
    private static final String EVENT_NAME = "custom-event";
    private static final String RESOURCE = "/subscribe?name=" + EVENT_NAME + "&lastEventId=";

    private final WebClient client = WebClient.create(BASE_URL);
    private final SseLastEventIdRepository sseLastEventIdRepository;

    @Async
    public void consume() {
        String lastEventId = this.getLastEventId(EVENT_NAME);
        log.info("Connect SSE server, name={}, lastEventId={}", EVENT_NAME, lastEventId);
        String uri = (lastEventId == null) ? RESOURCE : RESOURCE + lastEventId;

        ParameterizedTypeReference<ServerSentEvent<String>> type =
                new ParameterizedTypeReference<ServerSentEvent<String>>() {};
        Flux<ServerSentEvent<String>> stringStream = client.get()
                .uri(uri)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(type)
                .retry();

        stringStream.subscribe(
                event -> {
                    log.info("Received: name={}, id={}, data={}, comment={}",
                            event.event(), event.id(), event.data(), event.comment());

                    if (event.event() != null && event.id() != null) {
                        saveLastEventId(event.event(), event.id());
                    }
                },
                error -> log.error("Error retrieving content", error),
                () -> log.info("Completed")
        );
    }

    private void saveLastEventId(String name, String lastEventId) {
        if (StringUtils.hasText(name)) {
            SseLastEventId sseLastEventId = sseLastEventIdRepository.findByName(name);
            if (sseLastEventId != null) {
                sseLastEventId.setLastEventId(lastEventId);
            } else {
                sseLastEventId = new SseLastEventId(name, lastEventId);
            }
            sseLastEventIdRepository.save(sseLastEventId);
        }
    }

    private String getLastEventId(String name) {
        SseLastEventId sseLastEventId = null;
        if (StringUtils.hasText(name)) {
            sseLastEventId = sseLastEventIdRepository.findByName(name);
        }
        return (sseLastEventId == null) ? null : sseLastEventId.getLastEventId();
    }
}
