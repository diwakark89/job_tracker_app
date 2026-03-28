package com.thewalkersoft.linkedin_job_tracker.scraper

import org.junit.Assert.assertEquals
import org.junit.Test

class JobScraperTest {
    @Test
    fun normalizeLineBreaks_limitsConsecutiveBlankLines() {
        val input = "Line 1\n\n\n\nLine 2\n\n   \n\nLine 3\n\r\n\r\n\r\nLine 4"

        val result = JobScraper.normalizeLineBreaks(input)

        val expected = "Line 1\n\nLine 2\n\nLine 3\n\nLine 4"
        assertEquals(expected, result)
    }

    @Test
    fun extractTextFromHtmlWithLineBreaks_limitsBlankLines() {
        val html = """
            <p>Line 1</p>
            <div><br><br></div>
            <p>Line 2</p>
            <ul>
                <li>Item A</li>
                <li>Item B</li>
            </ul>
            <div><br><br><br></div>
            <p>Line 3</p>
        """.trimIndent()

        val result = JobScraper.extractTextFromHtmlWithLineBreaks(html)

        val pattern = Regex("Line 1\\n{1,2}Line 2\\n{1,2}• Item A\\n{1,2}• Item B\\n{1,2}Line 3")
        assertEquals(true, pattern.matches(result))
        assertEquals(false, result.contains("\n\n\n"))
    }
}
