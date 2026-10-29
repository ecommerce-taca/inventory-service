package com.taca.inventory.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Standard success response envelope adhering to DC-02 and PC-07.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        @JsonProperty("data") T data,
        @JsonProperty("meta") Meta meta
) {

    public static <T> ApiResponse<T> of(T data, String requestId) {
        return new ApiResponse<>(data, new Meta(requestId));
    }

    public static <T> ApiResponse<T> of(T data, Meta meta) {
        return new ApiResponse<>(data, meta);
    }

    public static <T> ApiResponse<T> of(
            T data,
            String requestId,
            Integer page,
            Integer size,
            Long total,
            Integer totalPages
    ) {
        return new ApiResponse<>(data, new Meta(requestId, page, size, total, totalPages));
    }

    public T getData() {
        return data;
    }

    public Meta getMeta() {
        return meta;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Meta(
            @JsonProperty("request_id") String requestId,
            @JsonProperty("page") Integer page,
            @JsonProperty("size") Integer size,
            @JsonProperty("total") Long total,
            @JsonProperty("total_pages") Integer totalPages
    ) {

        public Meta(String requestId) {
            this(requestId, null, null, null, null);
        }

        @JsonProperty("request_id")
        public String getRequestId() {
            return requestId;
        }

        public Integer getPage() {
            return page;
        }

        public Integer getSize() {
            return size;
        }

        public Long getTotal() {
            return total;
        }

        @JsonProperty("total_pages")
        public Integer getTotalPages() {
            return totalPages;
        }
    }
}
