package com.taca.inventory.observability.metrics;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Thu thập và ghi nhận metrics cho inventory-service tuân thủ quy ước PC-10.
 * Chỉ cho phép các nhãn hữu hạn (operation, result, reason, service_dependency).
 */
@Component
public class InventoryMetrics {

    private final MeterRegistry meterRegistry;

    public InventoryMetrics() {
        this(new SimpleMeterRegistry());
    }

    @Autowired
    public InventoryMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry != null ? meterRegistry : new SimpleMeterRegistry();
    }

    public MeterRegistry getMeterRegistry() {
        return meterRegistry;
    }

    public void recordOperation(String operation, String result, String reason, Duration duration) {
        BoundedMetricTags.validateKey(BoundedMetricTags.TAG_OPERATION);
        BoundedMetricTags.validateKey(BoundedMetricTags.TAG_RESULT);
        BoundedMetricTags.validateKey(BoundedMetricTags.TAG_REASON);

        Timer.builder("inventory.operation.duration")
                .description("Duration of inventory operations")
                .tag(BoundedMetricTags.TAG_OPERATION, operation != null ? operation : "unknown")
                .tag(BoundedMetricTags.TAG_RESULT, result != null ? result : "unknown")
                .tag(BoundedMetricTags.TAG_REASON, reason != null ? reason : "none")
                .register(meterRegistry)
                .record(duration != null ? duration : Duration.ZERO);
    }

    public void recordDependencyCall(String dependency, String result, Duration duration) {
        BoundedMetricTags.validateKey(BoundedMetricTags.TAG_SERVICE_DEPENDENCY);
        BoundedMetricTags.validateKey(BoundedMetricTags.TAG_RESULT);

        Timer.builder("inventory.dependency.duration")
                .description("Duration of outbound dependency calls")
                .tag(BoundedMetricTags.TAG_SERVICE_DEPENDENCY, dependency != null ? dependency : "unknown")
                .tag(BoundedMetricTags.TAG_RESULT, result != null ? result : "unknown")
                .register(meterRegistry)
                .record(duration != null ? duration : Duration.ZERO);
    }

    public void recordWithTags(String metricName, Map<String, String> tags, double value) {
        BoundedMetricTags.validateTags(tags);

        List<Tag> tagList = new ArrayList<>();
        if (tags != null) {
            tags.forEach((k, v) -> tagList.add(Tag.of(k, v != null ? v : "")));
        }

        if (metricName != null && metricName.contains("duration")) {
            Timer.builder(metricName)
                    .tags(tagList)
                    .register(meterRegistry)
                    .record(Duration.ofMillis((long) value));
        } else {
            DistributionSummary.builder(metricName != null ? metricName : "inventory.metric")
                    .tags(tagList)
                    .register(meterRegistry)
                    .record(value);
        }
    }
}
