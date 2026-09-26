package com.cybersixseven.platformapi.observability;

import java.util.UUID;
import org.slf4j.MDC;

/** MDC keys the OTel javaagent also fills (`trace_id`, `span_id`) when it is attached. */
public final class Correlation {

    public static final String SUBMISSION_ID = "submissionId";
    public static final String COMMAND_ID = "commandId";
    public static final String DEVICE_ID = "deviceId";

    private Correlation() {}

    public static void put(UUID submissionId, UUID commandId, String deviceId) {
        put(SUBMISSION_ID, submissionId == null ? null : submissionId.toString());
        put(COMMAND_ID, commandId == null ? null : commandId.toString());
        put(DEVICE_ID, deviceId);
    }

    public static void clear() {
        MDC.remove(SUBMISSION_ID);
        MDC.remove(COMMAND_ID);
        MDC.remove(DEVICE_ID);
    }

    private static void put(String key, String value) {
        if (value == null || value.isBlank()) {
            MDC.remove(key);
            return;
        }
        MDC.put(key, value);
    }
}
