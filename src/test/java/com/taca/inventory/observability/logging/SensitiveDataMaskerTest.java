package com.taca.inventory.observability.logging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SensitiveDataMasker Unit Tests")
class SensitiveDataMaskerTest {

    private final SensitiveDataMasker masker = new SensitiveDataMasker();

    @Test
    @DisplayName("Che giấu Authorization Bearer token và Basic auth")
    void mask_authorizationHeader() {
        String input = "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.token.signature";
        String masked = masker.mask(input);

        assertThat(masked).doesNotContain("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.token.signature");
        assertThat(masked).contains("Bearer [REDACTED]");
    }

    @ParameterizedTest(name = "Che giấu trường nhạy cảm: {0}")
    @ValueSource(strings = {"password", "secret", "client_secret", "access_token", "refresh_token"})
    void mask_jsonSensitiveFields(String fieldName) {
        String input = "{\"username\":\"admin\",\"" + fieldName + "\":\"p@ssw0rd123\",\"status\":\"ACTIVE\"}";
        String masked = masker.mask(input);

        assertThat(masked).doesNotContain("p@ssw0rd123");
        assertThat(masked).contains("\"" + fieldName + "\":\"[REDACTED]\"");
        assertThat(masked).contains("\"status\":\"ACTIVE\"");
    }

    @Test
    @DisplayName("Che giấu số thẻ tín dụng (PAN) và mã CVV")
    void mask_paymentData() {
        String input = "Payment payload: card=4532-1111-2222-3333 cvv=456 expire=12/28";
        String masked = masker.mask(input);

        assertThat(masked).doesNotContain("4532-1111-2222-3333");
        assertThat(masked).doesNotContain("cvv=456");
        assertThat(masked).contains("[CARD_REDACTED]");
    }

    @Test
    @DisplayName("Che giấu thông tin PII thô (email, số điện thoại)")
    void mask_piiInformation() {
        String input = "Customer info: email=alice.smith@example.com, phone=0987654321, address=123 Main St";
        String masked = masker.mask(input);

        assertThat(masked).doesNotContain("alice.smith@example.com");
        assertThat(masked).doesNotContain("0987654321");
    }

    @Test
    @DisplayName("Chuyển đổi full order payload sang safe summary chỉ giữ lại thông tin tồn kho cần thiết")
    void mask_orderPayloadToSafeSummary() {
        String orderJson = "{" +
                "\"order_id\":\"ord-001\"," +
                "\"customer_name\":\"Nguyen Van A\"," +
                "\"shipping_address\":\"123 Le Loi, Q1, HCM\"," +
                "\"payment_details\":{\"method\":\"CREDIT_CARD\",\"account\":\"1234-5678\"}," +
                "\"items\":[{\"sku_id\":\"sku-01912f33\",\"quantity\":2}]" +
                "}";

        String safeSummary = masker.summarizeOrderPayload(orderJson);

        assertThat(safeSummary).doesNotContain("Nguyen Van A");
        assertThat(safeSummary).doesNotContain("123 Le Loi, Q1, HCM");
        assertThat(safeSummary).doesNotContain("CREDIT_CARD");
        assertThat(safeSummary).contains("sku-01912f33");
        assertThat(safeSummary).contains("\"quantity\":2");
    }

    @Test
    @DisplayName("Giữ nguyên các trường payload tồn kho an toàn (sku_id, quantity, reason)")
    void mask_safeInventoryFieldsPreserved() {
        String inventoryJson = "{\"sku_id\":\"sku-01912f33\",\"quantity\":5,\"reason\":\"DAMAGED\"}";
        String masked = masker.mask(inventoryJson);

        assertThat(masked).contains("\"sku_id\":\"sku-01912f33\"");
        assertThat(masked).contains("\"quantity\":5");
        assertThat(masked).contains("\"reason\":\"DAMAGED\"");
    }

    @Test
    @DisplayName("Xử lý an toàn khi chuỗi null hoặc rỗng không gây NullPointerException")
    void mask_nullOrEmptyInputSafe() {
        assertThat(masker.mask(null)).isNull();
        assertThat(masker.mask("")).isEmpty();
        assertThat(masker.summarizeOrderPayload(null)).isNull();
    }
}
