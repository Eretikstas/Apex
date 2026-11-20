package app.aaps.plugins.sibionics

import tk.glucodata.drivers.sibionics.SibionicsConstants
import tk.glucodata.drivers.sibionics.SibionicsProbeSensitivity
import tk.glucodata.drivers.sibionics.SibionicsSensitivity
import java.util.Locale

/**
 * One Sibionics 2 sensor as AAPS knows it: what was read from the box (QR code or typed serial) and
 * the factory calibration found in it.
 *
 * @property sensorId stable id of the sensor, used for its files
 * @property displayName what the user sees
 * @property shortCode 8 characters the vendor algorithm is configured with
 * @property probeCode the QR serial that carries the factory sensitivity, or empty
 * @property fromQr true when the identity came from the QR code on the box
 */
data class SibionicsIdentity(
    val sensorId: String,
    val displayName: String,
    val shortCode: String,
    val probeCode: String,
    val fromQr: Boolean
) {

    /** Factory sensitivity used by the vendor algorithm. */
    val sensitivity: Float
        get() = SibionicsSensitivity.sensitivityFor(shortCode, VARIANT, probeCode.ifEmpty { null })

    /** True when the sensitivity was really read from the sensor code and is not the type's default. */
    val sensitivityFromCode: Boolean
        get() = SibionicsProbeSensitivity.tryDecode(probeCode.ifEmpty { null }) != null || SibionicsSensitivity.tryDecode(shortCode) != null

    companion object {

        /** Only Sibionics 2 (V120 protocol, V116A algorithm) is supported here. */
        val VARIANT = SibionicsConstants.Variant.SIBIONICS2
    }
}

/**
 * Reads a Sibionics 2 identity from the QR code on the sensor box or from a typed serial.
 *
 * The parsing below is taken from JugglucoNG (https://github.com/ctqvva/JugglucoNG, commit 75ff85a,
 * `SibionicsRegistry.buildIdentity` and its helpers), GPL-3.0, kept to the Sibionics 2 path. The same
 * test vectors as in JugglucoNG are in `SibionicsIdentityParserTest`.
 */
object SibionicsIdentityParser {

    private const val GTIN_PREFIX = "0697283164"
    private val QR_PATTERN = Regex("^\u001D?01(\\d{14})11\\d{6}17\\d{6}10[A-Z0-9]{8,20}\u001D?21([A-Z0-9]{10,20})\u001D?$")

    /**
     * True when [text] is a Sibionics sensor QR code. The serial in it is not checked for the Sibionics 2 form:
     * newer boxes carry a 14 character probe code there (with the factory sensitivity), not a P serial.
     */
    fun isSensorQr(text: String?): Boolean = qrMatch(text) != null

    /** True when [text] looks like a typed Sibionics 2 serial (not a QR code). */
    fun isSibionics2Serial(text: String?): Boolean {
        val normalized = SibionicsConstants.normalizeBleName(text)
        return normalized.length in 10..20 && isV120Serial(normalized)
    }

    /**
     * The identity in [input], a QR payload or a typed serial; null when it is neither a Sibionics 2 QR code
     * nor a Sibionics 2 serial.
     */
    fun parse(input: String?): SibionicsIdentity? {
        if (!isSensorQr(input) && !isSibionics2Serial(input)) return null
        val variant = SibionicsIdentity.VARIANT
        val normalizedRaw = SibionicsConstants.normalizeBleName(input)
        // The identity window is defined on the framed QR payload: keep the group separators here.
        val framedQrName = nativeQrName(input, structuredV120Serial = true)
        val source = normalizedRaw.ifBlank { variant.displayLabel }
        val qrName = framedQrName ?: nativeQrName(source, structuredV120Serial = true)
        val qrShortView = qrName?.takeLast(11)?.takeIf { it.length == 11 }
        val display = qrShortView ?: displayName(source)
        val shortCode = qrShortView?.take(8)?.takeIf { it.length == 8 }
            ?: SibionicsSensitivity.deriveShortCode(bleName(source) ?: source, variant.fallbackShortCode)
        val probeCode = qrMatch(input)?.groupValues?.get(2)?.takeIf { SibionicsProbeSensitivity.tryDecode(it) != null }.orEmpty()
        return SibionicsIdentity(
            sensorId = SibionicsConstants.canonicalSensorId(display.ifBlank { shortCode }),
            displayName = display.ifBlank { "${variant.displayLabel} $shortCode" },
            shortCode = shortCode,
            probeCode = probeCode,
            fromQr = framedQrName != null
        )
    }

    /** True when a device advertising [name] is a Sibionics 2 transmitter. */
    fun isSibionics2Transmitter(name: String?): Boolean = SibionicsConstants.isSibionics2TransmitterName(name)

