package com.celeris.message;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CelerisMessageApplication {

    public static void main(String[] args) {
        SpringApplication.run(CelerisMessageApplication.class, args);
    }
}
