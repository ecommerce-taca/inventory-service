package com.taca.inventory.observability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class HealthProbeIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4.0")
            .withDatabaseName("inventorydb")
            .withUsername("inventory")
            .withPassword("inventory_secret");

    @Container
    static KafkaContainer kafka = new KafkaContainer("apache/kafka-native:3.8.0");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("I-API-15: GET /health/live trả về 200 OK khi tiến trình ứng dụng đang chạy")
    void liveProbeShouldReturn200WhenApplicationRunning() throws Exception {
        mockMvc.perform(get("/health/live"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("I-API-15: GET /health/ready trả về 200 OK khi MySQL và Kafka sẵn sàng")
    void readyProbeShouldReturn200WhenDependenciesReady() throws Exception {
        mockMvc.perform(get("/health/ready"))
                .andExpect(status().isOk());
    }

    @Nested
    @DisplayName("Khi cơ sở dữ liệu hoặc Kafka không sẵn sàng")
    @SpringBootTest(properties = {
            "spring.datasource.url=jdbc:mysql://localhost:9999/invalid_db?connectTimeout=1000",
            "spring.kafka.bootstrap-servers=localhost:9998"
    })
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    class WhenDependenciesDownTest {

        @Autowired
        private MockMvc mockMvc;

        @Test
        @DisplayName("I-API-15: GET /health/live vẫn trả về 200 OK độc lập với trạng thái MySQL/Kafka")
        void liveProbeShouldStillReturn200Independently() throws Exception {
            mockMvc.perform(get("/health/live"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("I-API-15: GET /health/ready trả về 503 Service Unavailable khi dependencies không sẵn sàng")
        void readyProbeShouldReturn503WhenDependenciesDown() throws Exception {
            mockMvc.perform(get("/health/ready"))
                    .andExpect(status().isServiceUnavailable());
        }
    }
}
