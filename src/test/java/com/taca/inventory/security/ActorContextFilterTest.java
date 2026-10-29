package com.taca.inventory.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ActorContextFilter Unit Tests")
class ActorContextFilterTest {

    private SecurityProperties securityProperties;
    private ActorContextFilter filter;

    @BeforeEach
    void setUp() {
        securityProperties = new SecurityProperties();
        securityProperties.setInternalToken("internal-secret-token-xyz");
        filter = new ActorContextFilter(securityProperties);
        ActorContext.clear();
    }

    @AfterEach
    void tearDown() {
        ActorContext.clear();
    }

    @Test
    @DisplayName("Trích xuất đầy đủ các headers Gateway (X-User-ID, X-User-Roles, X-User-Permissions, X-User-Shop-Scope) vào ActorContext")
    void doFilter_withAllGatewayHeaders_populatesActorContext() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-ID", "user-uuid-1234");
        request.addHeader("X-User-Roles", "SELLER,STAFF");
        request.addHeader("X-User-Permissions", "inventory:read,inventory:adjust");
        request.addHeader("X-User-Shop-Scope", "shop-uuid-5678");

        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<ActorContext> capturedContext = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> {
            capturedContext.set(ActorContext.getCurrent());
        });

        ActorContext ctx = capturedContext.get();
        assertThat(ctx).isNotNull();
        assertThat(ctx.getUserId()).isEqualTo("user-uuid-1234");
        assertThat(ctx.getRoles()).containsExactlyInAnyOrder("SELLER", "STAFF");
        assertThat(ctx.getPermissions()).containsExactlyInAnyOrder("inventory:read", "inventory:adjust");
        assertThat(ctx.getShopScope()).isEqualTo("shop-uuid-5678");
        assertThat(ctx.isSeller()).isTrue();
        assertThat(ctx.isInternalService()).isFalse();
    }

    @Test
    @DisplayName("Xác thực header nội bộ hợp lệ và đánh dấu ActorContext là internal service")
    void doFilter_withInternalServiceToken_identifiesInternalService() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Internal-Service-Token", "internal-secret-token-xyz");

        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<ActorContext> capturedContext = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> {
            capturedContext.set(ActorContext.getCurrent());
        });

        ActorContext ctx = capturedContext.get();
        assertThat(ctx).isNotNull();
        assertThat(ctx.isInternalService()).isTrue();
    }

    @Test
    @DisplayName("Dọn dẹp sạch ActorContext khỏi ThreadLocal sau khi kết thúc request")
    void doFilter_cleansUpContextAfterCompletion() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-ID", "user-999");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(ActorContext.getCurrent()).isNull();
    }

    @Test
    @DisplayName("Dọn dẹp sạch ActorContext kể cả khi FilterChain ném ngoại lệ")
    void doFilter_cleansUpContextEvenOnException() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-ID", "user-error");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain faultyChain = (ServletRequest req, ServletResponse res) -> {
            throw new RuntimeException("Simulated filter chain failure");
        };

        assertThatThrownBy(() -> filter.doFilter(request, response, faultyChain))
                .isInstanceOf(RuntimeException.class);

        assertThat(ActorContext.getCurrent()).isNull();
    }

    @Test
    @DisplayName("Không có header nào trả về context rỗng hoặc vô danh (anonymous)")
    void doFilter_missingHeaders_producesAnonymousContext() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<ActorContext> capturedContext = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> {
            capturedContext.set(ActorContext.getCurrent());
        });

        ActorContext ctx = capturedContext.get();
        assertThat(ctx == null || ctx.isAnonymous()).isTrue();
    }
}
