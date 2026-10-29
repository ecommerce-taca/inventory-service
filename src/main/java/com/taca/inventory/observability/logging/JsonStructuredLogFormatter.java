package com.taca.inventory.observability.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.LayoutBase;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Logback layout that formats logging events into structured JSON according to
 * PC-09 and LLD §2.2 specifications.
 * Output contains standard fields: timestamp, level, service, env, version,
 * event, trace_id, span_id, request_id, route, method, status_code, duration_ms,
 * and automatically masks sensitive data in log messages.
 */
public class JsonStructuredLogFormatter extends LayoutBase<ILoggingEvent> {

    private static final String DEFAULT_SERVICE = "inventory-service";
    private static final String DEFAULT_ENV = "dev";
    private static final String DEFAULT_VERSION = "0.0.1-SNAPSHOT";

    private final SensitiveDataMasker masker;
    private final ObjectMapper objectMapper;

    private String service = DEFAULT_SERVICE;
    private String env = System.getProperty("app.env", System.getenv().getOrDefault("APP_ENV", DEFAULT_ENV));
    private String version = System.getProperty("app.version", System.getenv().getOrDefault("APP_VERSION", DEFAULT_VERSION));

    public JsonStructuredLogFormatter(SensitiveDataMasker masker) {
        this.masker = masker != null ? masker : new SensitiveDataMasker();
        this.objectMapper = new ObjectMapper();
    }

    public JsonStructuredLogFormatter() {
        this(new SensitiveDataMasker());
    }

    public String getService() {
        return service;
    }

    public void setService(String service) {
        this.service = service;
    }

    public String getEnv() {
        return env;
    }

    public void setEnv(String env) {
        this.env = env;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    /**
     * Formats a Logback ILoggingEvent into a structured JSON string.
     *
     * @param event the logback logging event
     * @return structured JSON string
     */
    public String format(ILoggingEvent event) {
        if (event == null) {
            return "{}";
        }

        try {
            Map<String, Object> logMap = new LinkedHashMap<>();

            // 1. timestamp: UTC ISO-8601
            long timeStamp = event.getTimeStamp();
            String timestampStr = Instant.ofEpochMilli(timeStamp).toString();
            logMap.put("timestamp", timestampStr);

            // 2. level
            String levelStr = event.getLevel() != null ? event.getLevel().toString() : "INFO";
            logMap.put("level", levelStr);

            // 3. service
            logMap.put("service", service);

            // 4. env
            logMap.put("env", env);

            // 5. version
            logMap.put("version", version);

            // message: masked
            String rawMessage = event.getFormattedMessage();
            if (rawMessage != null) {
                logMap.put("message", masker.mask(rawMessage));
            }

            // MDC properties (PC-09 standard fields)
            Map<String, String> mdc = event.getMDCPropertyMap();
            if (mdc != null && !mdc.isEmpty()) {
                if (mdc.containsKey("event")) {
                    logMap.put("event", mdc.get("event"));
                }
                if (mdc.containsKey("trace_id")) {
                    logMap.put("trace_id", mdc.get("trace_id"));
                }
                if (mdc.containsKey("span_id")) {
                    logMap.put("span_id", mdc.get("span_id"));
                }
                if (mdc.containsKey("request_id")) {
                    logMap.put("request_id", mdc.get("request_id"));
                }
                if (mdc.containsKey("route")) {
                    logMap.put("route", mdc.get("route"));
                }
                if (mdc.containsKey("method")) {
                    logMap.put("method", mdc.get("method"));
                }
                if (mdc.containsKey("status_code")) {
                    String sc = mdc.get("status_code");
                    try {
                        logMap.put("status_code", Integer.parseInt(sc));
                    } catch (NumberFormatException e) {
                        logMap.put("status_code", sc);
                    }
                }
                if (mdc.containsKey("duration_ms")) {
                    String d = mdc.get("duration_ms");
                    try {
                        logMap.put("duration_ms", Long.parseLong(d));
                    } catch (NumberFormatException e) {
                        logMap.put("duration_ms", d);
                    }
                }

                // Copy any additional MDC keys (e.g. reservation_id, order_intent_id)
                for (Map.Entry<String, String> entry : mdc.entrySet()) {
                    String key = entry.getKey();
                    if (!logMap.containsKey(key)) {
                        logMap.put(key, entry.getValue());
                    }
                }
            }

            // Stack trace if exception present
            if (event.getThrowableProxy() != null) {
                String stackTrace = ThrowableProxyUtil.asString(event.getThrowableProxy());
                logMap.put("stack_trace", masker.mask(stackTrace));
            }

            return objectMapper.writeValueAsString(logMap);
        } catch (Exception e) {
            return "{\"level\":\"ERROR\",\"message\":\"Failed to format log event: " + e.getMessage() + "\"}";
        }
    }

    @Override
    public String doLayout(ILoggingEvent event) {
        return format(event) + System.lineSeparator();
    }
}
