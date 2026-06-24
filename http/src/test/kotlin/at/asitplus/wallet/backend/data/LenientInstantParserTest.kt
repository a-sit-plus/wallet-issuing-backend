package at.asitplus.wallet.backend.data

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class LenientInstantParserTest {

    @Test
    fun testParseWithoutTimeZone() {
        val input = "2022-12-01T14:00:00"

        val instant = LenientInstantParser.parse(input)

        assertNotNull(instant)
        assertEquals("2022-12-01T14:00:00Z", instant.toString())
    }

    @Test
    fun testParseWithTimeZone() {
        val input = "2022-12-01T14:00:00Z"

        val instant = LenientInstantParser.parse(input)

        assertNotNull(instant)
        assertEquals("2022-12-01T14:00:00Z", instant.toString())
    }

}