    private fun isV120Serial(value: String): Boolean = value.startsWith("P") || value.startsWith("XPT")

    private fun qrMatch(source: String?): MatchResult? {
        val match = QR_PATTERN.matchEntire(cleanQr(source)) ?: return null
        val gtin = match.groupValues[1]
        if (!gtin.startsWith(GTIN_PREFIX)) return null
        val body = gtin.dropLast(1)
        val checksum = body.mapIndexed { index, char ->
            val digit = char.digitToInt()
            if ((body.length - index) % 2 == 1) digit * 3 else digit
        }.sum()
        if ((10 - checksum % 10) % 10 != gtin.last().digitToInt()) return null
        if (nativeQrName(source) == null) return null
        return match
    }

    private fun displayName(source: String): String {
        val normalized = SibionicsConstants.normalizeBleName(source)
        if (normalized.isBlank()) return SibionicsIdentity.VARIANT.displayLabel
        if (normalized.length <= 18) return normalized
        if (normalized.indexOf(GTIN_PREFIX) >= 0 && normalized.length >= 17) {
            return normalized.takeLast(17).dropLast(1).takeLast(11).ifBlank { normalized.takeLast(11) }
        }
        return normalized.takeLast(16)
    }

    private fun bleName(source: String?): String? {
        val compact = SibionicsConstants.normalizeBleName(source)
        if (compact.length in 8..16) return compact
        val ai10 = batchAi10(compact)
        if (ai10 < 0) return null
        val start = ai10 + 2
        val nextAi21 = compact.indexOf("21", startIndex = start + 8)
        val end = when {
            nextAi21 > start                -> nextAi21
            compact.length - start in 8..16 -> compact.length
            else                            -> -1
        }
        if (end <= start) return null
        return compact.substring(start, end).takeIf { it.length in 8..16 }
    }

    private fun batchAi10(compact: String): Int {
        val searchStart = compact.indexOf(GTIN_PREFIX).takeIf { it >= 0 }?.let { it + GTIN_PREFIX.length } ?: 0
        var pos = compact.indexOf("10", startIndex = searchStart)
        while (pos >= 0) {
            val valueStart = pos + 2
            val nextAi21 = compact.indexOf("21", startIndex = valueStart + 8)
            if (nextAi21 > valueStart && nextAi21 - valueStart in 8..16) return pos
            if (compact.length - valueStart in 8..16) return pos
            pos = compact.indexOf("10", startIndex = pos + 1)
        }
        return -1
    }

    private fun nativeQrName(source: String?, structuredV120Serial: Boolean = false): String? {
        val payload = cleanQr(source)
        if (!payload.contains(GTIN_PREFIX) || payload.length < 50) return null
        if (structuredV120Serial) {
            // The visible identity leaves out the serial's check character.
            v120CalibrationSerial(payload)?.let { serial -> return "1" + serial.dropLast(1) }
        }
        return if (payload.length < 65) {
            val endLen = payload.length - 49
            val startLen = 16 - endLen
            if (endLen <= 0 || startLen < 0 || payload.length < 49 + endLen || payload.length < 22 + startLen) null
            else (payload.substring(22, 22 + startLen) + payload.substring(49, 49 + endLen)).takeIf { it.length == 16 }
        } else {
            val start = payload.length - 17
            payload.substring(start, start + 16).takeIf { it.length == 16 }
        }?.let { SibionicsConstants.normalizeBleName(it) }
    }

    private fun v120CalibrationSerial(payload: String): String? {
        val magicPos = payload.indexOf(GTIN_PREFIX)
        if (magicPos < 0) return null
        val explicitAi21 = payload.indexOf("\u001D21", startIndex = magicPos + 10)
        val valueStart = if (explicitAi21 >= 0) {
            explicitAi21 + 3
        } else {
            // Some scanners drop the group separators: find AI 21 only after the batch field AI 10.
            val ai10 = batchAi10(payload)
            if (ai10 < 0) return null
            val batchStart = ai10 + 2
            val ai21 = payload.indexOf("21", startIndex = batchStart + 8)
            if (ai21 < 0 || ai21 - batchStart !in 8..16) return null
            ai21 + 2
        }
        // Only the P serial form carries a sensitivity in this window; an XPT serial is a different form.
        if (payload.length - valueStart < 16) return null
        return payload.substring(valueStart, valueStart + 16).takeIf { serial -> serial.startsWith('P') && serial.all(Char::isLetterOrDigit) }
    }

    private fun cleanQr(source: String?): String =
        source.orEmpty()
            .trim()
            .uppercase(Locale.US)
            .removePrefix("]D2")
            .replace("^]", "\u001D")
            .filter { it.isLetterOrDigit() || it == '\u001D' }
}
