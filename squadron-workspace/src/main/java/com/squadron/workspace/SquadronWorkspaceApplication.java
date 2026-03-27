package com.squadron.workspace;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.squadron.workspace", "com.squadron.common"})
@EnableJpaRepositories
@EnableScheduling
public class SquadronWorkspaceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SquadronWorkspaceApplication.class, args);
    }
}
