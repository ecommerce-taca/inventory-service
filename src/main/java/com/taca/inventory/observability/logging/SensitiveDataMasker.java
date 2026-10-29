package com.taca.inventory.observability.logging;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Utility responsible for masking sensitive information such as tokens,
 * passwords, secrets, card PAN, CVV, PII (email, phone), and summarizing
 * full order payloads into safe inventory-focused summaries.
 */
public class SensitiveDataMasker {

    private final ObjectMapper objectMapper;

    // Precompiled regex patterns for optimal performance per PC-09
    private static final Pattern AUTH_PATTERN = Pattern.compile(
            "(?i)\\b(Bearer|Basic)\\s+(?!\\[REDACTED\\])[A-Za-z0-9.\\-_~+/]+=*"
    );

    private static final Pattern JSON_SENSITIVE_FIELDS_PATTERN = Pattern.compile(
            "\"([a-zA-Z0-9_-]*(?:password|secret|access_token|refresh_token)[a-zA-Z0-9_-]*)\"(\\s*:\\s*)\"(?!\\[REDACTED\\])[^\"]*\"",
            Pattern.CASE_INSENSITIVE
    );

    // Credit card PAN: 4 groups of 4 digits separated by dash or space
    private static final Pattern CREDIT_CARD_PATTERN = Pattern.compile(
            "\\b(?:\\d{4}[- ]){3}\\d{4}\\b"
    );

    // CVV / CVC: 3 or 4 digits
    private static final Pattern CVV_PATTERN = Pattern.compile(
            "(?i)\\b(cvv|cvc|cid)\\s*[:=]\\s*\\d{3,4}\\b"
    );

    private static final Pattern CVV_JSON_PATTERN = Pattern.compile(
            "(?i)\"(cvv|cvc|cid)\"(\\s*:\\s*)\"?\\d{3,4}\"?"
    );

    // Email pattern
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b"
    );

    // Phone pattern with key label e.g. phone=0987654321
    private static final Pattern PHONE_LABELED_PATTERN = Pattern.compile(
            "(?i)\\b(phone|mobile|tel|telephone)\\s*[:=]\\s*[\"']?(\\+?[0-9\\s-]{9,15})[\"']?"
    );

    // Standalone Vietnamese phone number pattern (10 digits starting with 03, 05, 07, 08, 09 or +84)
    private static final Pattern PHONE_STANDALONE_PATTERN = Pattern.compile(
            "(?<!\\w)(?:\\+?84|0)[35789]\\d{8}(?!\\w)"
    );

    public SensitiveDataMasker() {
        this.objectMapper = new ObjectMapper();
    }

    public SensitiveDataMasker(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    /**
     * Masks sensitive tokens, credentials, payment data, and PII from the given input string.
     *
     * @param input the input string to mask
     * @return the masked string, or null if input is null
     */
    public String mask(String input) {
        if (input == null) {
            return null;
        }
        if (input.isEmpty()) {
            return "";
        }

        String result = input;

        // 1. Mask Bearer / Basic auth tokens
        result = AUTH_PATTERN.matcher(result).replaceAll("$1 [REDACTED]");

        // 2. Mask JSON sensitive fields (password, secret, client_secret, access_token, refresh_token, etc.)
        result = JSON_SENSITIVE_FIELDS_PATTERN.matcher(result).replaceAll("\"$1\"$2\"[REDACTED]\"");

        // 3. Mask Payment card numbers (PAN)
        result = CREDIT_CARD_PATTERN.matcher(result).replaceAll("[CARD_REDACTED]");

        // 4. Mask CVV
        result = CVV_PATTERN.matcher(result).replaceAll("$1=[REDACTED]");
        result = CVV_JSON_PATTERN.matcher(result).replaceAll("\"$1\"$2\"[REDACTED]\"");

        // 5. Mask Email
        result = EMAIL_PATTERN.matcher(result).replaceAll("[REDACTED]");

        // 6. Mask Phone numbers
        result = PHONE_LABELED_PATTERN.matcher(result).replaceAll("$1=[REDACTED]");
        result = PHONE_STANDALONE_PATTERN.matcher(result).replaceAll("[REDACTED]");

        return result;
    }

    /**
     * Converts a full order payload into a safe summary containing only essential inventory information,
     * stripping customer PII, addresses, and payment details.
     *
     * @param payload the raw order payload JSON
     * @return a safe summarized JSON, or null if input is null
     */
    public String summarizeOrderPayload(String payload) {
        if (payload == null) {
            return null;
        }
        if (payload.isBlank()) {
            return payload;
        }

        try {
            JsonNode root = objectMapper.readTree(payload);
            if (root.isObject()) {
                Map<String, Object> summary = new LinkedHashMap<>();
                if (root.has("order_id")) {
                    summary.put("order_id", root.get("order_id").asText());
                }
                if (root.has("items") && root.get("items").isArray()) {
                    List<Map<String, Object>> safeItems = new ArrayList<>();
                    for (JsonNode item : root.get("items")) {
                        Map<String, Object> safeItem = new LinkedHashMap<>();
                        if (item.has("sku_id")) {
                            safeItem.put("sku_id", item.get("sku_id").asText());
                        }
                        if (item.has("quantity")) {
                            safeItem.put("quantity", item.get("quantity").asInt());
                        }
                        safeItems.add(safeItem);
                    }
                    summary.put("items", safeItems);
                }
                return objectMapper.writeValueAsString(summary);
            }
        } catch (Exception e) {
            // Fallback to masking sensitive data if payload cannot be parsed as JSON object
            return mask(payload);
        }

        return mask(payload);
    }
}
