package com.taca.inventory.observability.trace;

import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("KafkaTracePropagator Unit Tests")
class KafkaTracePropagatorTest {

    private KafkaTracePropagator propagator;

    @BeforeEach
    void setUp() {
        propagator = new KafkaTracePropagator();
    }

    @Test
    @DisplayName("Gắn (inject) W3C traceparent, tracestate và X-Request-ID vào Kafka RecordHeaders")
    void inject_addsW3CHeadersToKafkaHeaders() {
        TraceContext context = TraceContext.builder()
                .traceId("4bf92f3577b34da6a3ce929d0e0e4736")
                .spanId("00f067aa0ba902b7")
                .requestId("req-kafka-001")
                .tracestate("congo=ucfJifl5GOE")
                .build();

        RecordHeaders headers = new RecordHeaders();
        propagator.inject(headers, context);

        Header traceparentHeader = headers.lastHeader("traceparent");
        assertThat(traceparentHeader).isNotNull();
        assertThat(new String(traceparentHeader.value(), StandardCharsets.UTF_8))
                .isEqualTo("00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");

        Header requestIdHeader = headers.lastHeader("X-Request-ID");
        assertThat(requestIdHeader).isNotNull();
        assertThat(new String(requestIdHeader.value(), StandardCharsets.UTF_8))
                .isEqualTo("req-kafka-001");

        Header tracestateHeader = headers.lastHeader("tracestate");
        assertThat(tracestateHeader).isNotNull();
        assertThat(new String(tracestateHeader.value(), StandardCharsets.UTF_8))
                .isEqualTo("congo=ucfJifl5GOE");
    }

    @Test
    @DisplayName("Trích xuất (extract) W3C traceparent, tracestate và X-Request-ID từ Kafka RecordHeaders vào TraceContext")
    void extract_readsW3CHeadersFromKafkaHeaders() {
        RecordHeaders headers = new RecordHeaders();
        headers.add("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01".getBytes(StandardCharsets.UTF_8));
        headers.add("tracestate", "congo=ucfJifl5GOE".getBytes(StandardCharsets.UTF_8));
        headers.add("X-Request-ID", "req-kafka-002".getBytes(StandardCharsets.UTF_8));

        TraceContext extracted = propagator.extract(headers);

        assertThat(extracted).isNotNull();
        assertThat(extracted.getTraceId()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(extracted.getParentSpanId()).isEqualTo("00f067aa0ba902b7");
        assertThat(extracted.getRequestId()).isEqualTo("req-kafka-002");
        assertThat(extracted.getTracestate()).isEqualTo("congo=ucfJifl5GOE");
    }

    @Test
    @DisplayName("Xử lý an toàn khi Kafka headers rỗng và tự sinh fallback context hợp lệ")
    void extract_withMissingHeaders_generatesFallbackContext() {
        RecordHeaders headers = new RecordHeaders();

        TraceContext extracted = propagator.extract(headers);

        assertThat(extracted).isNotNull();
        assertThat(extracted.getTraceId()).hasSize(32);
        assertThat(extracted.getRequestId()).isNotBlank();
    }
}
