package com.example.demo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.sse.InboundSseEvent;
import javax.ws.rs.sse.SseEventSource;
import java.util.function.Consumer;


@SpringBootApplication
@Slf4j
public class DemoSseJaxrsClientApplication implements CommandLineRunner {
    private static final String URL = "http://localhost:8080/sse/custom-event";
    private SseEventSource eventSource;

    public static void main(String[] args) {
        SpringApplication.run(DemoSseJaxrsClientApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        Client client = ClientBuilder.newClient();
        WebTarget target = client.target(URL);
        eventSource = SseEventSource.target(target).build();
        try {
            eventSource.register(onEvent, onError, onComplete);
            eventSource.open();

            //Consuming events
            while (eventSource.isOpen()) {
                Thread.sleep(1000);
            }
        } catch (InterruptedException e) {
            log.warn(e.getMessage(), e);
            Thread.currentThread().interrupt();
        } finally {
            if (eventSource.isOpen()) {
                eventSource.close();
            }
        }
        client.close();
        log.info("End");

    }

    // A new event is received
    private Consumer<InboundSseEvent> onEvent = inboundSseEvent -> {
        log.info("id: [{}] , name: [{}] , data: [{}]", inboundSseEvent.getId(), inboundSseEvent.getName(), inboundSseEvent.readData());

        if ("complete".equals(inboundSseEvent.getName())) {
            eventSource.close();
        }
    };

    //Error
    private Consumer<Throwable> onError = throwable -> log.error(throwable.getMessage(), throwable);

    //Connection close and there is nothing to receive
    private Runnable onComplete = () -> log.info("Connection closed");

}
