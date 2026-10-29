package com.taca.inventory.observability.logging;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Filter that captures incoming HTTP request metadata (route, method),
 * calculates execution duration, extracts HTTP status code, and logs completion
 * in structured JSON format via MDC context.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class HttpLoggingFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(HttpLoggingFilter.class);

    public static final String MDC_ROUTE = "route";
    public static final String MDC_METHOD = "method";
    public static final String MDC_STATUS_CODE = "status_code";
    public static final String MDC_DURATION_MS = "duration_ms";
    public static final String MDC_EVENT = "event";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest httpRequest && response instanceof HttpServletResponse httpResponse) {
            long startTime = System.currentTimeMillis();
            String route = httpRequest.getRequestURI();
            String method = httpRequest.getMethod();

            MDC.put(MDC_ROUTE, route);
            MDC.put(MDC_METHOD, method);

            try {
                chain.doFilter(request, response);
            } finally {
                long durationMs = System.currentTimeMillis() - startTime;
                int statusCode = httpResponse.getStatus();

                MDC.put(MDC_ROUTE, route);
                MDC.put(MDC_METHOD, method);
                MDC.put(MDC_STATUS_CODE, String.valueOf(statusCode));
                MDC.put(MDC_DURATION_MS, String.valueOf(durationMs));
                MDC.put(MDC_EVENT, "http.request.completed");

                log.info("HTTP {} {} completed with status {} in {} ms", method, route, statusCode, durationMs);

                MDC.remove(MDC_ROUTE);
                MDC.remove(MDC_METHOD);
                MDC.remove(MDC_STATUS_CODE);
                MDC.remove(MDC_DURATION_MS);
                MDC.remove(MDC_EVENT);
            }
        } else {
            chain.doFilter(request, response);
        }
    }
}
