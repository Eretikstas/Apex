package app.aaps.pump.atc3.command

import app.aaps.core.interfaces.queue.CustomCommand

/**
 * The first connection to a pump being paired: a status read through the queue, since the pump
 * accepts a link before it has looked at the password. See `Atc3PairingScreen`.
 */
class Atc3Pair : CustomCommand {

    override val statusDescription: String = "Apex PAIR"
}
