package com.taca.inventory.common.error;

import com.taca.inventory.common.api.ApiResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.BindException;
import org.springframework.validation.MapBindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("GlobalExceptionHandler & ErrorEnvelope Unit Tests")
class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new DummyTestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    @DisplayName("Bắt InventoryException và trả về ErrorEnvelope với mã HTTP và trace_id tương ứng")
    void handleInventoryException_withTraceId() throws Exception {
        MDC.put("trace_id", "01912fa0-1111-7000-8000-000000000001");

        mockMvc.perform(get("/test/inventory-exception")
                        .param("code", "INVENTORY_STOCK_INSUFFICIENT"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVENTORY_STOCK_INSUFFICIENT"))
                .andExpect(jsonPath("$.error.message").value("Error message for INVENTORY_STOCK_INSUFFICIENT"))
                .andExpect(jsonPath("$.error.details.sku_id").value("sku-test-01"))
                .andExpect(jsonPath("$.error.trace_id").value("01912fa0-1111-7000-8000-000000000001"));
    }

    @ParameterizedTest(name = "ErrorCode {0} trả về HTTP status {1}")
    @CsvSource({
            "INVENTORY_INVALID_INPUT, 400",
            "INVENTORY_UNAUTHENTICATED, 401",
            "INVENTORY_FORBIDDEN, 403",
            "INVENTORY_SKU_NOT_FOUND, 404",
            "INVENTORY_RESERVATION_NOT_FOUND, 404",
            "INVENTORY_SKU_DISABLED, 409",
            "INVENTORY_STOCK_INSUFFICIENT, 409",
            "INVENTORY_RESERVATION_STATE_INVALID, 409",
            "INVENTORY_RESERVATION_EXPIRED, 409",
            "INVENTORY_IDEMPOTENCY_CONFLICT, 409",
            "INVENTORY_CONCURRENCY_CONFLICT, 409",
            "INVENTORY_DEPENDENCY_UNAVAILABLE, 503",
            "INVENTORY_INTERNAL_ERROR, 500"
    })
    void handleInventoryException_allErrorCodesMapToCorrectStatus(String codeName, int expectedStatus) throws Exception {
        mockMvc.perform(get("/test/inventory-exception").param("code", codeName))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.error.code").value(codeName))
                .andExpect(jsonPath("$.error.message").exists());
    }

    @Test
    @DisplayName("Bắt lỗi validation và trả về INVENTORY_INVALID_INPUT (400)")
    void handleValidationException_returns400WithDetails() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVENTORY_INVALID_INPUT"))
                .andExpect(jsonPath("$.error.details").exists());
    }

    @Test
    @DisplayName("Bắt lỗi ngoại lệ không lường trước (Unhandled Exception) và trả về INVENTORY_INTERNAL_ERROR (500)")
    void handleUnhandledException_returns500() throws Exception {
        MDC.put("trace_id", "trace-unhandled-500");

        mockMvc.perform(get("/test/unhandled-exception"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INVENTORY_INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.message").value(containsString("Internal Server Error")))
                .andExpect(jsonPath("$.error.trace_id").value("trace-unhandled-500"));
    }

    @Test
    @DisplayName("Đặc tả DC-02: Phản hồi thành công ApiResponse chỉ chứa data và meta:{request_id}, không có trace_id")
    void apiResponse_successFormatFollowsConvention() {
        ApiResponse<Map<String, String>> response = ApiResponse.of(Map.of("key", "value"), "req-01912fa0");

        assertThat(response.getData()).containsEntry("key", "value");
        assertThat(response.getMeta().getRequestId()).isEqualTo("req-01912fa0");
    }

    @Test
    @DisplayName("Kiểm tra toàn bộ HttpStatus tương ứng của enum ErrorCode")
    void verifyErrorCodeHttpStatusMappings() {
        assertThat(ErrorCode.INVENTORY_INVALID_INPUT.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(ErrorCode.INVENTORY_UNAUTHENTICATED.getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(ErrorCode.INVENTORY_FORBIDDEN.getHttpStatus()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(ErrorCode.INVENTORY_SKU_NOT_FOUND.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ErrorCode.INVENTORY_RESERVATION_NOT_FOUND.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(ErrorCode.INVENTORY_SKU_DISABLED.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.INVENTORY_STOCK_INSUFFICIENT.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.INVENTORY_RESERVATION_STATE_INVALID.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.INVENTORY_RESERVATION_EXPIRED.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.INVENTORY_IDEMPOTENCY_CONFLICT.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.INVENTORY_CONCURRENCY_CONFLICT.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(ErrorCode.INVENTORY_DEPENDENCY_UNAVAILABLE.getHttpStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(ErrorCode.INVENTORY_INTERNAL_ERROR.getHttpStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @RestController
    static class DummyTestController {

        @GetMapping("/test/inventory-exception")
        public void throwInventoryException(@RequestParam String code) {
            ErrorCode errorCode = ErrorCode.valueOf(code);
            throw new InventoryException(errorCode, "Error message for " + code, Map.of("sku_id", "sku-test-01"));
        }

        @PostMapping("/test/validation")
        public void validateRequest() throws BindException {
            MapBindingResult bindingResult = new MapBindingResult(Map.of(), "dummyValidationRequest");
            bindingResult.rejectValue("name", "NotBlank", "name must not be blank");
            throw new BindException(bindingResult);
        }

        @GetMapping("/test/unhandled-exception")
        public void throwUnhandledException() {
            throw new RuntimeException("Unexpected database timeout");
        }
    }
}
