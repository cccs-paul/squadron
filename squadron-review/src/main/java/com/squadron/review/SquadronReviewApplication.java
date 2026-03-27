package com.squadron.review;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.squadron.review", "com.squadron.common"})
public class SquadronReviewApplication {

    public static void main(String[] args) {
        SpringApplication.run(SquadronReviewApplication.class, args);
    }
}
