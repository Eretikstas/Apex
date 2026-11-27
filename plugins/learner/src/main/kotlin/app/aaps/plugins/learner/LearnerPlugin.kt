package app.aaps.plugins.learner

import app.aaps.core.data.model.data.Block
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.aps.LoopNote
import app.aaps.core.data.model.TE
import app.aaps.core.objects.runningMode.RunningModeGuard
import app.aaps.core.objects.runningMode.PumpCommandGate
import app.aaps.core.interfaces.pump.DetailedBolusInfo
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.aps.LoopNotes
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.PluginConstraints
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationId
import app.aaps.core.interfaces.notifications.NotificationLevel
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.PluginBase
import app.aaps.core.interfaces.plugin.PluginBaseWithPreferences
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.Profile
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
    private val commandQueue: CommandQueue,
    private val runningModeGuard: RunningModeGuard,
    private val iobCobCalculator: IobCobCalculator,
) : PluginBaseWithPreferences(
    PluginDescription()
        .mainType(PluginType.GENERAL)
        .setDefault(true)
        .composeContent { plugin -> LearnerComposeContent(plugin as LearnerPlugin) }
        .icon(IcStats)
        .pluginName(TextRef.AndroidRes(R.string.learner_name))
        .description(TextRef.AndroidRes(R.string.learner_description)),
    LearnerStringNonKey.entries, aapsLogger, rh, prefs, notificationManager
), PluginConstraints {

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
        val pctProposal: PctProposal? = null,
        /** The rebound guard switch (off by default) and its state at the last loop run. */
        val reboundOn: Boolean = false,
        val guard: GuardState = GuardState(false),
        /** The last percentage card: highs, lows (percent), hint to try more than 100 %. */
        val lastPct: Triple<Double, Double, Boolean>? = null,
        /** The sensor hint switch (off by default) and the last hint given. */
        val sensorHints: Boolean = false,
        val sensorHint: SensorHint? = null,
        /** The cannula hint switch (off by default) and the hint shown now. */
        val cannulaHints: Boolean = false,
        val cannulaHint: CannulaHint? = null,
        /** The low meal hint switch (off by default). */
        val lowMealHints: Boolean = false,
        /** The change evaluation switch (off by default), the change waiting for its verdict, and the last verdict. */
        val evalOn: Boolean = false,
        val evalPending: PendingEval? = null,
        val lastEval: DoneEval? = null,
        /** The weekly digest switch (off by default) and the last digest with its time. */
        val digestOn: Boolean = false,
        val lastDigest: Pair<Long, WeeklyDigest>? = null,
        /** The CR block card: a proposal (weaker CR of one block) and the last shares of meals ending low per block. */
        val crProposal: CrProposal? = null,
        val lastCr: Map<String, Double>? = null,
        /** The special day: until when it runs (0 = not now). */
        val specialUntil: Long = 0L,
        /** What the learner learned about hypo treatments. */
        val hypoSize: HypoSize? = null,
        /** Insulin curve watch: switch, the curve now, the last change (when, the curve before). */
        val curveWatch: Boolean = false,
        val curve: CurveSnap? = null,
        val curveChange: Pair<Long, CurveSnap>? = null,
        /** "Why was it low": switch and the lows of the last 7 days with their likely cause. */
        val lowCausesOn: Boolean = false,
        val lows: List<LowEpisode> = emptyList(),
        /** Night peak check: switch and the last fit. */
        val peakFitOn: Boolean = false,
        val peakFit: PeakFit? = null,
        /** Low predictor: switch, learned margin (mg/dL), warnings checked (true, false). */
        val lowPredOn: Boolean = false,
        val lowPredMargin: Double = 0.0,
        val lowPredStats: Pair<Int, Int> = Pair(0, 0),
        /** Split meal bolus: switch (asked before on), a calculated preview, the meal waiting for its rest, a message. */
        val splitOn: Boolean = false,
        val splitPreview: SplitPlan? = null,
        val splitPlan: SplitPlan? = null,
        val splitMessage: String = "",
    )

    private val store = LearnerStore(prefs)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var ticker: Job? = null
    private val applying = AtomicBoolean(false)
    private var predJob: Job? = null
    @Volatile private var lastRollbackCheck = 0L
    @Volatile private var guard = GuardState(false)
    private var splitJob: Job? = null
    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    override suspend fun onStart() {
        super.onStart()
        refresh()
        if (store.lowPredOn) watchLows()
        if (store.splitPlan != null) watchSplit()
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                runCatching { maybeRunCard() }.onFailure { aapsLogger.error(LTag.APS, "Learner: card failed", it) }
                runCatching { maybeRunPctCard() }.onFailure { aapsLogger.error(LTag.APS, "Learner: percentage card failed", it) }
                runCatching { checkSensor() }.onFailure { aapsLogger.error(LTag.APS, "Learner: sensor hint failed", it) }
                runCatching { checkCannula() }.onFailure { aapsLogger.error(LTag.APS, "Learner: cannula hint failed", it) }
                runCatching { checkLowMeal() }.onFailure { aapsLogger.error(LTag.APS, "Learner: low meal hint failed", it) }
                runCatching { checkCurve() }.onFailure { aapsLogger.error(LTag.APS, "Learner: curve watch failed", it) }
                runCatching { maybeCountLows() }.onFailure { aapsLogger.error(LTag.APS, "Learner: low causes failed", it) }
                runCatching { checkEval() }.onFailure { aapsLogger.error(LTag.APS, "Learner: change evaluation failed", it) }
                runCatching { maybeDigest() }.onFailure { aapsLogger.error(LTag.APS, "Learner: weekly digest failed", it) }
                delay(TICK_MS)
            }
        }
    }

    override suspend fun onStop() {
        ticker?.cancel()
        ticker = null
        super.onStop()
    }

    /** The history without the readings of special days (see `LearnerMemory`). */
    private suspend fun cleanSteps(now: Long, days: Int): List<LearnerStep> {
        // With the sensor hint on, the first 2 h after a logged sensor change are left out too (readings may be false).
        val sensor = if (store.sensorHints) history.sensorChanges(now, days + 1).map { Pair(it, it + SensorHintEngine.FIRST_HOURS_MS) } else emptyList()
        return LearnerMemory.withoutSpecial(history.steps(now, days), store.specialDays + sensor)
    }

    private fun failed(kind: String) = LearnerMemory.failedRecently(dateUtil.now(), kind, store.failedKinds)

    /** The card runs at 06:00 local time, 4 days after the last one (the first one at the first 06:00). */
    private suspend fun maybeRunCard() {
        val now = dateUtil.now()
        if (LearnerMemory.specialNow(now, store.specialDays)) return
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
        val steps = cleanSteps(now, BasalCardEngine.NEED_DAYS)
        store.hypoSize = HypoSizeEngine.learn(steps)
        if (store.peakFitOn) runCatching {
            val i = profile.iCfg
            store.lastPeakFit = PeakFitEngine.fit(steps, i.peak, i.dia)
            store.lastPeakFit?.takeIf { PeakFitEngine.proposes(it) }?.let { f ->
                note(rh.gs(R.string.learner_why_peak, f.bestPeakMin, f.currentPeakMin), now, NOTE_LONG_MS, PEAK_NOTE_SOURCE)
            }
        }.onFailure { aapsLogger.error(LTag.APS, "Learner: peak fit failed", it) }
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
        // The CR block card looks at the same 14 days (it only proposes a WEAKER CR).
        val crCard = CrBlockCardEngine.card(steps.takeLast(CrBlockCardEngine.DAYS * STEPS_PER_DAY))
        store.lastCr = crCard.lowPct.mapKeys { it.key.name }
        aapsLogger.debug(LTag.APS, "Learner: CR block card $crCard")
        // One change at a time: the basal first; nothing new while the last applied change is still judged.
        val judging = store.lastAppliedAt > 0 && now - store.lastAppliedAt < CARD_EVERY_MS - 2 * 3_600_000L
        when {
            judging                     -> note(rh.gs(R.string.learner_why_judging), now, NOTE_SHORT_MS)
            card.changes.isNotEmpty() && !failed(KIND_BASAL) -> {
                store.proposal = BasalProposal(name, now, card.changes)
                store.duraProposal = null
                notificationManager.post(NotificationId.AUTOMATION_MESSAGE, rh.gs(R.string.learner_notice_proposal), level = NotificationLevel.INFO)
            }

            duraCard.to != null && !failed(duraKind(dura, duraCard.to)) -> {
                // One proposal at a time.
                store.proposal = null
                store.duraProposal = DuraProposal(dura, duraCard.to, now, duraCard.flatHighPct, duraCard.lowPct)
                notificationManager.post(NotificationId.AUTOMATION_MESSAGE, rh.gs(R.string.learner_notice_dura), level = NotificationLevel.INFO)
            }

            crCard.weaker != null && !failed(KIND_CR + crCard.weaker.name) -> {
                val block = crCard.weaker
                val icHours = hourly(single.ic)
                store.proposal = null
                store.duraProposal = null
                store.crProposal = CrProposal(
                    name, block.name, now, crCard.lowPct[block] ?: 0.0,
                    (block.fromHour until block.toHour).associateWith { icHours[it] }, CrBlockCardEngine.weakerHours(icHours, block)
                )
                notificationManager.post(NotificationId.AUTOMATION_MESSAGE, rh.gs(R.string.learner_notice_cr), level = NotificationLevel.INFO)
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
        startEval(rh.gs(R.string.learner_eval_label_basal, proposal.changes.size), now, KIND_BASAL)
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
        scope.launch { runCatching { startEval(rh.gs(R.string.learner_eval_label_dura, p.from, p.to), dateUtil.now(), duraKind(p.from, p.to)) } }
        note(rh.gs(R.string.learner_why_dura_applied, p.from, p.to), dateUtil.now(), NOTE_LONG_MS)
        aapsLogger.debug(LTag.APS, "Learner: dura ${p.from} -> ${p.to} applied")
        refresh(rh.gs(R.string.learner_dura_applied))
    }

    /**
     * The percentage card (a safety card, lab PCTCARD + PCTMAX_100): once a day from 06:00, over the last 3 days. Not
     * while a temporary profile runs (tried again on the next tick), not while another proposal waits, not within 2 days
     * of its own last change.
     */
    private suspend fun maybeRunPctCard(force: Boolean = false) {
        val now = dateUtil.now()
        if (LearnerMemory.specialNow(now, store.specialDays)) return
        val local = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
        if (!force && local.hour < CARD_HOUR) return
        val today = local.toLocalDate().toEpochDay()
        if (!force && store.lastPctDay == today) return
        val running = profileFunction.getRequestedProfile()
        if (running != null && running.duration > 0L) return
        store.lastPctDay = today
        if (store.proposal != null || store.duraProposal != null || store.pctProposal != null || store.crProposal != null) return
        if (now - store.lastPctAppliedAt < PCT_GAP_MS) return
        val nowPct = running?.percentage ?: 100
        val card = PctCardEngine.card(cleanSteps(now, PctCardEngine.DAYS), nowPct)
        store.lastPct = Triple(card.highPct, card.lowPct, card.hintUp)
        aapsLogger.debug(LTag.APS, "Learner: percentage card $card")
        when {
            card.to != null && !failed(pctKind(nowPct, card.to)) -> {
                store.pctProposal = PctProposal(nowPct, card.to, now, card.highPct, card.lowPct)
                notificationManager.post(NotificationId.AUTOMATION_MESSAGE, rh.gs(R.string.learner_notice_pct), level = NotificationLevel.INFO)
            }
            card.hintUp     -> note(rh.gs(R.string.learner_why_pct_hint, card.highPct), now, NOTE_LONG_MS)
        }
        refresh()
    }

    /**
     * The sensor hint (advisor, off by default): after a sensor change logged in AAPS, a note in the overview "why" for
     * the first 2 hours and for a jump of 1.5 mmol/L or more in 5 minutes during the first day (`SensorHintEngine`).
     * Only text: no value is hidden, no alarm is stopped, the loop is not touched.
     */
    private suspend fun checkSensor() {
        if (!store.sensorHints) return
        val now = dateUtil.now()
        val (changeAt, readings) = history.sensorWatch(now)
        val hint = SensorHintEngine.check(now, changeAt, readings) ?: return
        // Put again on every tick with fixed times: the same note, also after a restart of the app.
        val text = when (hint.kind) {
            SensorHintKind.FIRST_HOURS -> rh.gs(R.string.learner_why_sensor_first_hours)
            SensorHintKind.JUMP        -> rh.gs(R.string.learner_why_sensor_jump, (hint.fromMgdl ?: 0.0) / MGDL_PER_MMOL, (hint.toMgdl ?: 0.0) / MGDL_PER_MMOL)
        }
        val until = if (hint.kind == SensorHintKind.FIRST_HOURS) hint.changeAt + SensorHintEngine.FIRST_HOURS_MS else hint.at + SensorHintEngine.JUMP_SHOW_MS
        if (until <= now) return
        loopNotes.put(LoopNote(SENSOR_NOTE_SOURCE, text, if (hint.kind == SensorHintKind.JUMP) hint.at else hint.changeAt, until))
        if (_status.value.sensorHint != hint) aapsLogger.debug(LTag.APS, "Learner: sensor hint $hint")
        _status.value = _status.value.copy(sensorHint = hint)
    }

    /**
     * The cannula hint (advisor, off by default): a cannula of 60 hours or more and 40 % or more over 10 mmol/L in the
     * last 6 hours -> a note in the overview "why" (`CannulaHintEngine`). Only text, nothing is changed.
     */
    private suspend fun checkCannula() {
        if (!store.cannulaHints) return
        val now = dateUtil.now()
        val (changeAt, readings) = history.cannulaWatch(now)
        val hint = CannulaHintEngine.check(now, changeAt, readings)
        if (hint == null) {
            loopNotes.clear(CANNULA_NOTE_SOURCE)
        } else {
            // Valid a bit longer than one tick: the next tick puts it again or removes it.
            loopNotes.put(LoopNote(CANNULA_NOTE_SOURCE, rh.gs(R.string.learner_why_cannula, hint.ageH, hint.highPct), now, now + 2 * TICK_MS))
            if (_status.value.cannulaHint == null) aapsLogger.debug(LTag.APS, "Learner: cannula hint $hint")
        }
        _status.value = _status.value.copy(cannulaHint = hint)
    }

    /** A change was applied with "Внести": keep the numbers of the 3 days before it for the verdict 3 days later. */
    private suspend fun startEval(label: String, now: Long, kind: String) {
        val before = ChangeEvalEngine.stats(cleanSteps(now, ChangeEvalEngine.DAYS))
        store.evalPending = PendingEval(label, now, before, kind = kind)
        aapsLogger.debug(LTag.APS, "Learner: evaluation started for $label, before $before")
        refresh()
    }

    /**
     * The change evaluation (advisor, `ChangeEvalEngine`): 3 days after a change applied with "Внести" a verdict in the
     * overview "why" and on the learner screen. Before that, once an hour, the rollback rule (under 3.0 over 1 % two days
     * in a row, or under 2.5) gives an early note "check whether to undo it". Nothing is undone by the learner.
     */
    private suspend fun checkEval() {
        val pending = store.evalPending ?: return
        val now = dateUtil.now()
        val due = now - pending.at >= ChangeEvalEngine.DAYS * ChangeEvalEngine.DAY_MS
        if (!due && (pending.warned || now - lastRollbackCheck < HOUR_MS)) return
        lastRollbackCheck = now
        val days = ((now - pending.at) / ChangeEvalEngine.DAY_MS + 1).toInt().coerceIn(1, ChangeEvalEngine.DAYS)
        val after = cleanSteps(now, days)
        if (!due) {
            if (ChangeEvalEngine.rollback(pending.at, after, now)) {
                store.evalPending = pending.copy(warned = true)
                rememberFailed(pending.kind, now)
                if (store.evalOn) note(rh.gs(R.string.learner_why_eval_rollback, pending.label), now, NOTE_LONG_MS, EVAL_NOTE_SOURCE)
                aapsLogger.debug(LTag.APS, "Learner: rollback rule hit early for ${pending.label}")
                refresh()
            }
            return
        }
        val eval = ChangeEvalEngine.evaluate(pending.before, after, pending.at, now)
        store.lastEval = DoneEval(pending.label, pending.at, now, eval)
        if (eval.rollback || eval.verdict == EvalVerdict.WORSE) rememberFailed(pending.kind, now)
        store.evalPending = null
        aapsLogger.debug(LTag.APS, "Learner: evaluation of ${pending.label}: $eval")
        if (store.evalOn) note(verdictText(pending.label, eval), now, NOTE_LONG_MS, EVAL_NOTE_SOURCE)
        refresh()
    }

    private fun verdictText(label: String, e: ChangeEval) = rh.gs(
        when {
            e.rollback                                -> R.string.learner_why_eval_rollback
            e.verdict == EvalVerdict.BETTER           -> R.string.learner_why_eval_better
            e.verdict == EvalVerdict.WORSE            -> R.string.learner_why_eval_worse
            e.verdict == EvalVerdict.SAME             -> R.string.learner_why_eval_same
            else                                      -> R.string.learner_why_eval_no_data
        }, label
    )

    /** The weekly digest (advisor, `WeeklyDigestEngine`): from 06:00, 7 days after the last one; a note and the numbers. */
    private suspend fun maybeDigest(force: Boolean = false) {
        if (!store.digestOn) return
        val now = dateUtil.now()
        val last = store.lastDigest?.first ?: 0L
        if (!force) {
            if (Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).hour < CARD_HOUR) return
            if (now - last < DIGEST_EVERY_MS - 2 * HOUR_MS) return
        }
        val digest = WeeklyDigestEngine.digest(cleanSteps(now, WeeklyDigestEngine.DAYS))
        store.lastDigest = now to digest
        val w = digest.thisWeek
        note(rh.gs(R.string.learner_why_digest, w.inRangePct, w.low39Pct, w.high10Pct, w.cvPct, digest.goalsMet, digest.goals), now, NOTE_LONG_MS, DIGEST_NOTE_SOURCE)
        aapsLogger.debug(LTag.APS, "Learner: weekly digest $digest")
        refresh()
    }

    /** The user's switch for the weekly digest. On makes a digest now; off removes its note. */
    fun setDigestOn(on: Boolean) {
        store.digestOn = on
        if (!on) loopNotes.clear(DIGEST_NOTE_SOURCE)
        refresh()
        if (on) scope.launch { runCatching { maybeDigest(force = true) }.onFailure { aapsLogger.error(LTag.APS, "Learner: weekly digest failed", it) } }
    }

    /** "What did not work": this kind of change is not proposed again for 14 days. */
    private fun rememberFailed(kind: String, now: Long) {
        if (kind.isEmpty()) return
        store.failedKinds = store.failedKinds.filterValues { now - it < LearnerMemory.FAILED_MS } + (kind to now)
        aapsLogger.debug(LTag.APS, "Learner: $kind judged worse, not proposed again for 14 days")
    }

    private fun duraKind(from: Double, to: Double) = KIND_DURA + if (to > from) "up" else "down"
    private fun pctKind(from: Int, to: Int) = KIND_PCT + if (to < from) "down" else "up"

    /** The special day button: on = 24 h without learning (and its readings left out later); off = it ends now. */
    fun setSpecialDay(on: Boolean) {
        val now = dateUtil.now()
        val kept = store.specialDays.filter { now - it.second < 30 * 86_400_000L }
        store.specialDays = if (on) kept + Pair(now, now + LearnerMemory.SPECIAL_MS)
        else kept.map { if (now >= it.first && now < it.second) Pair(it.first, now) else it }
        if (on) note(rh.gs(R.string.learner_why_special), now, LearnerMemory.SPECIAL_MS, SPECIAL_NOTE_SOURCE) else loopNotes.clear(SPECIAL_NOTE_SOURCE)
        refresh()
    }

    /**
     * The insulin curve watch (advisor, off by default, `CurveWatchEngine`): when the curve of the running profile
     * (name, peak, DIA) is not the one seen last time, a note and a notice. Only text.
     */
    private suspend fun checkCurve() {
        val i = profileFunction.getProfile()?.iCfg ?: return
        val now = CurveSnap(i.insulinLabel, i.peak, i.dia)
        val before = store.lastCurve
        if (before == now) return
        store.lastCurve = now
        if (store.curveWatch && CurveWatchEngine.changed(before, now) && before != null) {
            val t = dateUtil.now()
            store.curveChange = t to before
            val text = rh.gs(R.string.learner_why_curve, before.label, before.peakMin, before.diaH, now.label, now.peakMin, now.diaH)
            note(text, t, NOTE_LONG_MS, CURVE_NOTE_SOURCE)
            notificationManager.post(NotificationId.AUTOMATION_MESSAGE, text, level = NotificationLevel.URGENT)
            aapsLogger.debug(LTag.APS, "Learner: insulin curve changed $before -> $now")
        }
        refresh()
    }

    fun setCurveWatch(on: Boolean) {
        store.curveWatch = on
        if (!on) loopNotes.clear(CURVE_NOTE_SOURCE)
        refresh()
    }

    /** "Why was it low" (advisor, off by default, `LowCauseEngine`): counted at most every 6 hours, last 7 days. */
    private suspend fun maybeCountLows(force: Boolean = false) {
        if (!store.lowCausesOn) return
        val now = dateUtil.now()
        if (!force && now - (store.lastLows?.first ?: 0L) < 6 * HOUR_MS) return
        store.lastLows = now to LowCauseEngine.episodes(cleanSteps(now, 7))
        refresh()
    }

    fun setLowCauses(on: Boolean) {
        store.lowCausesOn = on
        refresh()
        if (on) scope.launch { runCatching { maybeCountLows(force = true) }.onFailure { aapsLogger.error(LTag.APS, "Learner: low causes failed", it) } }
    }

    fun setPeakFitOn(on: Boolean) {
        store.peakFitOn = on
        if (!on) loopNotes.clear(PEAK_NOTE_SOURCE)
        refresh()
    }

    /** The low predictor (advisor, off by default, `LowPredictEngine`): checked every 5 minutes while on. */
    fun setLowPredOn(on: Boolean) {
        store.lowPredOn = on
        if (on) watchLows() else predJob?.cancel()
        refresh()
    }

    private fun watchLows() {
        predJob?.cancel()
        predJob = scope.launch {
            while (isActive && store.lowPredOn) {
                runCatching { checkLowPred() }.onFailure { aapsLogger.error(LTag.APS, "Learner: low predictor failed", it) }
                delay(PRED_TICK_MS)
            }
        }
    }

    private suspend fun checkLowPred() {
        val now = dateUtil.now()
        // Check old warnings: did a low come within 40 min?
        var warnings = store.lowWarnings
        if (warnings.any { it.trueLow == null && now - it.at >= LowPredictEngine.CHECK_MS }) {
            warnings = warnings.map { w ->
                if (w.trueLow == null && now - w.at >= LowPredictEngine.CHECK_MS)
                    w.copy(trueLow = history.lowBetween(w.at, w.at + LowPredictEngine.CHECK_MS, LowPredictEngine.LOW_MGDL))
                else w
            }
            store.lowWarnings = warnings
            store.lowPredMargin = LowPredictEngine.learn(store.lowPredMargin, warnings)
        }
        val (bg, delta15) = history.smoothedNow(now)
        val last = warnings.maxOfOrNull { it.at } ?: 0L
        if (LowPredictEngine.warn(now, bg, delta15?.let { it / 3.0 }, store.lowPredMargin, last)) {
            store.lowWarnings = warnings + LowWarning(now)
            val grams = store.hypoSize?.let { HypoSizeEngine.suggest(it, LowPredictEngine.LOW_MGDL) }
            val text = if (grams != null) rh.gs(R.string.learner_pred_warn_g, grams) else rh.gs(R.string.learner_pred_warn)
            notificationManager.post(NotificationId.AUTOMATION_MESSAGE, text, level = NotificationLevel.IMPORTANT)
            note(text, now, LowPredictEngine.GAP_MS, PRED_NOTE_SOURCE)
            aapsLogger.debug(LTag.APS, "Learner: low forecast, bg=$bg delta15=$delta15 margin=${store.lowPredMargin}")
        }
        refresh()
    }

    /** The user's switch for the change evaluation. Off also removes its note. */
    fun setEvalOn(on: Boolean) {
        store.evalOn = on
        if (!on) loopNotes.clear(EVAL_NOTE_SOURCE)
        refresh()
    }

    /** The low meal hint (advisor, off by default, `LowMealHintEngine`): a note "fast carbs first". Only text. */
    private suspend fun checkLowMeal() {
        if (!store.lowMealHints) return
        val now = dateUtil.now()
        val at = LowMealHintEngine.check(now, history.recentCarbs(now)) ?: return
        loopNotes.put(LoopNote(LOW_MEAL_NOTE_SOURCE, rh.gs(R.string.learner_why_low_meal), at, at + LowMealHintEngine.SHOW_MS))
    }

    /** The user's switch for the low meal hint. Off also removes its note. */
    fun setLowMealHints(on: Boolean) {
        store.lowMealHints = on
        if (!on) loopNotes.clear(LOW_MEAL_NOTE_SOURCE)
        refresh()
        if (on) scope.launch { runCatching { checkLowMeal() }.onFailure { aapsLogger.error(LTag.APS, "Learner: low meal hint failed", it) } }
    }

    /** The user's switch for the cannula hint. Off also removes a hint that is shown now. */
    fun setCannulaHints(on: Boolean) {
        store.cannulaHints = on
        if (!on) loopNotes.clear(CANNULA_NOTE_SOURCE)
        _status.value = _status.value.copy(cannulaHints = on, cannulaHint = if (on) _status.value.cannulaHint else null)
        if (on) scope.launch { runCatching { checkCannula() }.onFailure { aapsLogger.error(LTag.APS, "Learner: cannula hint failed", it) } }
    }

    /** The user's switch for the sensor hint. Off also removes a hint that is shown now. */
    fun setSensorHints(on: Boolean) {
        store.sensorHints = on
        if (!on) loopNotes.clear(SENSOR_NOTE_SOURCE)
        _status.value = _status.value.copy(sensorHints = on, sensorHint = if (on) _status.value.sensorHint else null)
        if (on) scope.launch { runCatching { checkSensor() }.onFailure { aapsLogger.error(LTag.APS, "Learner: sensor hint failed", it) } }
    }

    /**
     * For the virtual bench (the app's device tests): the basal and dura card, or the percentage card, now - without
     * their clock gates (06:00, every 4 days, once a day). Not used by the app itself.
     */
    suspend fun runBasalCardNowForBench() = runCard(dateUtil.now())

    suspend fun runPctCardNowForBench() = maybeRunPctCard(force = true)

    /** "Внести" for the percentage: a permanent switch of the running profile to the new percentage, time shift kept. */
    fun acceptPct() {
        if (!applying.compareAndSet(false, true)) return
        setBusy(true)
        scope.launch {
            try {
                val p = store.pctProposal ?: return@launch finish("")
                val running = profileFunction.getRequestedProfile()
                if (running != null && running.duration > 0L) return@launch finish(rh.gs(R.string.learner_wait_temporary))
                if ((running?.percentage ?: 100) != p.from) {
                    store.pctProposal = null
                    return@launch finish(rh.gs(R.string.learner_pct_changed))
                }
                val profileStore = profileRepository.profile.value
                val iCfg = running?.iCfg ?: profileFunction.getProfile()?.iCfg
                val name = running?.profileName ?: profileFunction.getOriginalProfileName()
                val now = dateUtil.now()
                val switch = if (profileStore != null && iCfg != null) profileFunction.createProfileSwitch(
                    profileStore = profileStore, profileName = name, durationInMinutes = 0, percentage = p.to,
                    timeShiftInHours = ((running?.timeshift ?: 0L) / HOUR_MS).toInt(), timestamp = now,
                    action = Action.PROFILE_SWITCH, source = Sources.Aaps, note = rh.gs(R.string.learner_switch_note_pct),
                    listValues = emptyList(), iCfg = iCfg
                ) else null
                store.pctProposal = null
                if (switch != null) {
                    store.lastPctAppliedAt = now
                    startEval(rh.gs(R.string.learner_eval_label_pct, p.from, p.to), now, pctKind(p.from, p.to))
                    note(rh.gs(R.string.learner_why_pct_applied, p.from, p.to), now, NOTE_LONG_MS)
                }
                aapsLogger.debug(LTag.APS, "Learner: percentage ${p.from} -> ${p.to}, switch=${switch != null}")
                finish(rh.gs(if (switch != null) R.string.learner_pct_applied else R.string.learner_saved_no_switch))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                aapsLogger.error(LTag.APS, "Learner: applying the percentage failed", e)
                finish(rh.gs(R.string.learner_profile_invalid, e.message.orEmpty()))
            } finally {
                applying.set(false)
            }
        }
    }

    /**
     * The rebound guard (`ReboundGuardEngine`), off by default. The loop asks this before every run: while the guard
     * holds (3 hours after a hypo treatment, no meal, glucose under 13.9, no early release) SMB are off. It only takes away: it never turns
     * SMB on and never adds insulin. A failure to read the data leaves the loop as it is.
     */
    override suspend fun isSMBModeEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        if (!store.reboundOn) {
            guard = GuardState(false)
            return value
        }
        val now = dateUtil.now()
        val state = runCatching {
            val (carbs, bgNow, delta) = history.reboundInputs(now)
            ReboundGuardEngine.state(now, carbs, bgNow, delta)
        }.getOrElse {
            aapsLogger.error(LTag.APS, "Learner: rebound guard could not read the data", it)
            GuardState(false)
        }
        if (state.active != guard.active) aapsLogger.debug(LTag.APS, "Learner: rebound guard $state")
        guard = state
        if (state.active) {
            value.set(false, rh.gs(R.string.learner_guard_smb_reason), this)
            loopNotes.put(LoopNote(GUARD_NOTE_SOURCE, rh.gs(R.string.learner_why_guard, dateUtil.timeString(state.until)), state.startedAt, state.until))
        } else loopNotes.clear(GUARD_NOTE_SOURCE)
        _status.value = _status.value.copy(guard = state)
        return value
    }

    /** While the guard holds, the temp basal is at most the profile basal (no high temps). Only lowers, never raises. */
    override fun applyBasalConstraints(absoluteRate: Constraint<Double>, profile: Profile): Constraint<Double> {
        val g = guard
        if (!store.reboundOn || !g.active || dateUtil.now() >= g.until) return absoluteRate
        val cap = ReboundGuardEngine.basalCap(profile.getBasal())
        absoluteRate.setIfSmaller(cap, rh.gs(R.string.learner_guard_basal_reason, cap), this)
        return absoluteRate
    }

    /** The same cap for pumps that take a percentage. */
    override fun applyBasalPercentConstraints(percentRate: Constraint<Int>, profile: Profile): Constraint<Int> {
        val g = guard
        if (!store.reboundOn || !g.active || dateUtil.now() >= g.until) return percentRate
        val cap = (ReboundGuardEngine.BASAL_CAP * 100).toInt()
        percentRate.setIfSmaller(cap, rh.gs(R.string.learner_guard_percent_reason, cap), this)
        return percentRate
    }

    /** The user's switch for the rebound guard. Off lets go at once. */
    fun setReboundOn(on: Boolean) {
        store.reboundOn = on
        if (!on) {
            guard = GuardState(false)
            loopNotes.clear(GUARD_NOTE_SOURCE)
    /** The user's switch for the split bolus (the screen asks first). Off drops a waiting rest. */
    fun setSplitOn(on: Boolean) {
        store.splitOn = on
        if (!on) {
            store.splitPlan = null
            splitJob?.cancel()
            loopNotes.clear(SPLIT_NOTE_SOURCE)
        }
        refresh()
    }

    /**
     * The split bolus dose for [carbs] g of a [type] meal: carbs / CR of now, minus the bolus insulin still active (never
     * below 0), split by `SplitBolusEngine`. Shown to the family before anything is given.
     */
    fun previewSplit(carbs: Double, type: MealType) {
        scope.launch {
            val p = runCatching { splitPlanFor(carbs, type, dateUtil.now()) }.getOrNull()
            _status.value = _status.value.copy(splitPreview = p, splitMessage = if (p == null) rh.gs(R.string.learner_split_no_profile) else "")
        }
    }

    private suspend fun splitPlanFor(carbs: Double, type: MealType, now: Long): SplitPlan? {
        val profile = profileFunction.getProfile() ?: return null
        val iob = iobCobCalculator.calculateIobFromBolus().iob.coerceAtLeast(0.0)
        val total = (carbs / profile.getIc() - iob).coerceAtLeast(0.0)
        return SplitBolusEngine.plan(now, type, Math.round(total / SPLIT_STEP) * SPLIT_STEP, SPLIT_STEP).copy(carbsG = carbs)
    }

    /** "Give the first part": carbs and the first part as one meal bolus; the rest waits (`checkSplit`). */
    fun startSplit() {
        val preview = _status.value.splitPreview ?: return
        if (!store.splitOn || store.splitPlan != null) return
        if (!applying.compareAndSet(false, true)) return
        setBusy(true)
        scope.launch {
            try {
                runningModeGuard.rejectionMessage(PumpCommandGate.CommandKind.BOLUS)?.let { return@launch finish(it) }
                val now = dateUtil.now()
                // Recalculated now: the preview may be minutes old.
                val plan = splitPlanFor(preview.carbsG, preview.type, now) ?: return@launch finish(rh.gs(R.string.learner_split_no_profile))
                val dbi = DetailedBolusInfo().apply {
                    insulin = plan.firstU
                    carbs = plan.carbsG
                    eventType = TE.Type.MEAL_BOLUS
                    timestamp = now
                    notes = rh.gs(R.string.learner_split_note_first)
                }
                val r = commandQueue.bolus(dbi)
                if (!r.success) return@launch finish(rh.gs(R.string.learner_split_failed, r.comment))
                if (plan.restU >= SplitBolusEngine.MIN_BOLUS_U) {
                    store.splitPlan = plan
                    note(rh.gs(R.string.learner_why_split_wait, plan.restU), now, SplitBolusEngine.SLOW_LIMIT_MS, SPLIT_NOTE_SOURCE)
                    watchSplit()
                }
                aapsLogger.debug(LTag.APS, "Learner: split bolus started $plan")
                _status.value = _status.value.copy(splitPreview = null)
                finish(rh.gs(R.string.learner_split_started, plan.firstU, plan.restU))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                aapsLogger.error(LTag.APS, "Learner: split bolus failed", e)
                finish(rh.gs(R.string.learner_split_failed, e.message.orEmpty()))
            } finally {
                applying.set(false)
            }
        }
    }

    /** "Cancel the rest": nothing more is given for this meal. */
    fun cancelSplit() {
        store.splitPlan = null
        splitJob?.cancel()
        loopNotes.clear(SPLIT_NOTE_SOURCE)
        refresh(rh.gs(R.string.learner_split_cancelled))
    }

    private fun watchSplit() {
        splitJob?.cancel()
        splitJob = scope.launch {
            while (isActive && store.splitPlan != null) {
                runCatching { checkSplit() }.onFailure { aapsLogger.error(LTag.APS, "Learner: split check failed", it) }
                delay(SPLIT_TICK_MS)
            }
        }
    }

    /**
     * Every minute while a rest waits: `SplitBolusEngine.decide` on the smoothed glucose, its 15 minute change and the SMB
     * given since the meal. The plan is removed BEFORE the rest is sent, so a crash can never give it twice.
     */
    private suspend fun checkSplit() {
        val plan = store.splitPlan ?: return
        if (!store.splitOn) return cancelSplit()
        val now = dateUtil.now()
        val (bg, delta15) = history.smoothedNow(now)
        val smb = history.smbSince(plan.madeAt, now)
        when (val d = SplitBolusEngine.decide(now, plan, bg, delta15, null, smb, SPLIT_STEP)) {
            is SplitDecision.Give    -> {
                if (runningModeGuard.rejectionMessage(PumpCommandGate.CommandKind.BOLUS) != null) return
                store.splitPlan = null
                val dbi = DetailedBolusInfo().apply {
                    insulin = d.units
                    eventType = TE.Type.MEAL_BOLUS
                    timestamp = now
                    notes = rh.gs(R.string.learner_split_note_rest)
                }
                val r = commandQueue.bolus(dbi)
                loopNotes.clear(SPLIT_NOTE_SOURCE)
                if (r.success) note(rh.gs(R.string.learner_why_split_given, d.units), now, NOTE_SHORT_MS)
                else notificationManager.post(NotificationId.AUTOMATION_MESSAGE, rh.gs(R.string.learner_split_failed, r.comment), level = NotificationLevel.URGENT)
                aapsLogger.debug(LTag.APS, "Learner: split rest ${d.units} U, success=${r.success}")
                refresh(if (r.success) rh.gs(R.string.learner_split_rest_given, d.units) else rh.gs(R.string.learner_split_failed, r.comment))
            }

            SplitDecision.Covered    -> {
                store.splitPlan = null
                loopNotes.clear(SPLIT_NOTE_SOURCE)
                refresh(rh.gs(R.string.learner_split_covered))
            }

            SplitDecision.Dropped    -> {
                store.splitPlan = null
                loopNotes.clear(SPLIT_NOTE_SOURCE)
                notificationManager.post(NotificationId.AUTOMATION_MESSAGE, rh.gs(R.string.learner_split_dropped), level = NotificationLevel.NORMAL)
                refresh(rh.gs(R.string.learner_split_dropped))
            }

            is SplitDecision.Wait    -> _status.value = _status.value.copy(splitPlan = plan)
        }
    }

    fun refusePct() {
        store.pctProposal = null
        loopNotes.clear(NOTE_SOURCE)
        refresh(rh.gs(R.string.learner_refused))
    }

    /** "Внести" for the CR block card: the block hours of the profile get the weaker CR; switched to like the basal. */
    fun acceptCr() {
        if (!applying.compareAndSet(false, true)) return
        setBusy(true)
        scope.launch {
            try {
                applyCr()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                aapsLogger.error(LTag.APS, "Learner: applying the CR proposal failed", e)
                finish(rh.gs(R.string.learner_profile_invalid, e.message.orEmpty()))
            } finally {
                applying.set(false)
            }
        }
    }

    private suspend fun applyCr() {
        val p = store.crProposal ?: return finish("")
        val running = profileFunction.getRequestedProfile()
        if (running != null && running.duration > 0L) return finish(rh.gs(R.string.learner_wait_temporary))
        val index = profileRepository.profiles.value.indexOfFirst { it.name == p.profileName }
        val single = profileRepository.profiles.value.getOrNull(index) ?: return finish(rh.gs(R.string.learner_profile_gone))
        val ic = hourly(single.ic)
        if (p.from.any { (h, v) -> abs(ic[h] - v) > 0.005 }) {
            store.crProposal = null
            return finish(rh.gs(R.string.learner_profile_changed))
        }
        val edited = single.copy(ic = withHours(single.ic, p.to))
        val percentage = running?.percentage ?: 100
        val errors = profileRepository.validateStructured(edited) + profileRepository.validatePumpCompatibility(edited, percentage)
        if (errors.isNotEmpty()) return finish(rh.gs(R.string.learner_profile_invalid, errors.joinToString { it.toString() }))
        val saved = profileRepository.replace(index, edited)
        if (saved.isFailure) return finish(rh.gs(R.string.learner_profile_invalid, saved.exceptionOrNull()?.message.orEmpty()))
        val now = dateUtil.now()
        val profileStore = profileRepository.profile.value
        val iCfg = running?.iCfg ?: profileFunction.getProfile()?.iCfg
        val switch = if (profileStore != null && iCfg != null) profileFunction.createProfileSwitch(
            profileStore = profileStore, profileName = p.profileName, durationInMinutes = 0, percentage = percentage,
            timeShiftInHours = ((running?.timeshift ?: 0L) / HOUR_MS).toInt(), timestamp = now,
            action = Action.PROFILE_SWITCH, source = Sources.Aaps, note = rh.gs(R.string.learner_switch_note_cr),
            listValues = emptyList(), iCfg = iCfg
        ) else null
        store.crProposal = null
        store.lastAppliedAt = now
        val label = rh.gs(R.string.learner_eval_label_cr, blockName(p.block))
        startEval(label, now, KIND_CR + p.block)
        note(rh.gs(R.string.learner_why_cr_applied, blockName(p.block)), now, NOTE_LONG_MS)
        aapsLogger.debug(LTag.APS, "Learner: CR of ${p.block} ${p.from} -> ${p.to}, switch=${switch != null}")
        finish(rh.gs(if (switch != null) R.string.learner_applied else R.string.learner_saved_no_switch))
    }

    fun refuseCr() {
        store.crProposal = null
        loopNotes.clear(NOTE_SOURCE)
        refresh(rh.gs(R.string.learner_refused))
    }

    /** The block name in the user's language. */
    fun blockName(block: String): String = rh.gs(
        when (block) {
            MealBlock.BREAKFAST.name -> R.string.learner_block_breakfast
            MealBlock.LUNCH.name     -> R.string.learner_block_lunch
            else                     -> R.string.learner_block_dinner
        }
    )

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
    private fun note(text: String, now: Long, keepMs: Long, source: String = NOTE_SOURCE) = loopNotes.put(LoopNote(source, text, now, now + keepMs))

    private fun refresh(message: String = "") {
        val last = store.lastCardAt
        // A proposal not answered by the next card is old: the numbers it was made from have moved on.
        val now = dateUtil.now()
        store.proposal?.let { if (now - it.madeAt > PROPOSAL_MAX_AGE_MS) store.proposal = null }
        store.duraProposal?.let { if (now - it.madeAt > PROPOSAL_MAX_AGE_MS) store.duraProposal = null }
        store.crProposal?.let { if (now - it.madeAt > PROPOSAL_MAX_AGE_MS) store.crProposal = null }
        store.crProposal?.let { note(rh.gs(R.string.learner_why_cr_proposal, blockName(it.block), it.lowPct), it.madeAt, PROPOSAL_NOTE_MS) }
        // A waiting proposal is always in the "why", also after a restart of the app.
        store.proposal?.let { note(rh.gs(R.string.learner_why_proposal, it.changes.size), it.madeAt, PROPOSAL_NOTE_MS) }
        store.duraProposal?.let { note(rh.gs(R.string.learner_why_dura_proposal, it.from, it.to), it.madeAt, PROPOSAL_NOTE_MS) }
        store.pctProposal?.let { if (now - it.madeAt > PCT_MAX_AGE_MS) store.pctProposal = null }
        store.pctProposal?.let { note(rh.gs(R.string.learner_why_pct_proposal, it.from, it.to), it.madeAt, PROPOSAL_NOTE_MS) }
        _status.value = Status(
            lastCardAt = last,
            nextCardAt = nextCardAt(last),
            proposal = store.proposal,
            duraProposal = store.duraProposal,
            crProposal = store.crProposal,
            lastCr = store.lastCr,
            lastDura = store.lastDura,
            pctProposal = store.pctProposal,
            reboundOn = store.reboundOn,
            guard = guard,
            splitOn = store.splitOn,
            splitPreview = _status.value.splitPreview,
            splitPlan = store.splitPlan,
            lastPct = store.lastPct,
            specialUntil = store.specialDays.firstOrNull { val n = dateUtil.now(); n >= it.first && n < it.second }?.second ?: 0L,
            hypoSize = store.hypoSize,
            peakFitOn = store.peakFitOn,
            peakFit = store.lastPeakFit,
            lowPredOn = store.lowPredOn,
            lowPredMargin = store.lowPredMargin,
            lowPredStats = store.lowWarnings.let { w -> Pair(w.count { it.trueLow == true }, w.count { it.trueLow == false }) },
            curveWatch = store.curveWatch,
            curve = store.lastCurve,
            curveChange = store.curveChange,
            lowCausesOn = store.lowCausesOn,
            lows = store.lastLows?.second.orEmpty(),
            dura = prefs.get(DoubleKey.ApsAutoIsfDuraWeight),
            hours = store.lastHours,
            message = message,
            busy = false,
            sensorHints = store.sensorHints,
            sensorHint = _status.value.sensorHint,
            cannulaHints = store.cannulaHints,
            lowMealHints = store.lowMealHints,
            cannulaHint = _status.value.cannulaHint,
            evalOn = store.evalOn,
            evalPending = store.evalPending,
            lastEval = store.lastEval,
            digestOn = store.digestOn,
            lastDigest = store.lastDigest,
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
        const val PCT_GAP_MS = 2 * 86_400_000L
        const val PCT_MAX_AGE_MS = 2 * 86_400_000L
        const val SPECIAL_NOTE_SOURCE = "learner_special"
        const val PEAK_NOTE_SOURCE = "learner_peak"
        const val PRED_NOTE_SOURCE = "learner_pred"
        const val PRED_TICK_MS = 5 * 60_000L
        const val CURVE_NOTE_SOURCE = "learner_curve"
        const val KIND_BASAL = "basal"
        const val KIND_DURA = "dura:"
        const val KIND_PCT = "pct:"
        const val KIND_CR = "cr:"
        const val SENSOR_NOTE_SOURCE = "learner_sensor"
        const val CANNULA_NOTE_SOURCE = "learner_cannula"
        const val LOW_MEAL_NOTE_SOURCE = "learner_low_meal"
        const val EVAL_NOTE_SOURCE = "learner_eval"
        const val DIGEST_NOTE_SOURCE = "learner_digest"
        const val DIGEST_EVERY_MS = 7 * 86_400_000L
        const val MGDL_PER_MMOL = 18.0
        const val GUARD_NOTE_SOURCE = "learner_guard"
        const val SPLIT_NOTE_SOURCE = "learner_split"
        const val SPLIT_STEP = 0.05
        const val SPLIT_TICK_MS = 60_000L
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
