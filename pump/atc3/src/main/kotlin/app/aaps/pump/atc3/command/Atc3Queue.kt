package app.aaps.pump.atc3.command

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.queue.CustomCommand
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Puts the driver's own requests on the AAPS command queue without waiting for them.
 *
 * In AAPS 4.0 [CommandQueue.readStatus] and [CommandQueue.customCommand] wait until the command has
 * run. The driver asks for a status read from inside commands the queue is running now (after a
 * bolus, on a time change) and from its watchers. Waiting there would wait for a command that can only
 * start after the current one ends, so the queue would stop for good. Here the request is only put on
 * the queue, as it was in AAPS 3.4, and the caller goes on.
 */
@Singleton
class Atc3Queue @Inject constructor(
    private val aapsLogger: AAPSLogger,
    private val commandQueue: CommandQueue
) {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * Ask for a status read and do not wait for it.
     *
     * @return false when a status read is already queued or running, so nothing new was asked
     */
    fun readStatus(reason: String): Boolean {
        if (commandQueue.statusInQueue()) return false
        scope.launch {
            try {
                commandQueue.readStatus(reason)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                aapsLogger.error(LTag.PUMPQUEUE, "ATC3: status read '$reason' failed", e)
            }
        }
        return true
    }

    /**
     * Put [command] on the queue and do not wait. [onResult] gets whether it worked and the comment
     * once it has run; a command that failed to run at all is reported as not done, never left unanswered.
     */
    fun customCommand(command: CustomCommand, onResult: ((success: Boolean, comment: String) -> Unit)? = null) {
        scope.launch {
            val answer = try {
                val result = commandQueue.customCommand(command)
                Pair(result.success, result.comment)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                aapsLogger.error(LTag.PUMPQUEUE, "ATC3: command ${command.statusDescription} failed", e)
                Pair(false, e.message.orEmpty())
            }
            onResult?.invoke(answer.first, answer.second)
        }
    }
}
