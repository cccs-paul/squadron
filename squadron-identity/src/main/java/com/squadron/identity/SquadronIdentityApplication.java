package com.squadron.identity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"com.squadron.identity", "com.squadron.common"})
@ComponentScan(
    basePackages = {"com.squadron.identity", "com.squadron.common"},
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE,
        classes = com.squadron.common.exception.GlobalExceptionHandler.class
    )
)
@EnableJpaRepositories
public class SquadronIdentityApplication {

    public static void main(String[] args) {
        SpringApplication.run(SquadronIdentityApplication.class, args);
    }
}
