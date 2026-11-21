package app.aaps.ui.compose.overview.ring

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.aaps.core.data.model.GV
import app.aaps.core.interfaces.aps.Loop
import app.aaps.core.interfaces.aps.LoopNote
import app.aaps.core.interfaces.aps.LoopNotes
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.observeChanges
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventLoopUpdateGui
import app.aaps.core.interfaces.rx.events.EventRefreshOverview
import app.aaps.core.interfaces.utils.DateUtil
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesIntoMap
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.binding
import dev.zacsweers.metrox.viewmodel.ViewModelKey
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive

@Immutable
data class RingUiState(
    val why: RingWhy? = null,
    val whyTime: Long = 0L,
    val period: RingPeriod = RingPeriod.DAY,
    val stats: RingStats? = null,
    /** Notes of other plugins (the learner) for the "why", newest first. */
    val notes: List<LoopNote> = emptyList(),
)

/**
 * Data of the "Кольцо" overview that the other overview view models do not have: the plain "why" of the loop's last
 * decision and the time in range of the chosen period. It only reads; it changes nothing in the loop.
 */
// Registers itself, like LoopActionViewModel: @ViewModelKey infers the key from the class.
@ContributesIntoMap(AppScope::class, binding = binding<ViewModel>())
@ViewModelKey
@Stable
@Inject
class RingViewModel(
    private val loop: Loop,
    private val persistenceLayer: PersistenceLayer,
    private val dateUtil: DateUtil,
    private val loopNotes: LoopNotes,
    rxBus: RxBus
) : ViewModel() {

    private val _uiState = MutableStateFlow(RingUiState())
    val uiState: StateFlow<RingUiState> = _uiState.asStateFlow()

    init {
        rxBus.toFlow(EventLoopUpdateGui::class).onEach { refreshWhy(); refreshNotes() }.launchIn(viewModelScope)
        rxBus.toFlow(EventRefreshOverview::class).onEach { refreshWhy() }.launchIn(viewModelScope)
        persistenceLayer.observeChanges<GV>().onEach { refreshStats(settle = true) }.launchIn(viewModelScope)
        loopNotes.notes.onEach { refreshNotes() }.launchIn(viewModelScope)
        refreshWhy()
        refreshStats()
    }

    private fun refreshNotes() {
        _uiState.update { it.copy(notes = loopNotes.valid(dateUtil.now())) }
    }

    fun selectPeriod(period: RingPeriod) {
        _uiState.update { it.copy(period = period) }
        refreshStats()
    }

    private fun refreshWhy() {
        viewModelScope.launch {
            val lastRun = loop.lastRun ?: return@launch
            val request = lastRun.request ?: return@launch
            val why = runCatching {
                RingWhyMapper.map(
                    reason = request.reason,
                    smbUnits = request.smb,
                    tempRequested = request.isTempBasalRequested,
                    rateUph = request.rate,
                    durationMin = request.duration,
                    eventualMgdl = request.json()?.get("eventualBG")?.jsonPrimitive?.doubleOrNull,
                    cobG = request.mealData?.mealCOB ?: 0.0,
                    smbAllowedU = lastRun.constraintsProcessed?.smb,
                )
            }.getOrNull()
            _uiState.update { it.copy(why = why, whyTime = request.date) }
        }
    }

    private var statsJob: Job? = null

    // A new reading or a period tap cancels the count still running, so an older count can never land last;
    // readings that come close together (1-minute sensor, a backfill) give one count.
    private fun refreshStats(settle: Boolean = false) {
        statsJob?.cancel()
        statsJob = viewModelScope.launch {
            if (settle) delay(STATS_SETTLE_MS)
            val period = _uiState.value.period
            val now = dateUtil.now()
            val values = runCatching {
                persistenceLayer.getBgReadingsDataFromTimeToTime(now - period.hours * 3_600_000L, now, true).map { it.value }
            }.getOrDefault(emptyList())
            _uiState.update { if (it.period == period) it.copy(stats = RingLogic.stats(values)) else it }
        }
    }

    private companion object {

        const val STATS_SETTLE_MS = 500L
    }
}
