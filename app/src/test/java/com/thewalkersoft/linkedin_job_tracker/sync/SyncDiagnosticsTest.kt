package com.thewalkersoft.linkedin_job_tracker.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncDiagnosticsTest {

    @Test
    fun redactJobUrl_removesQueryAndFragment() {
        val url = "https://www.linkedin.com/jobs/view/123456?trackingId=secret#detail"

        val redacted = SyncDiagnostics.redactJobUrl(url)

        assertEquals("https://www.linkedin.com/jobs/view/123456", redacted)
        assertFalse(redacted.contains("trackingId"))
        assertFalse(redacted.contains('#'))
    }

    @Test
    fun redactJobUrl_keepsUnparseableInputWithoutSecrets() {
        val url = "linkedin.com/jobs/view/123456?token=secret"

        val redacted = SyncDiagnostics.redactJobUrl(url)

        assertEquals("linkedin.com/jobs/view/123456", redacted)
    }

    @Test
    fun buildFailureReason_compactsWhitespaceAndCapsLength() {
        val reason = SyncDiagnostics.buildFailureReason(
            stage = "pushJob",
            detail = "  duplicate   key\nvalue  ",
            httpCode = 409
        )

        assertEquals("pushJob: HTTP 409: duplicate key value", reason)
        assertTrue(reason.length <= 180)
    }
}

