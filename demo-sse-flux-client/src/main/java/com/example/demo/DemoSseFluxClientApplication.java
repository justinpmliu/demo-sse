package com.example.demo;

import com.example.demo.consumer.CustomEventConsumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class DemoSseFluxClientApplication implements CommandLineRunner {
    @Autowired
    private CustomEventConsumer customEventConsumer;

    public static void main(String[] args) {
        SpringApplication.run(DemoSseFluxClientApplication.class, args);
    }

    @Override
    public void run(String... args) {
        customEventConsumer.consume();
    }
}
