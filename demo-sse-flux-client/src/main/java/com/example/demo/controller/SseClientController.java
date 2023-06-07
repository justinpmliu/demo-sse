package com.example.demo.controller;

import com.example.demo.dao.SseLastEventIdRepository;
import com.example.demo.dao.entity.SseLastEventId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;import org.springframework.scheduling.annotation.Async;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/sse-consumer")
@RequiredArgsConstructor
@Slf4j
public class SseClientController {
    private WebClient client = WebClient.create("http://localhost:8080/sse-server");
    private static final String EVENT_NAME = "custom-event";
    private static final String URL = "/subscribe?name=" + EVENT_NAME + "&lastEventId=";

    private final SseLastEventIdRepository sseLastEventIdRepository;

    @GetMapping("/custom-event")
    public String launchCustomEventConsumer() {
        consumeEvents();
        return "Launched client to consume custom events. Check the logs...";
    }

    @Async
    public void consumeEvents() {
        ParameterizedTypeReference<ServerSentEvent<String>> type =
                new ParameterizedTypeReference<ServerSentEvent<String>>() {};
        Flux<ServerSentEvent<String>> stringStream = client.get()
                .uri(this.getUrl(URL))
                .accept(MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(type)
                .retry();

        stringStream.subscribe(
                event -> {
                    log.info("Received: name = [{}], id = [{}] , data = [{}]", event.event(), event.id(), event.data());
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

    private String getUrl(String url) {
        String lastEventId = this.getLastEventId(EVENT_NAME);
        log.info("Connect SSE server, name={}, lastEventId={}", EVENT_NAME, lastEventId);
        return lastEventId == null ? url : url + lastEventId;
    }

    private String getLastEventId(String name) {
        if (StringUtils.hasText(name)) {
            SseLastEventId sseLastEventId = sseLastEventIdRepository.findByName(name);
            if (sseLastEventId != null) {
                return sseLastEventId.getLastEventId();
            }
        }
        return null;
    }


}
