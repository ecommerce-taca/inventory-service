package com.taca.inventory.observability.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("InventoryMetrics & BoundedMetricTags Unit Tests")
class InventoryMetricsTest {

    private InventoryMetrics metrics;

    @BeforeEach
    void setUp() {
        metrics = new InventoryMetrics();
    }

    @Test
    @DisplayName("BoundedMetricTags chỉ cho phép đúng 4 nhãn hữu hạn: operation, result, reason, service_dependency theo PC-10")
    void boundedMetricTags_allowsOnlyConfiguredBoundedKeys() {
        assertThat(BoundedMetricTags.getAllowedTagNames())
                .containsExactlyInAnyOrder("operation", "result", "reason", "service_dependency");

        assertThat(BoundedMetricTags.isAllowed("operation")).isTrue();
        assertThat(BoundedMetricTags.isAllowed("result")).isTrue();
        assertThat(BoundedMetricTags.isAllowed("reason")).isTrue();
        assertThat(BoundedMetricTags.isAllowed("service_dependency")).isTrue();
    }

    @ParameterizedTest(name = "Từ chối nhãn high-cardinality bị cấm: {0}")
    @ValueSource(strings = {"sku_id", "order_id", "user_id", "reservation_id", "idempotency_key", "email"})
    void boundedMetricTags_rejectsHighCardinalityKeys(String disallowedKey) {
        assertThat(BoundedMetricTags.isAllowed(disallowedKey)).isFalse();

        assertThatThrownBy(() -> BoundedMetricTags.validateKey(disallowedKey))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(disallowedKey);
    }

    @Test
    @DisplayName("Ghi nhận metric thao tác tồn kho hợp lệ với tập nhãn bounded cho phép")
    void recordOperation_succeedsWithBoundedTags() {
        assertThatCode(() -> metrics.recordOperation("reserve", "SUCCESS", "NONE", Duration.ofMillis(35)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Ghi nhận metric gọi dependency ngoài (MySQL, Kafka) với nhãn service_dependency")
    void recordDependencyCall_recordsServiceDependency() {
        assertThatCode(() -> metrics.recordDependencyCall("mysql", "SUCCESS", Duration.ofMillis(12)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Ném IllegalArgumentException khi cố tình đính kèm nhãn high-cardinality vào metric")
    void recordWithTags_rejectsHighCardinalityTag() {
        Map<String, String> invalidTags = Map.of(
                "operation", "reserve",
                "sku_id", "sku-01912fa0"
        );

        assertThatThrownBy(() -> metrics.recordWithTags("inventory.operation.duration", invalidTags, 50))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sku_id");
    }

    @Test
    @DisplayName("Cho phép ghi nhận metric khi toàn bộ tags đều nằm trong tập bounded cho phép")
    void recordWithTags_acceptsValidBoundedTags() {
        Map<String, String> validTags = Map.of(
                "operation", "adjust",
                "result", "SUCCESS",
                "reason", "DAMAGED"
        );

        assertThatCode(() -> metrics.recordWithTags("inventory.operation.duration", validTags, 20))
                .doesNotThrowAnyException();
    }
}
