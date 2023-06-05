package com.example.demo;

import com.example.demo.dao.SseLastEventIdRepository;
import com.example.demo.entity.SseLastEventId;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.util.StringUtils;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.sse.InboundSseEvent;
import javax.ws.rs.sse.SseEventSource;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;


@SpringBootApplication
@Slf4j
public class DemoSseJaxrsClientApplication implements CommandLineRunner {
    private static final String EVENT_NAME = "custom-event";
    private static final String URL = "http://localhost:8080/sse/subscribe?name=" + EVENT_NAME + "&lastEventId=";

    @Autowired
    private SseLastEventIdRepository sseLastEventIdRepository;

    public static void main(String[] args) {
        SpringApplication.run(DemoSseJaxrsClientApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        String lastEventId = this.getLastEventId(EVENT_NAME);
        log.info("Connect SSE server, name={}, lastEventId={}", EVENT_NAME, lastEventId);

        Client client = ClientBuilder.newClient();
        WebTarget target = client.target(lastEventId == null ? URL : URL + lastEventId);

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
        log.info("id: [{}] , name: [{}] , data: [{}], comment: [{}]",
                inboundSseEvent.getId(), inboundSseEvent.getName(), inboundSseEvent.readData(), inboundSseEvent.getComment());
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
        if (StringUtils.hasText(name)) {
            SseLastEventId sseLastEventId = sseLastEventIdRepository.findByName(name);
            if (sseLastEventId != null) {
                return sseLastEventId.getLastEventId();
            }
        }
        return null;
    }

    //Error
    private Consumer<Throwable> onError = throwable -> log.error(throwable.getMessage(), throwable);

    //Connection close and there is nothing to receive
    private Runnable onComplete = () -> log.info("Connection closed");

}
