package com.taca.inventory.observability;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/health")
public class HealthController {

    private final HealthProbeService healthProbeService;

    public HealthController(HealthProbeService healthProbeService) {
        this.healthProbeService = healthProbeService;
    }

    @GetMapping("/live")
    public ResponseEntity<Map<String, Object>> live() {
        if (healthProbeService.isLive()) {
            return ResponseEntity.ok(Map.of("status", "UP"));
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("status", "DOWN"));
    }

    @GetMapping("/ready")
    public ResponseEntity<Map<String, Object>> ready() {
        HealthProbeService.ReadinessStatus status = healthProbeService.checkReadiness();
        Map<String, Object> body = Map.of(
                "status", status.ready() ? "UP" : "DOWN",
                "components", status.components()
        );
        if (status.ready()) {
            return ResponseEntity.ok(body);
        } else {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
        }
    }
}
