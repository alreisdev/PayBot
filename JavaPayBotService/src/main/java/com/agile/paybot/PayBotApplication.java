package com.agile.paybot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PayBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(PayBotApplication.class, args);
    }
}
