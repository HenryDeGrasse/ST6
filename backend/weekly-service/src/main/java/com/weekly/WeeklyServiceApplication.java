package com.weekly;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the Weekly Commitments modular-monolith backend.
 */
@SpringBootApplication
@EnableScheduling
public class WeeklyServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(WeeklyServiceApplication.class, args);
    }
}
