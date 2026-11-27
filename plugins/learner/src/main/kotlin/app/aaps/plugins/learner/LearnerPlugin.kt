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
        val pctProposal: PctProposal? = null,
        /** The last percentage card: highs, lows (percent), hint to try more than 100 %. */
        val lastPct: Triple<Double, Double, Boolean>? = null,
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
    private var splitJob: Job? = null
    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    override suspend fun onStart() {
        super.onStart()
        refresh()
        if (store.splitPlan != null) watchSplit()
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                runCatching { maybeRunCard() }.onFailure { aapsLogger.error(LTag.APS, "Learner: card failed", it) }
                runCatching { maybeRunPctCard() }.onFailure { aapsLogger.error(LTag.APS, "Learner: percentage card failed", it) }
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

    /**
     * The percentage card (a safety card, lab PCTCARD + PCTMAX_100): once a day from 06:00, over the last 3 days. Not
     * while a temporary profile runs (tried again on the next tick), not while another proposal waits, not within 2 days
     * of its own last change.
     */
    private suspend fun maybeRunPctCard(force: Boolean = false) {
        val now = dateUtil.now()
        val local = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault())
        if (!force && local.hour < CARD_HOUR) return
        val today = local.toLocalDate().toEpochDay()
        if (!force && store.lastPctDay == today) return
        val running = profileFunction.getRequestedProfile()
        if (running != null && running.duration > 0L) return
        store.lastPctDay = today
        if (store.proposal != null || store.duraProposal != null || store.pctProposal != null) return
        if (now - store.lastPctAppliedAt < PCT_GAP_MS) return
        val nowPct = running?.percentage ?: 100
        val card = PctCardEngine.card(history.steps(now, PctCardEngine.DAYS), nowPct)
        store.lastPct = Triple(card.highPct, card.lowPct, card.hintUp)
        aapsLogger.debug(LTag.APS, "Learner: percentage card $card")
        when {
            card.to != null -> {
                store.pctProposal = PctProposal(nowPct, card.to, now, card.highPct, card.lowPct)
                notificationManager.post(NotificationId.AUTOMATION_MESSAGE, rh.gs(R.string.learner_notice_pct), level = NotificationLevel.INFO)
            }
            card.hintUp     -> note(rh.gs(R.string.learner_why_pct_hint, card.highPct), now, NOTE_LONG_MS)
        }
        refresh()
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
            lastDura = store.lastDura,
            pctProposal = store.pctProposal,
            splitOn = store.splitOn,
            splitPreview = _status.value.splitPreview,
            splitPlan = store.splitPlan,
            lastPct = store.lastPct,
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
        const val PCT_GAP_MS = 2 * 86_400_000L
        const val PCT_MAX_AGE_MS = 2 * 86_400_000L
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
