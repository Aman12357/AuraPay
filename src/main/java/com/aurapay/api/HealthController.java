package com.aurapay.api;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        return Map.of(
                "service", "AuraPay",
                "status", "ok",
                "mode", "development",
                "realPaymentsEnabled", false
        );
    }
}
