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
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalTime;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/sse-server")
@RequiredArgsConstructor
@Slf4j
public class SseServerController {
    private final SseEventRepository sseEventRepository;

    private Map<String, SubscribableChannel> channelMap = new ConcurrentHashMap<>();
    private static final long RECONNECT_TIME = 15L;

    @GetMapping("/subscribe")
    public Flux<ServerSentEvent<String>> subscribe(
            @RequestHeader(value = "last-event-id", required = false) String headerLastEventId,
            @RequestParam(value = "name") String name,
            @RequestParam(value = "lastEventId", required = false) String queryLastEventId) {

        log.info("enter subscribe(), name={}, headerLastEventId={}, queryLastEventId={}", name, headerLastEventId, queryLastEventId);

        String lastEventIdStr = (headerLastEventId != null) ? headerLastEventId : queryLastEventId;
        long lastEventId = StringUtils.hasText(lastEventIdStr) ? Long.parseLong(lastEventIdStr) : 0;

        //event with comment only to indicate connection established
        Flux<ServerSentEvent<String>> commentEvent = Flux.just(this.buildComment(
                String.format("Connected @ %s, lastEventId=%s", LocalTime.now().toString(), lastEventIdStr)
        ));

        //find the events to resend
        List<SseEvent> resentSseEvents = sseEventRepository.findByNameAndIdAfterOrderById(name, lastEventId);
        List<Long> resentSseEventIds = resentSseEvents.stream().map(SseEvent::getId).collect(Collectors.toList());
        Flux<ServerSentEvent<String>> resentEvents = Flux.fromIterable(resentSseEvents).map(this::buildEvent);

        SubscribableChannel subscribableChannel = this.getSubscribableChannel(name);

        return Flux.concat(commentEvent, resentEvents, Flux.create(sink -> {
            MessageHandler handler = message -> {
                SseEvent sseEvent = (SseEvent)message.getPayload();
                sink.next(buildEvent(sseEvent));
            };
            sink.onCancel(() -> subscribableChannel.unsubscribe(handler));
            subscribableChannel.subscribe(handler);
            log.info("Subscribed to [{}]. Resent events: id={}", name, resentSseEventIds);
        }));
    }

    @PostMapping(value = "/publish")
    public Mono<Map<String, Object>> publish(@RequestBody EventData eventData) {
        SseEvent sseEvent = new SseEvent(eventData.getName(), eventData.getRefId(), eventData.getData());
        this.saveAndSendEvent(sseEvent);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", sseEvent.getId());
        response.put("name", sseEvent.getName());
        if (StringUtils.hasText(sseEvent.getRefId())) {
            response.put("refId", sseEvent.getRefId());
        }
        response.put("createdDttm", sseEvent.getCreatedDttm());

        return Mono.just(response);
    }

    private ServerSentEvent<String> buildEvent(SseEvent sseEvent) {
        return ServerSentEvent.<String>builder()
                .id(String.valueOf(sseEvent.getId()))
                .event(sseEvent.getName())
                .retry(Duration.ofSeconds(15))
                .data(Base64.getEncoder().encodeToString(sseEvent.getData().getBytes()))
                .build();
    }

    private ServerSentEvent<String> buildComment(String comment) {
        return ServerSentEvent.<String> builder()
                .comment(comment)
                .retry(Duration.ofSeconds(RECONNECT_TIME))
                .build();
    }

    private void saveAndSendEvent(SseEvent sseEvent) {
        sseEventRepository.save(sseEvent);
        SubscribableChannel subscribableChannel = this.getSubscribableChannel(sseEvent.getName());
        subscribableChannel.send(new GenericMessage<>(sseEvent));
        log.info("Sent event to [{}]: id={}, refId={}", sseEvent.getName(), sseEvent.getId(), sseEvent.getRefId());
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
