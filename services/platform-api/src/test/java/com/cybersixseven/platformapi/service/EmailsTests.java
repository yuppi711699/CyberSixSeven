package com.cybersixseven.platformapi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class EmailsTests {

    @Test
    void normalizesTrimAndCase() {
        assertEquals("pat@example.test", Emails.normalize("  Pat@Example.TEST "));
    }

    @Test
    void rejectsMissingOrMalformed() {
        assertThrows(InvalidAuthException.class, () -> Emails.normalize(null));
        assertThrows(InvalidAuthException.class, () -> Emails.normalize(" "));
        assertThrows(InvalidAuthException.class, () -> Emails.normalize("not-an-email"));
        assertThrows(InvalidAuthException.class, () -> Emails.normalize("@x.test"));
    }
}
