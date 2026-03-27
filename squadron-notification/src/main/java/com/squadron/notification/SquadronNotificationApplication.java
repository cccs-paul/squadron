package com.squadron.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.squadron.notification", "com.squadron.common"})
public class SquadronNotificationApplication {

    public static void main(String[] args) {
        SpringApplication.run(SquadronNotificationApplication.class, args);
    }
}
