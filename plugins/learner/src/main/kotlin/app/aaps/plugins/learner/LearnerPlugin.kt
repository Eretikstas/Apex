package app.aaps.plugins.learner

import app.aaps.core.data.model.data.Block
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.aps.LoopNote
import app.aaps.core.interfaces.aps.LoopNotes
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginBaseWithPreferences
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileRepository
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.keys.interfaces.TextRef
import app.aaps.core.ui.compose.icons.IcStats
import app.aaps.plugins.learner.ui.LearnerComposeContent
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.IntKey
import dev.zacsweers.metro.SingleIn
import dev.zacsweers.metro.binding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/**
 * The learner, first part: the basal card (advisor). Every 4 days at 06:00 it measures the basal need of each hour from
 * clean hours of the last 14 days (`BasalCardEngine`, the rules tested on the body model). When an hour needs a change on two
 * cards in a row it makes a proposal with two buttons: "Внести" edits the active profile in place (same name, no copy)
 * and switches to it at 100 %, "Отказаться" drops it. Nothing is changed without the user's tap; refusals are not
 * remembered, so the same change may be proposed again later.
 */
@ContributesIntoMap(AppScope::class, binding = binding<PluginBase>())
@IntKey(560)
@SingleIn(AppScope::class)
@Inject
class LearnerPlugin(
    override val rh: ResourceHelper,
    aapsLogger: AAPSLogger,
    notificationManager: NotificationManager,
    private val prefs: Preferences,
    private val history: LearnerHistory,
    private val profileFunction: ProfileFunction,
    private val profileRepository: ProfileRepository,
    private val dateUtil: DateUtil,
    private val loopNotes: LoopNotes,
) : PluginBaseWithPreferences(
    PluginDescription()
        .mainType(PluginType.GENERAL)
        .setDefault(true)
        .composeContent { plugin -> LearnerComposeContent(plugin as LearnerPlugin) }
        .icon(IcStats)
        .pluginName(TextRef.AndroidRes(R.string.learner_name))
        .description(TextRef.AndroidRes(R.string.learner_description)),
    LearnerStringNonKey.entries, aapsLogger, rh, prefs, notificationManager
) {

    data class Status(
        val lastCardAt: Long = 0L,
        val nextCardAt: Long = 0L,
        val proposal: BasalProposal? = null,
        val duraProposal: DuraProposal? = null,
        /** The last dura card: flat highs and lows in percent. */
        val lastDura: Pair<Double, Double>? = null,
        val dura: Double = 0.0,
        val hours: List<HourResult> = emptyList(),
        val message: String = "",
        val busy: Boolean = false,
    )

    private val store = LearnerStore(prefs)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var ticker: Job? = null
    private val applying = AtomicBoolean(false)
    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    override suspend fun onStart() {
        super.onStart()
        refresh()
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                runCatching { maybeRunCard() }.onFailure { aapsLogger.error(LTag.APS, "Learner: card failed", it) }
                delay(TICK_MS)
            }
        }
    }

    override suspend fun onStop() {
        ticker?.cancel()
        ticker = null
        super.onStop()
    }

    /** The card runs at 06:00 local time, 4 days after the last one (the first one at the first 06:00). */
    private suspend fun maybeRunCard() {
        val now = dateUtil.now()
        val hour = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).hour
        if (hour != CARD_HOUR) return
        val last = store.lastCardAt
        if (last > 0 && now - last < CARD_EVERY_MS - 2 * 3_600_000L) return
        runCard(now)
    }

    private suspend fun runCard(now: Long) {
        val profile = profileFunction.getProfile() ?: return
        val name = profileFunction.getOriginalProfileName()
        val single = profileRepository.profiles.value.firstOrNull { it.name == name } ?: return
        val profileBasal = hourly(single.basal)
        // The running profile has the switch percentage in it (ISF and IC divided by it); the card works on the
        // profile at 100 %, the same as the basal it compares with.
        val pct = (profileFunction.getRequestedProfile()?.percentage ?: 100).coerceAtLeast(1) / 100.0
        val engine = BasalCardEngine(
            isfMgdl = profile.getIsfMgdl("Learner") * pct,
            icG = profile.getIc() * pct,
            peakMin = profile.iCfg.insulinPeakTime / 60_000.0,
            diaH = profile.iCfg.insulinEndTime / 3_600_000.0,
        )
        val steps = history.steps(now, BasalCardEngine.NEED_DAYS)
        val card = engine.card(steps, profileBasal, store.cardState)
        store.cardState = card.state
        store.lastCardAt = now
        store.lastHours = card.hours
        aapsLogger.debug(LTag.APS, "Learner: basal card, ${card.changes.size} changes: ${card.changes}")
        // The dura card looks at the last 7 days of the same history.
        val dura = prefs.get(DoubleKey.ApsAutoIsfDuraWeight)
        val duraCard = DuraCardEngine.card(steps.takeLast(DuraCardEngine.DAYS * STEPS_PER_DAY), dura)
        store.lastDura = Pair(duraCard.flatHighPct, duraCard.lowPct)
        aapsLogger.debug(LTag.APS, "Learner: dura card $duraCard")
        // One change at a time: the basal first; nothing new while the last applied change is still judged.
        val judging = store.lastAppliedAt > 0 && now - store.lastAppliedAt < CARD_EVERY_MS - 2 * 3_600_000L
        when {
            judging                     -> note(rh.gs(R.string.learner_why_judging), now, NOTE_SHORT_MS)
            card.changes.isNotEmpty()   -> {
                store.proposal = BasalProposal(name, now, card.changes)
                store.duraProposal = null
                notificationManager.post(NotificationId.AUTOMATION_MESSAGE, rh.gs(R.string.learner_notice_proposal), level = NotificationLevel.INFO)
            }

            duraCard.to != null         -> {
                // One proposal at a time.
                store.proposal = null
                store.duraProposal = DuraProposal(dura, duraCard.to, now, duraCard.flatHighPct, duraCard.lowPct)
                notificationManager.post(NotificationId.AUTOMATION_MESSAGE, rh.gs(R.string.learner_notice_dura), level = NotificationLevel.INFO)
            }

            else                        -> note(rh.gs(R.string.learner_why_no_change), now, NOTE_SHORT_MS)
        }
        refresh()
    }

    /**
     * "Внести": the profile of the proposal gets the proposed hours (same profile, edited in place) and is switched to
     * with the percentage and time shift that run now (a 85 % set by the family stays 85 %). While a temporary switch
     * runs nothing is changed: when it ends AAPS goes back to the switch under it, which holds the old basal.
     */
    fun accept() {
        // A second tap while the first runs does nothing.
        if (!applying.compareAndSet(false, true)) return
        setBusy(true)
        scope.launch {
            try {
                applyProposal()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                aapsLogger.error(LTag.APS, "Learner: applying the proposal failed", e)
                finish(rh.gs(R.string.learner_profile_invalid, e.message.orEmpty()))
            } finally {
                applying.set(false)
            }
        }
    }

    private suspend fun applyProposal() {
        val proposal = store.proposal ?: return finish("")
        val running = profileFunction.getRequestedProfile()
        if (running != null && running.duration > 0L) return finish(rh.gs(R.string.learner_wait_temporary))
        val index = profileRepository.profiles.value.indexOfFirst { it.name == proposal.profileName }
        val single = profileRepository.profiles.value.getOrNull(index) ?: return finish(rh.gs(R.string.learner_profile_gone))
        val hours = hourly(single.basal)
        // The profile was edited since the card: the proposal is no longer about the same numbers.
        if (proposal.changes.any { abs(hours[it.hour] - it.from) > 0.005 }) {
            store.proposal = null
            return finish(rh.gs(R.string.learner_profile_changed))
        }
        val edited = single.copy(basal = withHours(single.basal, proposal.changes.associate { it.hour to it.to }))
        val percentage = running?.percentage ?: 100
        val errors = profileRepository.validateStructured(edited) + profileRepository.validatePumpCompatibility(edited, percentage)
        if (errors.isNotEmpty()) return finish(rh.gs(R.string.learner_profile_invalid, errors.joinToString { it.toString() }))
        val saved = profileRepository.replace(index, edited)
        if (saved.isFailure) return finish(rh.gs(R.string.learner_profile_invalid, saved.exceptionOrNull()?.message.orEmpty()))
        val now = dateUtil.now()
        val profileStore = profileRepository.profile.value
        val iCfg = running?.iCfg ?: profileFunction.getProfile()?.iCfg
        val switch = if (profileStore != null && iCfg != null) profileFunction.createProfileSwitch(
            profileStore = profileStore, profileName = proposal.profileName, durationInMinutes = 0, percentage = percentage,
            timeShiftInHours = ((running?.timeshift ?: 0L) / HOUR_MS).toInt(), timestamp = now,
            action = Action.PROFILE_SWITCH, source = Sources.Aaps, note = rh.gs(R.string.learner_switch_note),
            listValues = emptyList(), iCfg = iCfg
        ) else null
        store.proposal = null
        store.lastAppliedAt = now
        note(rh.gs(R.string.learner_why_applied, proposal.changes.size), now, NOTE_LONG_MS)
        aapsLogger.debug(LTag.APS, "Learner: basal proposal applied to ${proposal.profileName} at $percentage %, switch=${switch != null}")
        finish(rh.gs(if (switch != null) R.string.learner_applied else R.string.learner_saved_no_switch))
    }

    /** "Внести" for dura: the AutoISF weight gets the proposed value, if nobody changed it since the card. */
    fun acceptDura() {
        val p = store.duraProposal ?: return
        store.duraProposal = null
        if (abs(prefs.get(DoubleKey.ApsAutoIsfDuraWeight) - p.from) > 0.001) {
            refresh(rh.gs(R.string.learner_dura_changed))
            return
        }
        prefs.put(DoubleKey.ApsAutoIsfDuraWeight, p.to)
        store.lastAppliedAt = dateUtil.now()
        note(rh.gs(R.string.learner_why_dura_applied, p.from, p.to), dateUtil.now(), NOTE_LONG_MS)
        aapsLogger.debug(LTag.APS, "Learner: dura ${p.from} -> ${p.to} applied")
        refresh(rh.gs(R.string.learner_dura_applied))
    }

    fun refuseDura() {
        store.duraProposal = null
        loopNotes.clear(NOTE_SOURCE)
        refresh(rh.gs(R.string.learner_refused))
    }

    /** "Отказаться": the proposal is dropped and not remembered. */
    fun refuse() {
        store.proposal = null
        loopNotes.clear(NOTE_SOURCE)
        refresh(rh.gs(R.string.learner_refused))
    }

    private fun setBusy(busy: Boolean) {
        _status.value = _status.value.copy(busy = busy)
    }

    private fun finish(message: String) {
        refresh(message)
    }

    /** A line for the "why" of the overview (one learner line at a time). */
    private fun note(text: String, now: Long, keepMs: Long) = loopNotes.put(LoopNote(NOTE_SOURCE, text, now, now + keepMs))

    private fun refresh(message: String = "") {
        val last = store.lastCardAt
        // A proposal not answered by the next card is old: the numbers it was made from have moved on.
        val now = dateUtil.now()
        store.proposal?.let { if (now - it.madeAt > PROPOSAL_MAX_AGE_MS) store.proposal = null }
        store.duraProposal?.let { if (now - it.madeAt > PROPOSAL_MAX_AGE_MS) store.duraProposal = null }
        // A waiting proposal is always in the "why", also after a restart of the app.
        store.proposal?.let { note(rh.gs(R.string.learner_why_proposal, it.changes.size), it.madeAt, PROPOSAL_NOTE_MS) }
        store.duraProposal?.let { note(rh.gs(R.string.learner_why_dura_proposal, it.from, it.to), it.madeAt, PROPOSAL_NOTE_MS) }
        _status.value = Status(
            lastCardAt = last,
            nextCardAt = nextCardAt(last),
            proposal = store.proposal,
            duraProposal = store.duraProposal,
            lastDura = store.lastDura,
            dura = prefs.get(DoubleKey.ApsAutoIsfDuraWeight),
            hours = store.lastHours,
            message = message,
            busy = false,
        )
    }

    private fun nextCardAt(last: Long): Long {
        val zone = ZoneId.systemDefault()
        val from = if (last > 0) last + CARD_EVERY_MS - 2 * 3_600_000L else dateUtil.now()
        var t = Instant.ofEpochMilli(from).atZone(zone).withHour(CARD_HOUR).withMinute(0).withSecond(0).withNano(0)
        if (t.toInstant().toEpochMilli() < from) t = t.plusDays(1)
        return t.toInstant().toEpochMilli()
    }

    /** The profile basal per hour (the value at the start of each hour). */
    private fun hourly(blocks: List<Block>): DoubleArray {
        val out = DoubleArray(24)
        for (h in 0 until 24) {
            var t = 0L
            var v = blocks.firstOrNull()?.amount ?: 0.0
            for (b in blocks) {
                if (h * HOUR_MS < t + b.duration) { v = b.amount; break }
                t += b.duration
            }
            out[h] = v
        }
        return out
    }

    private companion object {

        const val CARD_HOUR = 6
        const val CARD_EVERY_MS = 4 * 86_400_000L
        const val HOUR_MS = 3_600_000L
        const val TICK_MS = 10 * 60_000L
        const val STEPS_PER_DAY = 288
        const val NOTE_SOURCE = "learner"
        const val NOTE_SHORT_MS = 12 * 3_600_000L
        const val NOTE_LONG_MS = 24 * 3_600_000L
        const val PROPOSAL_NOTE_MS = 7 * 86_400_000L
        const val PROPOSAL_MAX_AGE_MS = CARD_EVERY_MS
    }
}

private const val LEARNER_HOUR_MS = 3_600_000L

/**
 * [blocks] with the whole hours of [newByHour] set to their new value. Blocks shorter than an hour in the other
 * hours stay as they are (the profile is not flattened to hours); equal neighbours are joined.
 */
internal fun withHours(blocks: List<Block>, newByHour: Map<Int, Double>): List<Block> {
    val pieces = ArrayList<Block>()
    var t = 0L
    for (b in blocks) {
        var from = t
        val end = t + b.duration
        while (from < end) {
            val hourEnd = (from / LEARNER_HOUR_MS + 1) * LEARNER_HOUR_MS
            val to = minOf(end, hourEnd)
            pieces += Block(to - from, newByHour[(from / LEARNER_HOUR_MS).toInt()] ?: b.amount)
            from = to
        }
        t = end
    }
    val out = ArrayList<Block>()
    for (p in pieces) {
        val last = out.lastOrNull()
        if (last != null && abs(last.amount - p.amount) < 1e-9) out[out.size - 1] = Block(last.duration + p.duration, last.amount)
        else out += p
    }
    return out
}
