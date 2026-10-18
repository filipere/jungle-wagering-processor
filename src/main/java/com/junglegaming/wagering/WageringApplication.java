package com.junglegaming.wagering;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class WageringApplication {
    public static void main(String[] args) {
        SpringApplication.run(WageringApplication.class, args);
    }
}