package com.example.demo.controller;

import com.example.demo.consumer.SseEventConsumer;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/sse-client")
@RequiredArgsConstructor
public class SseClientController {
    private final SseEventConsumer sseEventConsumer;

    @GetMapping("/consumer")
    public String launchConsumer(@RequestParam("name") String name) {
        sseEventConsumer.consume(name);
        return String.format("Launched consumer for [%s]. Please check log...", name);
    }
}
