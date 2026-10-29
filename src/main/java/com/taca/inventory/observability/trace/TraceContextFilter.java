package com.taca.inventory.observability.trace;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Filter that intercepts incoming HTTP requests to extract W3C traceparent, tracestate,
 * and X-Request-ID headers, setting up TraceContext in ThreadLocal and MDC logging context.
 * Injects X-Request-ID into the HTTP response header and cleans up contexts upon completion.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceContextFilter implements Filter {

    public static final String HEADER_TRACEPARENT = "traceparent";
    public static final String HEADER_TRACESTATE = "tracestate";
    public static final String HEADER_REQUEST_ID = "X-Request-ID";

    public static final String MDC_TRACE_ID = "trace_id";
    public static final String MDC_SPAN_ID = "span_id";
    public static final String MDC_REQUEST_ID = "request_id";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (request instanceof HttpServletRequest httpRequest) {
            String traceparent = httpRequest.getHeader(HEADER_TRACEPARENT);
            String tracestate = httpRequest.getHeader(HEADER_TRACESTATE);
            String requestId = httpRequest.getHeader(HEADER_REQUEST_ID);
            if (requestId == null || requestId.isBlank()) {
                requestId = httpRequest.getHeader("x-request-id");
            }

            TraceContext context = TraceContext.fromHeaders(traceparent, tracestate, requestId);
            TraceContext.setCurrent(context);

            MDC.put(MDC_TRACE_ID, context.getTraceId());
            MDC.put(MDC_SPAN_ID, context.getSpanId());
            MDC.put(MDC_REQUEST_ID, context.getRequestId());

            if (response instanceof HttpServletResponse httpResponse) {
                httpResponse.setHeader(HEADER_REQUEST_ID, context.getRequestId());
            }

            try {
                chain.doFilter(request, response);
            } finally {
                TraceContext.clear();
                MDC.remove(MDC_TRACE_ID);
                MDC.remove(MDC_SPAN_ID);
                MDC.remove(MDC_REQUEST_ID);
            }
        } else {
            chain.doFilter(request, response);
        }
    }
}
