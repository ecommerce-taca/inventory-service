package com.taca.inventory.observability.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TraceContextFilter Unit Tests")
class TraceContextFilterTest {

    private TraceContextFilter filter;

    @BeforeEach
    void setUp() {
        filter = new TraceContextFilter();
        TraceContext.clear();
        MDC.clear();
    }

    @AfterEach
    void tearDown() {
        TraceContext.clear();
        MDC.clear();
    }

    @Test
    @DisplayName("Trích xuất W3C traceparent, tracestate và X-Request-ID hợp lệ vào TraceContext và MDC, gắn X-Request-ID vào response")
    void doFilter_withValidW3CTraceparent_populatesTraceContextAndMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
        request.addHeader("tracestate", "congo=ucfJifl5GOE,rojo=00f067a");
        request.addHeader("X-Request-ID", "req-custom-001");

        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<TraceContext> capturedContext = new AtomicReference<>();
        AtomicReference<String> capturedTraceMdc = new AtomicReference<>();
        AtomicReference<String> capturedRequestMdc = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> {
            capturedContext.set(TraceContext.getCurrent());
            capturedTraceMdc.set(MDC.get("trace_id"));
            capturedRequestMdc.set(MDC.get("request_id"));
        });

        TraceContext ctx = capturedContext.get();
        assertThat(ctx).isNotNull();
        assertThat(ctx.getTraceId()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(ctx.getParentSpanId()).isEqualTo("00f067aa0ba902b7");
        assertThat(ctx.getSpanId()).isNotBlank();
        assertThat(ctx.getRequestId()).isEqualTo("req-custom-001");
        assertThat(ctx.getTracestate()).isEqualTo("congo=ucfJifl5GOE,rojo=00f067a");

        assertThat(capturedTraceMdc.get()).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
        assertThat(capturedRequestMdc.get()).isEqualTo("req-custom-001");

        assertThat(response.getHeader("X-Request-ID")).isEqualTo("req-custom-001");
    }

    @Test
    @DisplayName("Tự động sinh mới trace_id, span_id và X-Request-ID khi request không truyền trace headers")
    void doFilter_withoutTraceparent_generatesNewW3CTraceAndRequestId() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<TraceContext> capturedContext = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> {
            capturedContext.set(TraceContext.getCurrent());
        });

        TraceContext ctx = capturedContext.get();
        assertThat(ctx).isNotNull();
        assertThat(ctx.getTraceId()).hasSize(32);
        assertThat(ctx.getSpanId()).hasSize(16);
        assertThat(ctx.getRequestId()).isNotBlank();

        assertThat(response.getHeader("X-Request-ID")).isEqualTo(ctx.getRequestId());
    }

    @Test
    @DisplayName("Dọn dẹp sạch TraceContext và MDC sau khi kết thúc request")
    void doFilter_cleansUpTraceContextAndMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(TraceContext.getCurrent()).isNull();
        assertThat(MDC.get("trace_id")).isNull();
        assertThat(MDC.get("span_id")).isNull();
        assertThat(MDC.get("request_id")).isNull();
    }

    @Test
    @DisplayName("Dọn dẹp sạch TraceContext và MDC kể cả khi request gặp exception")
    void doFilter_cleansUpEvenOnException() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain faultyChain = (ServletRequest req, ServletResponse res) -> {
            throw new RuntimeException("TraceContextFilter test failure");
        };

        assertThatThrownBy(() -> filter.doFilter(request, response, faultyChain))
                .isInstanceOf(RuntimeException.class);

        assertThat(TraceContext.getCurrent()).isNull();
        assertThat(MDC.get("trace_id")).isNull();
    }
}
