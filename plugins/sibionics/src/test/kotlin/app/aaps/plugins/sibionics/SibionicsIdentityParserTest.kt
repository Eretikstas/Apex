package app.aaps.plugins.sibionics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The same QR vectors and expected identities as JugglucoNG's SibionicsDeviceBindingTest / SibionicsProbePersistenceTest. */
class SibionicsIdentityParserTest {

    @Test
    fun `framed V120 QR gives the official identity window`() {
        val id = SibionicsIdentityParser.parse("\u001D0106972831641476112507301726072910LT46250683C\u001D21P2250683013AQT98")!!
        assertEquals("0683013AQT9", id.displayName)
        assertEquals("SIBI:0683013AQT9", id.sensorId)
        assertEquals("0683013A", id.shortCode)
        assertTrue(id.fromQr)
    }

    @Test
    fun `V120 lot keeps its own sensitivity`() {
        val id = SibionicsIdentityParser.parse("\u001D0106972831641476112512131727061210LT46251210C\u001D21P225121023GGFR60")!!
        assertEquals("121023GG", id.shortCode)
        assertEquals(1.39f, id.sensitivity, 0.0001f)
        assertTrue(id.sensitivityFromCode)
    }

    @Test
    fun `XPT serial uses the type fallback sensitivity`() {
        val id = SibionicsIdentityParser.parse("\u001D0106972831641476112512181727061710LT46251212C\u001D21XPT1EEX2NRU16U")!!
        assertEquals("1EEX2NRU16U", id.displayName)
        assertEquals("1EEX2NRU", id.shortCode)
        assertEquals(1.44f, id.sensitivity, 0.0001f)
        assertFalse(id.sensitivityFromCode)
    }

    @Test
    fun `probe code in the QR gives the factory sensitivity`() {
        val id = SibionicsIdentityParser.parse("\u001D0106972831641476112602081727080710LT46260201C\u001D21EU2VCZUQPSHD5Q")!!
        assertEquals("SIBI:VCZUQPSHD5Q", id.sensorId)
        assertEquals("VCZUQPSH", id.shortCode)
        assertEquals("EU2VCZUQPSHD5Q", id.probeCode)
        assertEquals(1.73f, id.sensitivity, 0.00001f)
    }

    @Test
    fun `scanner prefix and missing separators are understood`() {
        assertTrue(SibionicsIdentityParser.isSensorQr("]d2" + "0106972831641476112512181727061710LT46251212C\u001D21XPT1EEX2NRU16U"))
    }

    @Test
    fun `other text is not a sensor`() {
        assertNull(SibionicsIdentityParser.parse(null))
        assertNull(SibionicsIdentityParser.parse("hello"))
        assertNull(SibionicsIdentityParser.parse("YAICOMVK1HE1F5EE"))
    }

    @Test
    fun `transmitter names of Sibionics 2`() {
        assertTrue(SibionicsIdentityParser.isSibionics2Transmitter("P225043JMV"))
        assertFalse(SibionicsIdentityParser.isSibionics2Transmitter("LT2309GEPD"))
    }
}
