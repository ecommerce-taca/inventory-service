package com.taca.inventory.common.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Global REST exception handler mapping exceptions to standard ErrorEnvelope.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(InventoryException.class)
    public ResponseEntity<ErrorEnvelope> handleInventoryException(InventoryException ex) {
        ErrorCode errorCode = ex.getErrorCode();
        HttpStatus status = (errorCode != null) ? errorCode.getHttpStatus() : HttpStatus.INTERNAL_SERVER_ERROR;
        String code = (errorCode != null) ? errorCode.name() : ErrorCode.INVENTORY_INTERNAL_ERROR.name();
        String message = ex.getMessage() != null ? ex.getMessage() : (errorCode != null ? errorCode.getDefaultMessage() : "Internal Server Error");

        if (status.is5xxServerError()) {
            log.error("Inventory error [{}]: {}", code, message, ex);
        } else {
            log.warn("Inventory client error [{}]: {}", code, message);
        }

        ErrorEnvelope envelope = ErrorEnvelope.of(code, message, ex.getDetails(), resolveTraceId());
        return ResponseEntity.status(status).body(envelope);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorEnvelope> handleBindException(BindException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
        }
        for (ObjectError globalError : ex.getBindingResult().getGlobalErrors()) {
            if (!fieldErrors.containsKey(globalError.getObjectName())) {
                fieldErrors.put(globalError.getObjectName(), globalError.getDefaultMessage());
            }
        }
        if (fieldErrors.isEmpty()) {
            fieldErrors.put("error", "Validation failed");
        }

        log.warn("Validation failure: {}", fieldErrors);

        ErrorEnvelope envelope = ErrorEnvelope.of(
                ErrorCode.INVENTORY_INVALID_INPUT.name(),
                "Validation failed",
                fieldErrors,
                resolveTraceId()
        );
        return ResponseEntity.status(ErrorCode.INVENTORY_INVALID_INPUT.getHttpStatus()).body(envelope);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorEnvelope> handleHandlerMethodValidation(HandlerMethodValidationException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getParameterValidationResults().forEach(result -> {
            String param = result.getMethodParameter().getParameterName();
            result.getResolvableErrors().forEach(err -> {
                errors.put(param != null ? param : "parameter", err.getDefaultMessage());
            });
        });
        if (errors.isEmpty()) {
            errors.put("error", "Validation failed");
        }

        log.warn("Method validation failure: {}", errors);

        ErrorEnvelope envelope = ErrorEnvelope.of(
                ErrorCode.INVENTORY_INVALID_INPUT.name(),
                "Validation failed",
                errors,
                resolveTraceId()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(envelope);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorEnvelope> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Malformed HTTP message body: {}", ex.getMessage());

        String causeMsg = ex.getMostSpecificCause() != null ? ex.getMostSpecificCause().getMessage() : ex.getMessage();
        ErrorEnvelope envelope = ErrorEnvelope.of(
                ErrorCode.INVENTORY_INVALID_INPUT.name(),
                "Malformed request body",
                Map.of("error", causeMsg != null ? causeMsg : "Unreadable request body"),
                resolveTraceId()
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(envelope);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorEnvelope> handleUnhandledException(Exception ex) {
        log.error("Unhandled exception caught: {}", ex.getMessage(), ex);

        ErrorEnvelope envelope = ErrorEnvelope.of(
                ErrorCode.INVENTORY_INTERNAL_ERROR.name(),
                "Internal Server Error",
                null,
                resolveTraceId()
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(envelope);
    }

    private String resolveTraceId() {
        String traceId = MDC.get("trace_id");
        if (traceId != null && !traceId.isBlank()) {
            return traceId;
        }
        traceId = MDC.get("traceId");
        if (traceId != null && !traceId.isBlank()) {
            return traceId;
        }
        return null;
    }
}
