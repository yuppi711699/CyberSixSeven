package com.cybersixseven.platformapi.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class CorrelationTest {

    @AfterEach
    void clearMdc() {
        Correlation.clear();
    }

    @Test
    void putWritesIdsAndClearRemovesThem() {
        UUID submissionId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID commandId = UUID.fromString("22222222-2222-2222-2222-222222222222");
        Correlation.put(submissionId, commandId, "esp32-dev-001");
        assertEquals(submissionId.toString(), MDC.get(Correlation.SUBMISSION_ID));
        assertEquals(commandId.toString(), MDC.get(Correlation.COMMAND_ID));
        assertEquals("esp32-dev-001", MDC.get(Correlation.DEVICE_ID));

        Correlation.clear();
        assertNull(MDC.get(Correlation.SUBMISSION_ID));
        assertNull(MDC.get(Correlation.COMMAND_ID));
        assertNull(MDC.get(Correlation.DEVICE_ID));
    }

    @Test
    void blanksAndNullsAreRemoved() {
        MDC.put(Correlation.DEVICE_ID, "stale");
        Correlation.put(null, null, "  ");
        assertNull(MDC.get(Correlation.SUBMISSION_ID));
        assertNull(MDC.get(Correlation.COMMAND_ID));
        assertNull(MDC.get(Correlation.DEVICE_ID));
    }
}
