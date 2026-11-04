package app.aaps.pump.atc3.link

import app.aaps.pump.atc3.protocol.Atc3Frame

/** How a pump is told apart on the air: by the name it advertises, which carries its serial. */
object Atc3PumpNames {

    /** The name a pump with [serial] advertises: the identity block the protocol uses, as text. */
    fun advertisedNameFor(serial: String): String = Atc3Frame.IDENTITY_PREFIX + serial.trim()

    /** Whether a device calling itself [name] is the pump with [serial]. */
    fun matchesSerial(name: String, serial: String): Boolean =
        Atc3Frame.isValidSerial(serial) && name.trim() == advertisedNameFor(serial)
}
