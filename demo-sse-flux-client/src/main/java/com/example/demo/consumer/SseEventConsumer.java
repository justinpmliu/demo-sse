package com.example.demo.consumer;

import com.example.demo.dao.SseLastEventIdRepository;
import com.example.demo.dao.entity.SseLastEventId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.util.Base64;

@Component
@RequiredArgsConstructor
@Slf4j
public class SseEventConsumer {
    private static final String URI_SUBSCRIBE = "/subscribe?name=%s&lastEventId=%s";

    @Value("${sse-server.url}")
    private String sseServerUrl;

    private final SseLastEventIdRepository sseLastEventIdRepository;

    @Async
    public void consume(String name) {
        WebClient client = WebClient.create(sseServerUrl);
        String lastEventId = this.getLastEventId(name);
        String uri = (lastEventId == null) ? String.format(URI_SUBSCRIBE, name, "") : String.format(URI_SUBSCRIBE, name, lastEventId);

        log.info("Star connecting SSE server, url={}", sseServerUrl + uri);

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
                    String data = event.data() == null ? null : new String(Base64.getDecoder().decode((event.data())));
                    log.info("Received: name={}, id={}, data={}, comment={}",
                            event.event(), event.id(), data, event.comment());

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
