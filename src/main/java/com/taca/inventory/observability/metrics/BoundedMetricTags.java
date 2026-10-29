package com.taca.inventory.observability.metrics;

import java.util.Map;
import java.util.Set;

/**
 * Quản lý tập nhãn hữu hạn (Bounded Metric Tags) theo quy ước PC-10.
 * Ngăn chặn tuyệt đối việc đưa các nhãn high-cardinality (sku_id, order_id, user_id, reservation_id, ...)
 * vào hệ thống metrics gây tràn bộ nhớ time-series database.
 */
public final class BoundedMetricTags {

    public static final String TAG_OPERATION = "operation";
    public static final String TAG_RESULT = "result";
    public static final String TAG_REASON = "reason";
    public static final String TAG_SERVICE_DEPENDENCY = "service_dependency";

    private static final Set<String> ALLOWED_TAGS = Set.of(
            TAG_OPERATION,
            TAG_RESULT,
            TAG_REASON,
            TAG_SERVICE_DEPENDENCY
    );

    private BoundedMetricTags() {
        // Utility class
    }

    public static Set<String> getAllowedTagNames() {
        return ALLOWED_TAGS;
    }

    public static boolean isAllowed(String key) {
        return key != null && ALLOWED_TAGS.contains(key);
    }

    public static void validateKey(String key) {
        if (!isAllowed(key)) {
            throw new IllegalArgumentException(
                    "Metric tag key '" + key + "' is disallowed. High-cardinality tags are forbidden (PC-10). "
                            + "Allowed bounded tags: " + ALLOWED_TAGS
            );
        }
    }

    public static void validateTags(Map<String, String> tags) {
        if (tags != null) {
            for (String key : tags.keySet()) {
                validateKey(key);
            }
        }
    }
}
