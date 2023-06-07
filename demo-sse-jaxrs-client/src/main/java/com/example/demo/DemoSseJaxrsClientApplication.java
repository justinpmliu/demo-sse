package com.example.demo;

import com.example.demo.consumer.CustomEventConsumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;


@SpringBootApplication
@EnableAsync
@Slf4j
public class DemoSseJaxrsClientApplication implements CommandLineRunner {
    @Autowired
    private CustomEventConsumer customEventConsumer;

    public static void main(String[] args) {
        SpringApplication.run(DemoSseJaxrsClientApplication.class, args);
    }

    @Override
    public void run(String... args) {
        customEventConsumer.consume();
    }

}
