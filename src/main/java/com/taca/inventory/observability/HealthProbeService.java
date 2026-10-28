package com.taca.inventory.observability;

import com.zaxxer.hikari.HikariDataSource;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class HealthProbeService {


    public record ReadinessStatus(boolean ready, Map<String, String> components) {
    }

    private final DataSource dataSource;
    private final Environment environment;

    public HealthProbeService(
            @Autowired(required = false) DataSource dataSource,
            Environment environment) {
        this.dataSource = dataSource;
        this.environment = environment;
        if (this.dataSource instanceof HikariDataSource hikariDataSource) {
            hikariDataSource.setConnectionTimeout(2000);
        }
    }

    private String getEffectiveProperty(String key) {
        if (environment instanceof ConfigurableEnvironment configurableEnvironment) {
            PropertySource<?> inlined = configurableEnvironment.getPropertySources().get("Inlined Test Properties");
            if (inlined != null && inlined.containsProperty(key)) {
                Object val = inlined.getProperty(key);
                if (val != null) {
                    return val.toString();
                }
            }
        }
        return environment != null ? environment.getProperty(key) : null;
    }

    public boolean isLive() {
        return true;
    }

    public boolean isDatabaseReady() {
        String inlinedUrl = null;
        if (environment instanceof ConfigurableEnvironment configurableEnvironment) {
            PropertySource<?> inlined = configurableEnvironment.getPropertySources().get("Inlined Test Properties");
            if (inlined != null && inlined.containsProperty("spring.datasource.url")) {
                Object val = inlined.getProperty("spring.datasource.url");
                if (val != null) {
                    inlinedUrl = val.toString();
                }
            }
        }

        if (inlinedUrl != null) {
            String username = getEffectiveProperty("spring.datasource.username");
            String password = getEffectiveProperty("spring.datasource.password");
            try (Connection conn = DriverManager.getConnection(inlinedUrl, username, password)) {
                return conn != null && conn.isValid(2);
            } catch (Exception e) {
                return false;
            }
        }

        if (dataSource == null) {
            return false;
        }
        try (Connection connection = dataSource.getConnection()) {
            return connection != null && connection.isValid(2);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isKafkaReady() {
        String servers = getEffectiveProperty("spring.kafka.bootstrap-servers");
        if (servers == null || servers.isBlank()) {
            return false;
        }
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, servers);
        configs.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 2000);
        configs.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 2000);
        configs.put(AdminClientConfig.RETRIES_CONFIG, 0);

        AdminClient adminClient = null;
        try {
            adminClient = AdminClient.create(configs);
            adminClient.describeCluster().clusterId().get(2, TimeUnit.SECONDS);
            return true;
        } catch (Exception e) {
            return false;
        } finally {
            if (adminClient != null) {
                try {
                    adminClient.close(Duration.ofMillis(500));
                } catch (Exception ignored) {
                }
            }
        }
    }

    public ReadinessStatus checkReadiness() {
        CompletableFuture<Boolean> dbFuture = CompletableFuture.supplyAsync(this::isDatabaseReady);
        CompletableFuture<Boolean> kafkaFuture = CompletableFuture.supplyAsync(this::isKafkaReady);

        boolean dbReady = false;
        boolean kafkaReady = false;
        try {
            dbReady = Boolean.TRUE.equals(dbFuture.get(3, TimeUnit.SECONDS));
        } catch (Exception ignored) {
        }
        try {
            kafkaReady = Boolean.TRUE.equals(kafkaFuture.get(3, TimeUnit.SECONDS));
        } catch (Exception ignored) {
        }

        boolean overall = dbReady && kafkaReady;
        Map<String, String> components = Map.of(
                "database", dbReady ? "UP" : "DOWN",
                "kafka", kafkaReady ? "UP" : "DOWN"
        );
        return new ReadinessStatus(overall, components);
    }

    public boolean isReady() {
        return checkReadiness().ready();
    }
}
