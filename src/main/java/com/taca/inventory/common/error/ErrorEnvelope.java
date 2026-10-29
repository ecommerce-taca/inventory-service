package com.taca.inventory.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Standard error envelope following convention PC-07: {error: {code, message, details, trace_id}}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorEnvelope(
        @JsonProperty("error") ErrorBody error
) {

    public static ErrorEnvelope of(String code, String message, Object details, String traceId) {
        return new ErrorEnvelope(new ErrorBody(code, message, details, traceId));
    }

    public static ErrorEnvelope of(ErrorCode errorCode, String message, Object details, String traceId) {
        return of(errorCode != null ? errorCode.name() : null, message, details, traceId);
    }

    public static ErrorEnvelope of(ErrorCode errorCode, String message) {
        return of(errorCode, message, null, null);
    }

    public static ErrorEnvelope of(ErrorCode errorCode) {
        return of(errorCode, errorCode != null ? errorCode.getDefaultMessage() : null, null, null);
    }

    public ErrorBody getError() {
        return error;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorBody(
            @JsonProperty("code") String code,
            @JsonProperty("message") String message,
            @JsonProperty("details") Object details,
            @JsonProperty("trace_id") String traceId
    ) {

        public String getCode() {
            return code;
        }

        public String getMessage() {
            return message;
        }

        public Object getDetails() {
            return details;
        }

        @JsonProperty("trace_id")
        public String getTraceId() {
            return traceId;
        }
    }
}
