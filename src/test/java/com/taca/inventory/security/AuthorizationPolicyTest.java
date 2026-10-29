package com.taca.inventory.security;

import com.taca.inventory.common.error.ErrorCode;
import com.taca.inventory.common.error.InventoryException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AuthorizationPolicy Unit Tests")
class AuthorizationPolicyTest {

    private AuthorizationPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new AuthorizationPolicy();
        ActorContext.clear();
    }

    @AfterEach
    void tearDown() {
        ActorContext.clear();
    }

    @Nested
    @DisplayName("Internal Service Authentication Policy")
    class InternalServiceAuthTests {

        @Test
        @DisplayName("Cho phép khi tác nhân là dịch vụ nội bộ (Order service)")
        void requireInternalService_succeedsWhenInternalService() {
            ActorContext internalActor = ActorContext.builder()
                    .internalService(true)
                    .build();
            ActorContext.setCurrent(internalActor);

            assertThatCode(() -> policy.requireInternalService())
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Ném INVENTORY_UNAUTHENTICATED (401) khi thiếu ngữ cảnh tác nhân")
        void requireInternalService_throwsUnauthenticatedWhenNoContext() {
            ActorContext.clear();

            assertThatThrownBy(() -> policy.requireInternalService())
                    .isInstanceOf(InventoryException.class)
                    .satisfies(ex -> {
                        InventoryException ie = (InventoryException) ex;
                        org.assertj.core.api.Assertions.assertThat(ie.getErrorCode()).isEqualTo(ErrorCode.INVENTORY_UNAUTHENTICATED);
                    });
        }

        @Test
        @DisplayName("Ném INVENTORY_FORBIDDEN (403) khi tác nhân không phải internal service")
        void requireInternalService_throwsForbiddenWhenExternalUser() {
            ActorContext userActor = ActorContext.builder()
                    .userId("user-buyer-1")
                    .roles(Set.of("BUYER"))
                    .internalService(false)
                    .build();
            ActorContext.setCurrent(userActor);

            assertThatThrownBy(() -> policy.requireInternalService())
                    .isInstanceOf(InventoryException.class)
                    .satisfies(ex -> {
                        InventoryException ie = (InventoryException) ex;
                        org.assertj.core.api.Assertions.assertThat(ie.getErrorCode()).isEqualTo(ErrorCode.INVENTORY_FORBIDDEN);
                    });
        }
    }

    @Nested
    @DisplayName("Seller Shop Scope Policy (Chống IDOR)")
    class SellerShopScopeTests {

        @Test
        @DisplayName("Cho phép người bán thao tác trên SKU thuộc đúng gian hàng của mình")
        void requireSellerShopScope_succeedsWhenShopScopeMatches() {
            ActorContext sellerActor = ActorContext.builder()
                    .userId("seller-01")
                    .roles(Set.of("SELLER"))
                    .shopScope("shop-100")
                    .build();
            ActorContext.setCurrent(sellerActor);

            assertThatCode(() -> policy.requireSellerShopScope("shop-100"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Ném INVENTORY_FORBIDDEN (403) khi người bán cố ý truy cập SKU của gian hàng khác (IDOR)")
        void requireSellerShopScope_throwsForbiddenWhenShopScopeMismatches() {
            ActorContext sellerActor = ActorContext.builder()
                    .userId("seller-01")
                    .roles(Set.of("SELLER"))
                    .shopScope("shop-100")
                    .build();
            ActorContext.setCurrent(sellerActor);

            assertThatThrownBy(() -> policy.requireSellerShopScope("shop-200"))
                    .isInstanceOf(InventoryException.class)
                    .satisfies(ex -> {
                        InventoryException ie = (InventoryException) ex;
                        org.assertj.core.api.Assertions.assertThat(ie.getErrorCode()).isEqualTo(ErrorCode.INVENTORY_FORBIDDEN);
                    });
        }

        @Test
        @DisplayName("Ném INVENTORY_FORBIDDEN (403) khi người dùng không có vai trò SELLER")
        void requireSellerShopScope_throwsForbiddenWhenNotSeller() {
            ActorContext buyerActor = ActorContext.builder()
                    .userId("buyer-01")
                    .roles(Set.of("BUYER"))
                    .shopScope("shop-100")
                    .build();
            ActorContext.setCurrent(buyerActor);

            assertThatThrownBy(() -> policy.requireSellerShopScope("shop-100"))
                    .isInstanceOf(InventoryException.class)
                    .satisfies(ex -> {
                        InventoryException ie = (InventoryException) ex;
                        org.assertj.core.api.Assertions.assertThat(ie.getErrorCode()).isEqualTo(ErrorCode.INVENTORY_FORBIDDEN);
                    });
        }

        @Test
        @DisplayName("Ném INVENTORY_UNAUTHENTICATED (401) khi thiếu ngữ cảnh tác nhân")
        void requireSellerShopScope_throwsUnauthenticatedWhenNoContext() {
            ActorContext.clear();

            assertThatThrownBy(() -> policy.requireSellerShopScope("shop-100"))
                    .isInstanceOf(InventoryException.class)
                    .satisfies(ex -> {
                        InventoryException ie = (InventoryException) ex;
                        org.assertj.core.api.Assertions.assertThat(ie.getErrorCode()).isEqualTo(ErrorCode.INVENTORY_UNAUTHENTICATED);
                    });
        }
    }

    @Nested
    @DisplayName("Admin Step-Up Permission Policy")
    class AdminStepUpTests {

        @Test
        @DisplayName("Cho phép Admin thực hiện thao tác khi có đủ quyền nâng cao (step-up permission)")
        void requireAdminStepUp_succeedsWhenAdminHasPermission() {
            ActorContext adminActor = ActorContext.builder()
                    .userId("admin-01")
                    .roles(Set.of("ADMIN"))
                    .permissions(Set.of("inventory:reconcile", "inventory:admin_adjust"))
                    .build();
            ActorContext.setCurrent(adminActor);

            assertThatCode(() -> policy.requireAdminStepUp("inventory:reconcile"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Ném INVENTORY_FORBIDDEN (403) khi Admin không có quyền nâng cao tương ứng")
        void requireAdminStepUp_throwsForbiddenWhenMissingPermission() {
            ActorContext adminActor = ActorContext.builder()
                    .userId("admin-01")
                    .roles(Set.of("ADMIN"))
                    .permissions(Set.of("inventory:read"))
                    .build();
            ActorContext.setCurrent(adminActor);

            assertThatThrownBy(() -> policy.requireAdminStepUp("inventory:reconcile"))
                    .isInstanceOf(InventoryException.class)
                    .satisfies(ex -> {
                        InventoryException ie = (InventoryException) ex;
                        org.assertj.core.api.Assertions.assertThat(ie.getErrorCode()).isEqualTo(ErrorCode.INVENTORY_FORBIDDEN);
                    });
        }

        @Test
        @DisplayName("Ném INVENTORY_FORBIDDEN (403) khi người dùng không phải vai trò ADMIN")
        void requireAdminStepUp_throwsForbiddenWhenNotAdmin() {
            ActorContext sellerActor = ActorContext.builder()
                    .userId("seller-01")
                    .roles(Set.of("SELLER"))
                    .permissions(Set.of("inventory:reconcile"))
                    .build();
            ActorContext.setCurrent(sellerActor);

            assertThatThrownBy(() -> policy.requireAdminStepUp("inventory:reconcile"))
                    .isInstanceOf(InventoryException.class)
                    .satisfies(ex -> {
                        InventoryException ie = (InventoryException) ex;
                        org.assertj.core.api.Assertions.assertThat(ie.getErrorCode()).isEqualTo(ErrorCode.INVENTORY_FORBIDDEN);
                    });
        }
    }
}
