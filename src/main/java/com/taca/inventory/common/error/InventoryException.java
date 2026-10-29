package com.taca.inventory.common.error;

/**
 * Domain and operational runtime exception for Inventory Service.
 */
public class InventoryException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Object details;

    public InventoryException(ErrorCode errorCode) {
        this(errorCode, errorCode != null ? errorCode.getDefaultMessage() : null, null, null);
    }

    public InventoryException(ErrorCode errorCode, String message) {
        this(errorCode, message, null, null);
    }

    public InventoryException(ErrorCode errorCode, String message, Object details) {
        this(errorCode, message, details, null);
    }

    public InventoryException(ErrorCode errorCode, Throwable cause) {
        this(errorCode, errorCode != null ? errorCode.getDefaultMessage() : null, null, cause);
    }

    public InventoryException(ErrorCode errorCode, String message, Throwable cause) {
        this(errorCode, message, null, cause);
    }

    public InventoryException(ErrorCode errorCode, String message, Object details, Throwable cause) {
        super(message != null ? message : (errorCode != null ? errorCode.getDefaultMessage() : null), cause);
        this.errorCode = errorCode;
        this.details = details;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public Object getDetails() {
        return details;
    }
}
