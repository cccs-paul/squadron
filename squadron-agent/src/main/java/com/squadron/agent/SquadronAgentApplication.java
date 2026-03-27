package com.squadron.agent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.squadron.agent", "com.squadron.common"})
public class SquadronAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(SquadronAgentApplication.class, args);
    }
}
