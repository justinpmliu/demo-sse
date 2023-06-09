package com.example.demo.consumer;

import com.example.demo.dao.SseLastEventIdRepository;
import com.example.demo.dao.entity.SseLastEventId;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.sse.InboundSseEvent;
import javax.ws.rs.sse.SseEventSource;
import java.util.Base64;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

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
        String lastEventId = this.getLastEventId(name);
        String url = sseServerUrl + (lastEventId == null ? String.format(URI_SUBSCRIBE, name, "") : String.format(URI_SUBSCRIBE, name, lastEventId));
        log.info("Start connecting SSE server, url={}", url);

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
    private Consumer<InboundSseEvent> onEvent = event -> {
        String data = event.readData() == null ? null : new String(Base64.getDecoder().decode((event.readData())));
        log.info("Received: name={}, id={}, data={}, comment={}",
                event.getName(), event.getId(), data, event.getComment());
        if (event.getName() != null && event.getId() != null) {
            this.saveLastEventId(event.getName(), event.getId());
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
