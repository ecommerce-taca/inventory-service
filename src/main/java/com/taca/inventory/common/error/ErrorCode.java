package com.taca.inventory.common.error;

import org.springframework.http.HttpStatus;

/**
 * Standard error codes for Inventory Service.
 */
public enum ErrorCode {

    INVENTORY_INVALID_INPUT(HttpStatus.BAD_REQUEST, "Invalid input"),
    INVENTORY_UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Unauthenticated"),
    INVENTORY_FORBIDDEN(HttpStatus.FORBIDDEN, "Access denied"),
    INVENTORY_SKU_NOT_FOUND(HttpStatus.NOT_FOUND, "SKU not found"),
    INVENTORY_RESERVATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Reservation not found"),
    INVENTORY_SKU_DISABLED(HttpStatus.CONFLICT, "SKU is disabled"),
    INVENTORY_STOCK_INSUFFICIENT(HttpStatus.CONFLICT, "Stock insufficient"),
    INVENTORY_RESERVATION_STATE_INVALID(HttpStatus.CONFLICT, "Reservation state invalid"),
    INVENTORY_RESERVATION_EXPIRED(HttpStatus.CONFLICT, "Reservation expired"),
    INVENTORY_IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "Idempotency conflict"),
    INVENTORY_CONCURRENCY_CONFLICT(HttpStatus.CONFLICT, "Concurrency conflict"),
    INVENTORY_DEPENDENCY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "Dependency unavailable"),
    INVENTORY_INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
