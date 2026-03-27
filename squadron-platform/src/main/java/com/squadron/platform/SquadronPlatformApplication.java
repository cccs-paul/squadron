package com.squadron.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.squadron.platform", "com.squadron.common"})
public class SquadronPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(SquadronPlatformApplication.class, args);
    }
}
