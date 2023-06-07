package com.example.demo.consumer;

import com.example.demo.dao.SseLastEventIdRepository;
import com.example.demo.dao.entity.SseLastEventId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.sse.InboundSseEvent;
import javax.ws.rs.sse.SseEventSource;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Component
@RequiredArgsConstructor
@Slf4j
public class CustomEventConsumer {
    private static final String EVENT_NAME = "custom-event";
    private static final String URL = "http://localhost:8080/sse-server/subscribe?name=" + EVENT_NAME + "&lastEventId=";

    private final SseLastEventIdRepository sseLastEventIdRepository;

    @Async
    public void consume() {
        String lastEventId = this.getLastEventId(EVENT_NAME);
        log.info("Connect SSE server, name={}, lastEventId={}", EVENT_NAME, lastEventId);

        String url = (lastEventId == null) ? URL : URL + lastEventId;
        Client client = ClientBuilder.newBuilder().readTimeout(1, TimeUnit.HOURS).build();
        WebTarget target = client.target(url);

        try (SseEventSource eventSource = SseEventSource.target(target).reconnectingEvery(15, TimeUnit.SECONDS).build()) {
            eventSource.register(onEvent, onError, onComplete);
            eventSource.open();

            //Consuming events
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            log.info(e.getMessage());
            Thread.currentThread().interrupt();
        }

        client.close();
        log.info("Client closed");
    }

    // A new event is received
    private Consumer<InboundSseEvent> onEvent = inboundSseEvent -> {
        log.info("name: [{}] , id: [{}] , data: [{}], comment: [{}]",
                inboundSseEvent.getName(), inboundSseEvent.getId(), inboundSseEvent.readData(), inboundSseEvent.getComment());
        if (inboundSseEvent.getName() != null && inboundSseEvent.getId() != null) {
            this.saveLastEventId(inboundSseEvent.getName(), inboundSseEvent.getId());
        }
    };

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

    //Error
    private Consumer<Throwable> onError = throwable -> log.error(throwable.getMessage(), throwable);

    //Connection close and there is nothing to receive
    private Runnable onComplete = () -> log.info("Connection closed");
}
