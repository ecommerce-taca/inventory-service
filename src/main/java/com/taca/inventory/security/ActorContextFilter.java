package com.taca.inventory.security;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Filter responsible for extracting actor context headers injected by API Gateway
 * and setting up ActorContext in ThreadLocal for each incoming HTTP request.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ActorContextFilter implements Filter {

    public static final String HEADER_USER_ID = "X-User-ID";
    public static final String HEADER_USER_ROLES = "X-User-Roles";
    public static final String HEADER_USER_PERMISSIONS = "X-User-Permissions";
    public static final String HEADER_USER_SHOP_SCOPE = "X-User-Shop-Scope";
    public static final String HEADER_INTERNAL_SERVICE_TOKEN = "X-Internal-Service-Token";

    private final SecurityProperties securityProperties;

    public ActorContextFilter(SecurityProperties securityProperties) {
        this.securityProperties = securityProperties != null ? securityProperties : new SecurityProperties();
    }

    public ActorContextFilter() {
        this(new SecurityProperties());
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        try {
            if (request instanceof HttpServletRequest httpRequest) {
                String userId = httpRequest.getHeader(HEADER_USER_ID);
                String rolesHeader = httpRequest.getHeader(HEADER_USER_ROLES);
                String permissionsHeader = httpRequest.getHeader(HEADER_USER_PERMISSIONS);
                String shopScope = httpRequest.getHeader(HEADER_USER_SHOP_SCOPE);
                String internalToken = httpRequest.getHeader(HEADER_INTERNAL_SERVICE_TOKEN);

                boolean internalService = false;
                if (internalToken != null && !internalToken.isBlank()
                        && securityProperties.getInternalToken() != null
                        && MessageDigest.isEqual(
                                securityProperties.getInternalToken().getBytes(StandardCharsets.UTF_8),
                                internalToken.getBytes(StandardCharsets.UTF_8))) {
                    internalService = true;
                }

                Set<String> roles = parseCsv(rolesHeader);
                Set<String> permissions = parseCsv(permissionsHeader);

                ActorContext actorContext = ActorContext.builder()
                        .userId(userId)
                        .roles(roles)
                        .permissions(permissions)
                        .shopScope(shopScope)
                        .internalService(internalService)
                        .build();

                ActorContext.setCurrent(actorContext);
            }
            chain.doFilter(request, response);
        } finally {
            ActorContext.clear();
        }
    }

    private Set<String> parseCsv(String header) {
        if (header == null || header.isBlank()) {
            return Collections.emptySet();
        }
        return Arrays.stream(header.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }
}
