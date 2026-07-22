package com.berkay.crm.api;

import com.berkay.crm.dto.HealthResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    private final JdbcTemplate jdbcTemplate;

    public HealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/api/health")
    public ResponseEntity<HealthResponse> health() {

        if(databaseIsUp()) {
            return ResponseEntity.ok(HealthResponse.up());
        }

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(HealthResponse.down());
    }

    private boolean databaseIsUp() {
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return Integer.valueOf(1).equals(result);
        } catch (DataAccessException e) {
            return false;
        }
    }
}
