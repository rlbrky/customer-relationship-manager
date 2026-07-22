package com.berkay.crm.dto;

import java.time.Instant;

public record HealthResponse(String status, String db, Instant timestamp) {

    public static HealthResponse up() {
        return new HealthResponse("UP", "UP", Instant.now());
    }

    public static HealthResponse down() {
        return new HealthResponse("DOWN", "DOWN", Instant.now());
    }
}
