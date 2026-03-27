package com.squadron.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"com.squadron.config", "com.squadron.common"})
@EnableJpaRepositories
public class SquadronConfigApplication {

    public static void main(String[] args) {
        SpringApplication.run(SquadronConfigApplication.class, args);
    }
}
