package com.taca.inventory.observability.trace;

import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Propagator for injecting and extracting W3C trace context and X-Request-ID
 * into/from Kafka record headers.
 */
@Component
public class KafkaTracePropagator {

    public static final String HEADER_TRACEPARENT = "traceparent";
    public static final String HEADER_TRACESTATE = "tracestate";
    public static final String HEADER_REQUEST_ID = "X-Request-ID";

    /**
     * Injects W3C traceparent, tracestate and X-Request-ID into Kafka record headers.
     *
     * @param headers Kafka RecordHeaders
     * @param context TraceContext to inject (if null, falls back to TraceContext.getCurrent())
     */
    public void inject(Headers headers, TraceContext context) {
        if (headers == null) {
            return;
        }
        TraceContext ctx = context != null ? context : TraceContext.getCurrent();
        if (ctx == null) {
            return;
        }

        setHeader(headers, HEADER_TRACEPARENT, ctx.toTraceparent());

        if (ctx.getRequestId() != null && !ctx.getRequestId().isBlank()) {
            setHeader(headers, HEADER_REQUEST_ID, ctx.getRequestId());
        }

        if (ctx.getTracestate() != null && !ctx.getTracestate().isBlank()) {
            setHeader(headers, HEADER_TRACESTATE, ctx.getTracestate());
        }
    }

    /**
     * Extracts W3C traceparent, tracestate and X-Request-ID from Kafka record headers into TraceContext.
     * Generates a safe fallback context if headers are absent or invalid.
     *
     * @param headers Kafka RecordHeaders
     * @return extracted or fallback TraceContext
     */
    public TraceContext extract(Headers headers) {
        if (headers == null) {
            return TraceContext.fallback(null);
        }

        String traceparent = getHeaderValue(headers, HEADER_TRACEPARENT);
        String tracestate = getHeaderValue(headers, HEADER_TRACESTATE);
        String requestId = getHeaderValue(headers, HEADER_REQUEST_ID);
        if (requestId == null || requestId.isBlank()) {
            requestId = getHeaderValue(headers, "x-request-id");
        }

        return TraceContext.fromHeaders(traceparent, tracestate, requestId);
    }

    private void setHeader(Headers headers, String key, String value) {
        if (headers != null && key != null && value != null) {
            headers.remove(key);
            headers.add(key, value.getBytes(StandardCharsets.UTF_8));
        }
    }

    private String getHeaderValue(Headers headers, String key) {
        Header header = headers.lastHeader(key);
        if (header != null && header.value() != null) {
            return new String(header.value(), StandardCharsets.UTF_8);
        }
        for (Header h : headers) {
            if (h.key().equalsIgnoreCase(key) && h.value() != null) {
                return new String(h.value(), StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
