package com.example.demo.controller;

import com.example.demo.bean.EventData;
import com.example.demo.dao.SseEventRepository;
import com.example.demo.dao.entity.SseEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.integration.channel.PublishSubscribeChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.SubscribableChannel;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/sse-server")
@RequiredArgsConstructor
@Slf4j
public class SseServerController {
    private final SseEventRepository sseEventRepository;

    private static final String[] WORDS = "The quick brown fox jumps over the lazy dog.".split(" ");

    private Map<String, SubscribableChannel> channelMap = new ConcurrentHashMap<>();

    @GetMapping("/short-lasting")
    public Flux<ServerSentEvent<String>> getShortLastingEvents() {
        return Flux.zip(Flux.just(WORDS), Flux.interval(Duration.ofSeconds(1)))
                .map(t -> ServerSentEvent.<String> builder()
                        .id(String.valueOf(t.getT2()))
                        .event("short-lasting")
                        .data(t.getT1())
                        .build());
    }

    @GetMapping("/subscribe")
    public Flux<ServerSentEvent<String>> subscribe(@RequestHeader(value = "last-event-id", required = false) String sseLastEventId,
                                                   @RequestParam(value = "name") String name,
                                                   @RequestParam(value = "lastEventId", required = false) String lastEventId) {

        log.info("enter subscribe(), name={}, sseLastEventId={}, lastEventId={}", name, sseLastEventId, lastEventId);

        lastEventId = (sseLastEventId != null) ? sseLastEventId : lastEventId;
        int prevEventId = StringUtils.hasText(lastEventId) ? Integer.parseInt(lastEventId) : 0;
        List<SseEvent> sseEvents = sseEventRepository.findByNameAndIdAfterOrderById(name, prevEventId);
        Flux<ServerSentEvent<String>> resentEvents = Flux.fromIterable(sseEvents)
                .map(event -> ServerSentEvent.<String> builder()
                        .id(String.valueOf(event.getId()))
                        .event(event.getName())
                        .retry(Duration.ofSeconds(15))
                        .data(event.getData())
                        .build());

        SubscribableChannel subscribableChannel = this.getSubscribableChannel(name);

        return Flux.concat(resentEvents, Flux.create(sink -> {
            MessageHandler handler = message -> {
                SseEvent sseEvent = (SseEvent)message.getPayload();
                sink.next(ServerSentEvent.<String> builder()
                        .id(String.valueOf(sseEvent.getId()))
                        .event(sseEvent.getName())
                        .retry(Duration.ofSeconds(15))
                        .data(sseEvent.getData())
                        .build());
            };
            sink.onCancel(() -> subscribableChannel.unsubscribe(handler));
            subscribableChannel.subscribe(handler);
        }, FluxSink.OverflowStrategy.LATEST));
    }

    @PostMapping("/publish")
    public Mono<SseEvent> publish(@RequestBody EventData eventData) {
        SseEvent sseEvent = new SseEvent(eventData.getName(), eventData.getData());
        this.saveAndSendEvent(sseEvent);
        return Mono.just(sseEvent);
    }

    private void saveAndSendEvent(SseEvent sseEvent) {
        sseEventRepository.save(sseEvent);
        SubscribableChannel subscribableChannel = this.getSubscribableChannel(sseEvent.getName());
        subscribableChannel.send(new GenericMessage<>(sseEvent));
        log.info("sent {}", sseEvent);
    }

    private SubscribableChannel getSubscribableChannel(String name) {
        return channelMap.computeIfAbsent(name, k -> {
            PublishSubscribeChannel channel = new PublishSubscribeChannel();
            if (channel.getBeanName() == null) {
                channel.setBeanName(name);
            }
            return channel;
        });
    }
}
