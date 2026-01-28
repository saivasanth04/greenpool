package com.example.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/actuator/health")
    public String health() {
        return "{\"status\": \"UP\"}";
    }

    @GetMapping("/api/health")
    public String apiHealth() {
        return "{\"status\": \"OK\", \"message\": \"Service is running\"}";
    }
}