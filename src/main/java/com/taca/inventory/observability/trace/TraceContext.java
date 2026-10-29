package com.taca.inventory.observability.trace;

import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

/**
 * Context holding W3C Distributed Tracing identifiers and HTTP/event request metadata.
 * Complies with W3C Trace Context recommendation (traceparent, tracestate).
 */
public class TraceContext {

    private static final ThreadLocal<TraceContext> CURRENT = new ThreadLocal<>();

    private static final HexFormat HEX = HexFormat.of();
    private static final Pattern HEX_32 = Pattern.compile("^[0-9a-fA-F]{32}$");
    private static final Pattern HEX_16 = Pattern.compile("^[0-9a-fA-F]{16}$");
    private static final Pattern HEX_2 = Pattern.compile("^[0-9a-fA-F]{2}$");

    private static final String ALL_ZEROS_32 = "00000000000000000000000000000000";
    private static final String ALL_ZEROS_16 = "0000000000000000";

    private String traceId;
    private String spanId;
    private String parentSpanId;
    private String requestId;
    private String tracestate;
    private String traceFlags = "01";

    public TraceContext() {}

    public static void clear() {
        CURRENT.remove();
    }

    public static void setCurrent(TraceContext context) {
        if (context == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(context);
        }
    }

    public static TraceContext getCurrent() {
        return CURRENT.get();
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getTraceId() {
        return traceId;
    }

    public String getSpanId() {
        return spanId;
    }

    public String getParentSpanId() {
        return parentSpanId;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getTracestate() {
        return tracestate;
    }

    public String getTraceFlags() {
        return (traceFlags != null && !traceFlags.isBlank()) ? traceFlags : "01";
    }

    public String toTraceparent() {
        String tId = (traceId != null && !traceId.isBlank()) ? traceId : generateTraceId();
        String sId = (spanId != null && !spanId.isBlank()) ? spanId : generateSpanId();
        String flags = getTraceFlags();
        return "00-" + tId + "-" + sId + "-" + flags;
    }

    public static String generateTraceId() {
        byte[] bytes = new byte[16];
        do {
            ThreadLocalRandom.current().nextBytes(bytes);
        } while (isAllZeros(bytes));
        return HEX.formatHex(bytes);
    }

    public static String generateSpanId() {
        byte[] bytes = new byte[8];
        do {
            ThreadLocalRandom.current().nextBytes(bytes);
        } while (isAllZeros(bytes));
        return HEX.formatHex(bytes);
    }

    public static String generateRequestId() {
        return UUID.randomUUID().toString();
    }

    private static boolean isAllZeros(byte[] bytes) {
        for (byte b : bytes) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }

    public static boolean isValidTraceparent(String traceparent) {
        if (traceparent == null || traceparent.isBlank()) {
            return false;
        }
        String trimmed = traceparent.trim();
        String[] parts = trimmed.split("-");
        if (parts.length < 4) {
            return false;
        }
        String version = parts[0];
        if (version.length() != 2 || !HEX_2.matcher(version).matches() || version.equalsIgnoreCase("ff")) {
            return false;
        }
        if (version.equals("00") && (parts.length != 4 || trimmed.length() != 55)) {
            return false;
        }
        String traceId = parts[1];
        if (traceId.length() != 32 || !HEX_32.matcher(traceId).matches() || traceId.equals(ALL_ZEROS_32)) {
            return false;
        }
        String parentId = parts[2];
        if (parentId.length() != 16 || !HEX_16.matcher(parentId).matches() || parentId.equals(ALL_ZEROS_16)) {
            return false;
        }
        String flags = parts[3];
        if (flags.length() != 2 || !HEX_2.matcher(flags).matches()) {
            return false;
        }
        return true;
    }

    public static TraceContext fromHeaders(String traceparent, String tracestate, String requestId) {
        String resolvedRequestId = (requestId != null && !requestId.isBlank()) ? requestId.trim() : generateRequestId();

        if (isValidTraceparent(traceparent)) {
            String[] parts = traceparent.trim().split("-");
            String traceId = parts[1].toLowerCase(Locale.ROOT);
            String parentSpanId = parts[2].toLowerCase(Locale.ROOT);
            String traceFlags = parts[3].toLowerCase(Locale.ROOT);
            String spanId = generateSpanId();
            String resolvedTracestate = (tracestate != null && !tracestate.isBlank()) ? tracestate.trim() : null;

            return builder()
                    .traceId(traceId)
                    .spanId(spanId)
                    .parentSpanId(parentSpanId)
                    .requestId(resolvedRequestId)
                    .tracestate(resolvedTracestate)
                    .traceFlags(traceFlags)
                    .build();
        } else {
            return fallback(resolvedRequestId);
        }
    }

    public static TraceContext fallback(String requestId) {
        String resolvedRequestId = (requestId != null && !requestId.isBlank()) ? requestId.trim() : generateRequestId();
        return builder()
                .traceId(generateTraceId())
                .spanId(generateSpanId())
                .requestId(resolvedRequestId)
                .traceFlags("01")
                .build();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TraceContext that = (TraceContext) o;
        return Objects.equals(traceId, that.traceId) &&
                Objects.equals(spanId, that.spanId) &&
                Objects.equals(parentSpanId, that.parentSpanId) &&
                Objects.equals(requestId, that.requestId) &&
                Objects.equals(tracestate, that.tracestate) &&
                Objects.equals(traceFlags, that.traceFlags);
    }

    @Override
    public int hashCode() {
        return Objects.hash(traceId, spanId, parentSpanId, requestId, tracestate, traceFlags);
    }

    @Override
    public String toString() {
        return "TraceContext{" +
                "traceId='" + traceId + '\'' +
                ", spanId='" + spanId + '\'' +
                ", parentSpanId='" + parentSpanId + '\'' +
                ", requestId='" + requestId + '\'' +
                ", tracestate='" + tracestate + '\'' +
                ", traceFlags='" + traceFlags + '\'' +
                '}';
    }

    public static class Builder {
        private final TraceContext ctx = new TraceContext();

        public Builder traceId(String traceId) {
            ctx.traceId = traceId;
            return this;
        }

        public Builder spanId(String spanId) {
            ctx.spanId = spanId;
            return this;
        }

        public Builder parentSpanId(String parentSpanId) {
            ctx.parentSpanId = parentSpanId;
            return this;
        }

        public Builder requestId(String requestId) {
            ctx.requestId = requestId;
            return this;
        }

        public Builder tracestate(String tracestate) {
            ctx.tracestate = tracestate;
            return this;
        }

        public Builder traceFlags(String traceFlags) {
            ctx.traceFlags = traceFlags;
            return this;
        }

        public TraceContext build() {
            if (ctx.traceId == null || ctx.traceId.isBlank()) {
                ctx.traceId = generateTraceId();
            }
            if (ctx.spanId == null || ctx.spanId.isBlank()) {
                ctx.spanId = generateSpanId();
            }
            if (ctx.requestId == null || ctx.requestId.isBlank()) {
                ctx.requestId = generateRequestId();
            }
            if (ctx.traceFlags == null || ctx.traceFlags.isBlank()) {
                ctx.traceFlags = "01";
            }
            return ctx;
        }
    }
}
