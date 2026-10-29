package com.taca.inventory.observability.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@DisplayName("JsonStructuredLogFormatter Unit Tests")
class JsonStructuredLogFormatterTest {

    private JsonStructuredLogFormatter formatter;
    private SensitiveDataMasker masker;

    @BeforeEach
    void setUp() {
        masker = new SensitiveDataMasker();
        formatter = new JsonStructuredLogFormatter(masker);
    }

    @Test
    @DisplayName("Định dạng log JSON ra stdout với đầy đủ 13 trường chuẩn theo PC-09 và LLD §2.2")
    void format_containsAllThirteenRequiredFields() {
        ILoggingEvent event = Mockito.mock(ILoggingEvent.class);
        when(event.getLevel()).thenReturn(Level.INFO);
        when(event.getFormattedMessage()).thenReturn("Inventory reservation committed successfully");
        when(event.getTimeStamp()).thenReturn(1700000000000L);

        Map<String, String> mdc = new HashMap<>();
        mdc.put("event", "inventory.reservation.committed");
        mdc.put("trace_id", "4bf92f3577b34da6a3ce929d0e0e4736");
        mdc.put("span_id", "00f067aa0ba902b7");
        mdc.put("request_id", "req-01912fa0");
        mdc.put("route", "/internal/inventory/reservations/res-1/commit");
        mdc.put("method", "POST");
        mdc.put("status_code", "200");
        mdc.put("duration_ms", "28");
        when(event.getMDCPropertyMap()).thenReturn(mdc);

        String jsonOutput = formatter.format(event);

        assertThat(jsonOutput).isNotNull();
        assertThat(jsonOutput).contains("\"timestamp\":");
        assertThat(jsonOutput).contains("\"level\":\"INFO\"");
        assertThat(jsonOutput).contains("\"service\":\"inventory-service\"");
        assertThat(jsonOutput).contains("\"env\":");
        assertThat(jsonOutput).contains("\"version\":");
        assertThat(jsonOutput).contains("\"event\":\"inventory.reservation.committed\"");
        assertThat(jsonOutput).contains("\"trace_id\":\"4bf92f3577b34da6a3ce929d0e0e4736\"");
        assertThat(jsonOutput).contains("\"span_id\":\"00f067aa0ba902b7\"");
        assertThat(jsonOutput).contains("\"request_id\":\"req-01912fa0\"");
        assertThat(jsonOutput).contains("\"route\":\"/internal/inventory/reservations/res-1/commit\"");
        assertThat(jsonOutput).contains("\"method\":\"POST\"");
        assertThat(jsonOutput).contains("\"status_code\":200");
        assertThat(jsonOutput).contains("\"duration_ms\":28");
    }

    @Test
    @DisplayName("Xử lý an toàn khi thiếu các trường MDC tùy chọn mà không bị sập (null-safe)")
    void format_handlesMissingMdcGracefully() {
        ILoggingEvent event = Mockito.mock(ILoggingEvent.class);
        when(event.getLevel()).thenReturn(Level.WARN);
        when(event.getFormattedMessage()).thenReturn("Simple warning message");
        when(event.getTimeStamp()).thenReturn(1700000000000L);
        when(event.getMDCPropertyMap()).thenReturn(Map.of());

        String jsonOutput = formatter.format(event);

        assertThat(jsonOutput).isNotNull();
        assertThat(jsonOutput).contains("\"level\":\"WARN\"");
        assertThat(jsonOutput).contains("\"message\":\"Simple warning message\"");
    }

    @Test
    @DisplayName("Tự động che giấu thông tin nhạy cảm xuất hiện trong message của log event")
    void format_masksSensitiveDataInMessage() {
        ILoggingEvent event = Mockito.mock(ILoggingEvent.class);
        when(event.getLevel()).thenReturn(Level.INFO);
        when(event.getFormattedMessage()).thenReturn("Received token Authorization: Bearer secret-raw-token-12345");
        when(event.getTimeStamp()).thenReturn(1700000000000L);
        when(event.getMDCPropertyMap()).thenReturn(Map.of());

        String jsonOutput = formatter.format(event);

        assertThat(jsonOutput).doesNotContain("secret-raw-token-12345");
        assertThat(jsonOutput).contains("Bearer [REDACTED]");
    }
}
