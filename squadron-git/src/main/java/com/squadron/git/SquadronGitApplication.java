package com.squadron.git;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication(scanBasePackages = {"com.squadron.git", "com.squadron.common"})
@EnableFeignClients
public class SquadronGitApplication {

    public static void main(String[] args) {
        SpringApplication.run(SquadronGitApplication.class, args);
    }
}
