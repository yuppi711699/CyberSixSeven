package com.cybersixseven.reward

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LogRedactorTest {
    @Test
    fun redactsSecretsAndLeavesIds() {
        val redacted = LogRedactor.redact("password=hunter2 Authorization: Bearer aaa.bbb.ccc")
        assertFalse(redacted!!.contains("hunter2"))
        assertFalse(redacted.contains("aaa.bbb.ccc"))
        assertEquals(
            "commandId=22222222-2222-2222-2222-222222222222",
            LogRedactor.redact("commandId=22222222-2222-2222-2222-222222222222"),
        )
        assertNull(LogRedactor.redact(null))
        assertEquals("", LogRedactor.redact(""))
    }
}
