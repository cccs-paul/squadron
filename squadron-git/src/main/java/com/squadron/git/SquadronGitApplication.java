package com.squadron.git;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.squadron.git", "com.squadron.common"})
public class SquadronGitApplication {

    public static void main(String[] args) {
        SpringApplication.run(SquadronGitApplication.class, args);
    }
}
