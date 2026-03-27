package com.squadron.orchestrator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.squadron.orchestrator", "com.squadron.common"})
public class SquadronOrchestratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(SquadronOrchestratorApplication.class, args);
    }
}
