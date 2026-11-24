package app.aaps.plugins.aps.openAPSAutoISF

import app.aaps.core.data.aps.SMBDefaults
import app.aaps.core.data.configuration.Constants
import app.aaps.core.data.model.GlucoseUnit
import app.aaps.core.data.model.getPassedDurationToTimeInMinutes
import app.aaps.core.data.plugin.PluginType
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.InterfacesStrings
import app.aaps.core.interfaces.aps.APS
import app.aaps.core.interfaces.aps.APSResult
import app.aaps.core.interfaces.aps.AutoIsfFactors
import app.aaps.core.interfaces.aps.AutosensResult
import app.aaps.core.interfaces.aps.CurrentTemp
import app.aaps.core.interfaces.aps.GlucoseStatus
import app.aaps.core.interfaces.aps.OapsProfileAutoIsf
import app.aaps.core.interfaces.bgQualityCheck.BgQualityCheck
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.constraints.Constraint
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.constraints.PluginConstraints
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.db.ProcessedTbrEbData
import app.aaps.core.interfaces.insulin.ConcentrationHelper
import app.aaps.core.interfaces.iob.GlucoseStatusProvider
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.notifications.NotificationManager
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.plugin.PluginBaseWithPreferences
import app.aaps.core.interfaces.plugin.PluginDescription
import app.aaps.core.interfaces.profile.Profile
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.TextResolver
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventAPSCalculationFinished
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.HardLimits
import app.aaps.core.interfaces.utils.Round
import app.aaps.core.utils.MidnightUtils
import app.aaps.core.data.model.SC
import app.aaps.core.interfaces.automation.AutomationStateInterface
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.IntKey
import app.aaps.core.keys.KeysStrings
import app.aaps.core.keys.LongNonKey
import app.aaps.core.keys.UnitDoubleKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.objects.constraints.ConstraintObject
import app.aaps.core.objects.extensions.convertedToAbsolute
import app.aaps.core.objects.extensions.plannedRemainingMinutes
import app.aaps.core.objects.extensions.target
import app.aaps.core.objects.profile.ProfileSealed
import app.aaps.core.ui.CoreUiStrings
import app.aaps.core.ui.compose.icons.IcPluginOpenAPS
import app.aaps.core.ui.compose.preference.PreferenceSubScreenDef
import app.aaps.plugins.aps.ApsStrings
import app.aaps.plugins.aps.openAPSSMB.PhoneMovementDetector
import app.aaps.plugins.aps.openAPSSMB.StepService
import app.aaps.plugins.aps.events.EventOpenAPSUpdateGui
import app.aaps.plugins.aps.events.EventResetOpenAPSGui
import app.aaps.plugins.aps.keys.ApsIntentKey
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.HasMemberInjections
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

@HasMemberInjections
@SingleIn(AppScope::class)
@Inject
open class OpenAPSAutoISFPlugin(
    aapsLogger: AAPSLogger,
    private val rxBus: RxBus,
    private val constraintsChecker: ConstraintsChecker,
    override val rh: TextResolver,
    private val profileFunction: ProfileFunction,
    private val profileUtil: ProfileUtil,
    private val config: Config,
    private val activePlugin: ActivePlugin,
    private val iobCobCalculator: IobCobCalculator,
    private val hardLimits: HardLimits,
    preferences: Preferences,
    protected val dateUtil: DateUtil,
    private val processedTbrEbData: ProcessedTbrEbData,
    private val persistenceLayer: PersistenceLayer,
    private val glucoseStatusProvider: GlucoseStatusProvider,
    private val bgQualityCheck: BgQualityCheck,
    notificationManager: NotificationManager,
    private val determineBasalAutoISF: DetermineBasalAutoISF,
    private val glucoseStatusCalculatorAutoIsf: GlucoseStatusCalculatorAutoIsf,
    private val apsResultProvider: () -> APSResult,
    private val ch: ConcentrationHelper
) : PluginBaseWithPreferences(
    PluginDescription()
        .mainType(PluginType.APS)
        .composeContent { plugin ->
            app.aaps.plugins.aps.compose.OpenAPSComposeContent(
                apsPlugin = plugin as APS,
                rxBus = rxBus,
                rh = rh,
                dateUtil = dateUtil
            )
        }
        .icon(IcPluginOpenAPS)
        .pluginName(ApsStrings.openaps_auto_isf)
        .preferencesVisibleInSimpleMode(false)
        .showInList { config.APS || config.AAPSCLIENT }   // AAPSCLIENT: visible so a client can select the master's APS
        .description(ApsStrings.description_auto_isf),
    ownPreferences = ApsIntentKey.entries,
    aapsLogger, rh, preferences, notificationManager
), APS, PluginConstraints {

    @Inject lateinit var automationStateService: AutomationStateInterface

    // last values
    override var lastAPSRun: Long = 0
    override val algorithm = APSResult.Algorithm.AUTO_ISF
    override var lastAPSResult: APSResult? = null
    private var consoleError = mutableListOf<String>()
    private var consoleLog = mutableListOf<String>()
    val autoIsfVersion = "3.2.1"
    val autoIsfWeights; get() = preferences.get(BooleanKey.ApsUseAutoIsfWeights)
    internal fun readAutoIsfDouble(key: DoubleKey): Double {
        val stored = preferences.get(key)
        val safe = if (stored.isFinite()) stored.coerceIn(key.min, key.max) else key.defaultValue
        if (stored.toBits() != safe.toBits()) {
            consoleError.add("Invalid AutoISF preference ${key.key}=$stored; using bounded value $safe")
        }
        return safe
    }
    private val autoISF_max; get() = readAutoIsfDouble(DoubleKey.ApsAutoIsfMax)
    private val autoISF_min; get() = readAutoIsfDouble(DoubleKey.ApsAutoIsfMin)
    private val bgAccel_ISF_weight; get() = readAutoIsfDouble(DoubleKey.ApsAutoIsfBgAccelWeight)
    private val bgBrake_ISF_weight; get() = readAutoIsfDouble(DoubleKey.ApsAutoIsfBgBrakeWeight)
    private val pp_ISF_weight; get() = readAutoIsfDouble(DoubleKey.ApsAutoIsfPpWeight)
    private val lower_ISFrange_weight; get() = readAutoIsfDouble(DoubleKey.ApsAutoIsfLowBgWeight)
    private val higher_ISFrange_weight; get() = readAutoIsfDouble(DoubleKey.ApsAutoIsfHighBgWeight)
    private val dura_ISF_weight; get() = readAutoIsfDouble(DoubleKey.ApsAutoIsfDuraWeight)
    private val smb_delivery_ratio; get() = preferences.get(DoubleKey.ApsAutoIsfSmbDeliveryRatio)
    private val smb_delivery_ratio_min; get() = preferences.get(DoubleKey.ApsAutoIsfSmbDeliveryRatioMin)
    private val smb_delivery_ratio_max; get() = preferences.get(DoubleKey.ApsAutoIsfSmbDeliveryRatioMax)
    private val smb_delivery_ratio_bg_range
        get() = if (preferences.get(DoubleKey.ApsAutoIsfSmbDeliveryRatioBgRange) < 10.0) preferences.get(DoubleKey.ApsAutoIsfSmbDeliveryRatioBgRange) * Constants.MMOLL_TO_MGDL else preferences.get(DoubleKey.ApsAutoIsfSmbDeliveryRatioBgRange)
    val smbMaxRangeExtension; get() = preferences.get(DoubleKey.ApsAutoIsfSmbMaxRangeExtension)
    private val enableSMB_EvenOn_OddOff_always; get() = preferences.get(BooleanKey.ApsAutoIsfSmbOnEvenTarget) // for profile target
    val iobThresholdPercent; get() = preferences.get(IntKey.ApsAutoIsfIobThPercent)
    private val exerciseMode; get() = SMBDefaults.exercise_mode
    private val highTemptargetRaisesSensitivity; get() = preferences.get(BooleanKey.ApsAutoIsfHighTtRaisesSens)
    /** The steps the phone counted, in the form the activity monitor uses. */
    private fun phoneSteps() = MonitorSteps(
        last5 = StepService.getRecentStepCount5Min(),
        before5 = StepService.getRecentStepCount10Min(),
        before10 = StepService.getRecentStepCount15Min(),
        last30 = StepService.getRecentStepCount30Min(),
        last60 = StepService.getRecentStepCount60Min(),
        fromWatch = false
    )

    /**
     * AutoISF fork: the steps of this loop run, from the source chosen in the settings (phone, watch or
     * automatic). Null when only the watch may be used and it has sent nothing in the last 10 minutes.
     */
    private suspend fun monitorSteps(): MonitorSteps? {
        val source = preferences.get(IntKey.ActivityMonitorStepSource)
        if (source == STEP_SOURCE_PHONE) return phoneSteps()
        val now = dateUtil.now()
        val watch = watchSteps(persistenceLayer.getStepsCountFromTimeToTime(now - T.mins(61).msecs(), now), now)
        return chooseSteps(source, phoneSteps(), watch)
    }
    private val phone_moved; get() = PhoneMovementDetector.phoneMoved()
    val normalTarget = Constants.NORMAL_TARGET_MGDL
    /** Factors of the current run. Filled while the run is calculated, then stored in the result (RT.autoIsf). */
    private var autoIsfFactors = AutoIsfFactors()

    override fun usingDynamicIsf() = true //: Boolean = preferences.get(BooleanKey.ApsUseAutoIsf)

    /**
     * AutoISF works out its ISF inside its own run, as variable_sens. Every other caller gets the profile ISF,
     * so this must answer null. It used to run autoISF() here as well, only to fill a cache for the old
     * average ISF for carbs. That average is now worked out from the stored results, see `IsfForCarbs`.
     */
    override fun getIsfMgdl(profile: Profile, caller: String): Double? = null

    override fun getSensitivityOverviewString(): String? = null // placeholder for Auto ISF Detailed information for overview

    internal fun getHalfBasalExerciseTargetMgdl(): Int = preferences.get(IntKey.ApsAutoIsfHalfBasalExerciseTarget)
    override suspend fun invoke(initiator: String, tempBasalFallback: Boolean) = withContext(Dispatchers.Default) {
        aapsLogger.debug(LTag.APS, "invoke from $initiator tempBasalFallback: $tempBasalFallback")
        lastAPSResult = null
        val glucoseStatus = glucoseStatusProvider.glucoseStatusData
        val profile = profileFunction.getProfile()
        val pump = activePlugin.activePump
        if (profile == null) {
            rxBus.send(EventResetOpenAPSGui(rh.gs(CoreUiStrings.no_profile_set)))
            aapsLogger.debug(LTag.APS, rh.gs(CoreUiStrings.no_profile_set))
            return@withContext
        }
        if (!isEnabled()) {
            rxBus.send(EventResetOpenAPSGui(rh.gs(ApsStrings.openapsma_disabled)))
            aapsLogger.debug(LTag.APS, rh.gs(ApsStrings.openapsma_disabled))
            return@withContext
        }
        if (glucoseStatus == null) {
            rxBus.send(EventResetOpenAPSGui(rh.gs(ApsStrings.openapsma_no_glucose_data)))
            aapsLogger.debug(LTag.APS, rh.gs(ApsStrings.openapsma_no_glucose_data))
            return@withContext
        }

        val inputConstraints = ConstraintObject(0.0, aapsLogger) // fake. only for collecting all results

        if (!hardLimits.checkHardLimits(profile.iCfg.dia, InterfacesStrings.profile_dia, hardLimits.diaRange())) return@withContext
        if (!hardLimits.checkHardLimits(
                profile.getIcTimeFromMidnight(MidnightUtils.secondsFromMidnight()),
                InterfacesStrings.profile_carbs_ratio_value,
                hardLimits.icRange()
            )
        ) return@withContext
        if (!hardLimits.checkHardLimits(profile.getIsfMgdl("OpenAPSAutoISFPlugin"), InterfacesStrings.profile_sensitivity_value, HardLimits.LIMIT_ISF)) return@withContext
        if (!hardLimits.checkHardLimits(profile.getMaxDailyBasal(), CoreUiStrings.profile_max_daily_basal_value, 0.02, hardLimits.maxBasal())) return@withContext
        if (!hardLimits.checkHardLimits(ch.fromPump(pump.baseBasalRate), CoreUiStrings.current_basal_value, 0.01, hardLimits.maxBasal())) return@withContext

        // End of check, start gathering data

        val autoIsfMode = usingDynamicIsf()  // preferences.get(BooleanKey.ApsUseAutoIsf)
        val smbEnabled = preferences.get(BooleanKey.ApsUseSmb)
        val advancedFiltering = constraintsChecker.isAdvancedFilteringEnabled().also { inputConstraints.copyReasons(it) }.value()

        val now = dateUtil.now()
        autoIsfFactors = AutoIsfFactors(acceIsf = 1.0, bgIsf = 1.0, ppIsf = 1.0, duraIsf = 1.0, finalIsf = 1.0)
        val tb = processedTbrEbData.getTempBasalIncludingConvertedExtended(now)
        val currentTemp = CurrentTemp(
            duration = tb?.plannedRemainingMinutes ?: 0,
            rate = tb?.convertedToAbsolute(now, profile) ?: 0.0,
            minutesrunning = tb?.getPassedDurationToTimeInMinutes(now)
        )
        var minBg = hardLimits.verifyHardLimits(Round.roundTo(profile.getTargetLowMgdl(), 0.1), InterfacesStrings.profile_low_target, HardLimits.LIMIT_MIN_BG)
        var maxBg = hardLimits.verifyHardLimits(Round.roundTo(profile.getTargetHighMgdl(), 0.1), InterfacesStrings.profile_high_target, HardLimits.LIMIT_MAX_BG)
        var targetBg = hardLimits.verifyHardLimits(profile.getTargetMgdl(), CoreUiStrings.temp_target_value, HardLimits.LIMIT_TARGET_BG)
        var isTempTarget = false
        persistenceLayer.getTemporaryTargetActiveAt(dateUtil.now())?.let { tempTarget ->
            isTempTarget = true
            minBg = hardLimits.verifyHardLimits(tempTarget.lowTarget, CoreUiStrings.temp_target_low_target, HardLimits.LIMIT_TEMP_MIN_BG)
            maxBg = hardLimits.verifyHardLimits(tempTarget.highTarget, CoreUiStrings.temp_target_high_target, HardLimits.LIMIT_TEMP_MAX_BG)
            targetBg = hardLimits.verifyHardLimits(tempTarget.target(), CoreUiStrings.temp_target_value, HardLimits.LIMIT_TEMP_TARGET_BG)
        }

        var autosensResult = AutosensResult()
        var variableSensitivity = profile.getProfileIsfMgdl()
        val sens = profile.getIsfMgdl("OpenAPSAutoISFPlugin")

        if (constraintsChecker.isAutosensModeEnabled().value()) {
            val autosensData = iobCobCalculator.getLastAutosensDataWithWaitForCalculationFinish("OpenAPSAutoISFPlugin")
            if (autosensData == null) {
                rxBus.send(EventResetOpenAPSGui(rh.gs(ApsStrings.openaps_no_as_data)))
                return@withContext
            }
            autosensResult = autosensData.autosensResult
        } else autosensResult.sensResult = "autosens disabled"
        val halfBasalExerciseTarget = getHalfBasalExerciseTargetMgdl()
        val iobArray = iobCobCalculator.calculateIobArrayForSMB(autosensResult, highTemptargetRaisesSensitivity, halfBasalExerciseTarget, isTempTarget)
        val mealData = iobCobCalculator.getMealDataWithWaitingForCalculationFinish()
        val iobData = iobArray[0]
        val profile_percentage = if (profile is ProfileSealed.EPS) profile.value.originalPercentage else 100
        val microBolusAllowed = constraintsChecker.isSMBModeEnabled(ConstraintObject(tempBasalFallback.not(), aapsLogger)).also { inputConstraints.copyReasons(it) }.value()

        val steps = monitorSteps()
        aapsLogger.debug(LTag.APS, "invoke found step counts $steps")
        consoleError = mutableListOf()
        consoleLog = mutableListOf()
        val hourOfDay = Instant.fromEpochMilliseconds(dateUtil.now()).toLocalDateTime(TimeZone.currentSystemDefault()).hour
        val hour = max(1, hourOfDay)
        var activityRatio = activityMonitor(steps, isTempTarget, glucoseStatus.glucose, targetBg, hour)
        var activityLog = if (consoleLog.size == 0) "Activity Monitor skipped" else consoleLog[0]
        consoleLog.clear()

        val stepActivityDetected = activityRatio < 1
        val stepInactivityDetected = activityRatio > 1
        // Kept in the preferences only as the last known state; the calculation gets it directly below
        preferences.put(BooleanKey.ActivityMonitorStepsActive, stepActivityDetected)
        preferences.put(BooleanKey.ActivityMonitorStepsInactive, stepInactivityDetected)
        val activity = ActivityState(ratio = activityRatio, activityDetected = stepActivityDetected, inactivityDetected = stepInactivityDetected)
        if (autoIsfMode) {
            variableSensitivity = autoISF(profile, autosensResult.ratio, activity)
        }

        val oapsProfile = OapsProfileAutoIsf(
            dia = 0.0, // not used
            min_5m_carbimpact = 0.0, // not used
            max_iob = constraintsChecker.getMaxIOBAllowed().also { inputConstraints.copyReasons(it) }.value(),
            max_daily_basal = profile.getMaxDailyBasal(),
            max_basal = constraintsChecker.getMaxBasalAllowed(profile).also { inputConstraints.copyReasons(it) }.value(),
            min_bg = minBg,
            max_bg = maxBg,
            target_bg = targetBg,
            carb_ratio = profile.getIc(),
            sens = sens,
            autosens_adjust_targets = false, // not used
            max_daily_safety_multiplier = preferences.get(DoubleKey.ApsMaxDailyMultiplier),
            current_basal_safety_multiplier = preferences.get(DoubleKey.ApsMaxCurrentBasalMultiplier),
            lgsThreshold = profileUtil.convertToMgdlDetect(preferences.get(UnitDoubleKey.ApsLgsThreshold)).toInt(),
            high_temptarget_raises_sensitivity = exerciseMode || highTemptargetRaisesSensitivity, //was false,
            low_temptarget_lowers_sensitivity = preferences.get(BooleanKey.ApsAutoIsfLowTtLowersSens), // was false,
            sensitivity_raises_target = preferences.get(BooleanKey.ApsSensitivityRaisesTarget),
            resistance_lowers_target = preferences.get(BooleanKey.ApsResistanceLowersTarget),
            adv_target_adjustments = SMBDefaults.adv_target_adjustments,
            exercise_mode = SMBDefaults.exercise_mode,
            half_basal_exercise_target = halfBasalExerciseTarget,
            activity_detection = preferences.get(BooleanKey.ActivityMonitorDetection),
            recent_steps_5_minutes  = steps?.last5 ?: 0,
            recent_steps_10_minutes = steps?.before5 ?: 0,
            recent_steps_15_minutes = steps?.before10 ?: 0,
            recent_steps_30_minutes = steps?.last30 ?: 0,
            recent_steps_60_minutes = steps?.last60 ?: 0,
            phone_moved = phone_moved,
            time_since_start = (dateUtil.now() - preferences.get(LongNonKey.AppStart)).milliseconds.inWholeMinutes,
            now = hourOfDay,
            maxCOB = SMBDefaults.maxCOB,
            skip_neutral_temps = pump.setNeutralTempAtFullHour(),
            remainingCarbsCap = SMBDefaults.remainingCarbsCap,
            enableUAM = constraintsChecker.isUAMEnabled().also { inputConstraints.copyReasons(it) }.value(),
            A52_risk_enable = SMBDefaults.A52_risk_enable,
            SMBInterval = preferences.get(IntKey.ApsMaxSmbFrequency),
            enableSMB_with_COB = smbEnabled && preferences.get(BooleanKey.ApsUseSmbWithCob),
            enableSMB_with_temptarget = smbEnabled && preferences.get(BooleanKey.ApsUseSmbWithLowTt),
            allowSMB_with_high_temptarget = smbEnabled && preferences.get(BooleanKey.ApsUseSmbWithHighTt),
            enableSMB_always = smbEnabled && preferences.get(BooleanKey.ApsUseSmbAlways) && advancedFiltering,
            enableSMB_after_carbs = smbEnabled && preferences.get(BooleanKey.ApsUseSmbAfterCarbs) && advancedFiltering,
            maxSMBBasalMinutes = preferences.get(IntKey.ApsMaxMinutesOfBasalToLimitSmb),
            maxUAMSMBBasalMinutes = preferences.get(IntKey.ApsUamMaxMinutesOfBasalToLimitSmb),
            bolus_increment = pump.pumpDescription.bolusStep,
            carbsReqThreshold = preferences.get(IntKey.ApsCarbsRequestThreshold),
            current_basal = ch.fromPump(activePlugin.activePump.baseBasalRate),
            temptargetSet = isTempTarget,
            autosens_max = preferences.get(DoubleKey.AutosensMax),
            out_units = if (profileFunction.getUnits() == GlucoseUnit.MMOL) "mmol/L" else "mg/dl",
            variable_sens = variableSensitivity,
            autoISF_version = autoIsfVersion,
            enable_autoISF = autoIsfWeights,
            autoISF_max = autoISF_max,
            autoISF_min = autoISF_min,
            bgAccel_ISF_weight = bgAccel_ISF_weight,
            bgBrake_ISF_weight = bgBrake_ISF_weight,
            pp_ISF_weight = pp_ISF_weight,
            lower_ISFrange_weight = lower_ISFrange_weight,
            higher_ISFrange_weight = higher_ISFrange_weight,
            dura_ISF_weight = dura_ISF_weight,
            smb_delivery_ratio = smb_delivery_ratio,
            smb_delivery_ratio_min = smb_delivery_ratio_min,
            smb_delivery_ratio_max = smb_delivery_ratio_max,
            smb_delivery_ratio_bg_range = preferences.get(DoubleKey.ApsAutoIsfSmbDeliveryRatioBgRange),   //smb_delivery_ratio_bg_range was always in mg/dL
            smb_max_range_extension = smbMaxRangeExtension,
            enableSMB_EvenOn_OddOff_always = enableSMB_EvenOn_OddOff_always,
            iob_threshold_percent = iobThresholdPercent,
            profile_percentage = profile_percentage
        )

        // Refuse to run the algorithm with degenerate ISF inputs — division by these would produce NaN/Infinity in
        // the result. carb_ratio feeds csf (sens/carb_ratio) and autosensResult.ratio becomes the non-autoISF
        // sensitivityRatio (sens = profile.sens/sensitivityRatio); a non-finite/≤0 value of any cascades to a NaN
        // carbsReq (round() crash). Mirrors the OpenAPSSMBPlugin guard.
        val invalidInputs = !oapsProfile.sens.isFinite() || oapsProfile.sens <= 0.0 ||
            !oapsProfile.carb_ratio.isFinite() || oapsProfile.carb_ratio <= 0.0 ||
            !autosensResult.ratio.isFinite() || autosensResult.ratio <= 0.0 ||
            (autoIsfMode && (!oapsProfile.variable_sens.isFinite() || oapsProfile.variable_sens <= 0.0))
        if (invalidInputs) {
            val msg = "OpenAPS AutoISF aborting: invalid ISF inputs " +
                "autoIsfMode=$autoIsfMode sens=${oapsProfile.sens} carb_ratio=${oapsProfile.carb_ratio} " +
                "autosensRatio=${autosensResult.ratio} variable_sens=${oapsProfile.variable_sens}"
            aapsLogger.error(LTag.APS, msg)
            rxBus.send(EventResetOpenAPSGui(msg))
            return@withContext
        }
        var sensitivityRatio = 1.0
        // TODO eliminate
        val target_bg = (minBg + maxBg) / 2
        val exerciseModeActive = highTemptargetRaisesSensitivity && isTempTarget && Constants.isAboveNormalTarget(target_bg)
        val resistanceModeActive = oapsProfile.low_temptarget_lowers_sensitivity && isTempTarget && Constants.isBelowNormalTarget(target_bg)
        if (exerciseModeActive || resistanceModeActive || stepActivityDetected || stepInactivityDetected) {
            if (exerciseModeActive || resistanceModeActive) {
                val resistanceMax = min(1.5, preferences.get(DoubleKey.AutosensMax))
                val c = (oapsProfile.half_basal_exercise_target - normalTarget).toDouble()
                if (c * (c + target_bg - normalTarget) <= 0.0) {
                    sensitivityRatio = resistanceMax
                } else {
                    sensitivityRatio = c / (c + target_bg - normalTarget)
                    sensitivityRatio = min(sensitivityRatio, resistanceMax)
                    sensitivityRatio = round(sensitivityRatio, 2)
                }
            } else {
                sensitivityRatio = activitySensitivityRatio(autosensResult.ratio, activityRatio, stepActivityDetected, preferences.get(DoubleKey.AutosensMax))
            }
        }
        var iobTH_reduction_ratio = 1.0
        var use_iobTH = false
        if (iobThresholdPercent != 100) {
            iobTH_reduction_ratio = profile_percentage / 100.0 * sensitivityRatio
            use_iobTH = true
        }
        val iobTHtolerance = 130.0
        val iobTHvirtual = iobThresholdPercent * iobTHtolerance / 10000.0 * oapsProfile.max_iob * iobTH_reduction_ratio
        val loopWantedSmb = loop_smb(microBolusAllowed, oapsProfile, iobData.iob, use_iobTH, iobTHvirtual / iobTHtolerance * 100.0)
        val flatBGsDetected = bgQualityCheck.state == BgQualityCheck.State.FLAT
        val smbRatio = determine_varSMBratio(glucoseStatus.glucose.toInt(), target_bg, loopWantedSmb)

        aapsLogger.debug(LTag.APS, ">>> Invoking determine_basal AutoISF <<<")
        aapsLogger.debug(LTag.APS, "Glucose status:     $glucoseStatus")
        aapsLogger.debug(LTag.APS, "Current temp:       $currentTemp")
        aapsLogger.debug(LTag.APS, "IOB data:           ${iobArray.joinToString()}")
        aapsLogger.debug(LTag.APS, "Profile:            $oapsProfile")
        aapsLogger.debug(LTag.APS, "Autosens data:      $autosensResult")
        aapsLogger.debug(LTag.APS, "Meal data:          $mealData")
        aapsLogger.debug(LTag.APS, "MicroBolusAllowed:  $microBolusAllowed")
        aapsLogger.debug(LTag.APS, "flatBGsDetected:    $flatBGsDetected")
        aapsLogger.debug(LTag.APS, "AutoIsfMode:        $autoIsfMode")
        //aapsLogger.debug(LTag.APS, "AutoISF extras:     ${Json.encodeToString(OapsProfile.serializer(), oapsProfile)}")

        determineBasalAutoISF.determine_basal(
            glucose_status = glucoseStatus,
            currenttemp = currentTemp,
            iob_data_array = iobArray,
            profile = oapsProfile,
            autosens_data = autosensResult,
            meal_data = mealData,
            microBolusAllowed = microBolusAllowed,
            currentTime = now,
            flatBGsDetected = flatBGsDetected,
            autoIsfMode = autoIsfMode,
            loop_wanted_smb = loopWantedSmb,
            profile_percentage = profile_percentage,
            smb_ratio = smbRatio,
            smb_max_range_extension = smbMaxRangeExtension,
            iob_threshold_percent = iobThresholdPercent,
            activity_consoleLog = activityLog,
            auto_isf_consoleError = consoleError,
            auto_isf_consoleLog = consoleLog,
            activity = activity
        ).also {
            autoIsfFactors.smbOn = determineBasalAutoISF.lastSmbOn
            autoIsfFactors.smbOffReason = determineBasalAutoISF.lastSmbOffReason
            it.autoIsf = autoIsfFactors.finiteOnly()
            val determineBasalResult = apsResultProvider().with(it)
            // Preserve input data
            determineBasalResult.inputConstraints = inputConstraints
            determineBasalResult.autosensResult = autosensResult
            determineBasalResult.iobData = iobArray
            determineBasalResult.glucoseStatus = glucoseStatus
            determineBasalResult.currentTemp = currentTemp
            determineBasalResult.oapsProfileAutoIsf = oapsProfile
            determineBasalResult.mealData = mealData
            lastAPSResult = determineBasalResult
            lastAPSRun = now
            aapsLogger.debug(LTag.APS, "Result: $it")
            rxBus.send(EventAPSCalculationFinished())
        }

        rxBus.send(EventOpenAPSUpdateGui())
    }

    override fun getGlucoseStatusData(allowOldData: Boolean): GlucoseStatus? = glucoseStatusCalculatorAutoIsf.getGlucoseStatusData(allowOldData)

    override fun isSuperBolusEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        value.set(false)
        return value
    }

    override suspend fun applyMaxIOBConstraints(maxIob: Constraint<Double>): Constraint<Double> {
        if (isEnabled()) {
            val maxIobPref = preferences.get(DoubleKey.ApsSmbMaxIob)
            maxIob.setIfSmaller(maxIobPref, rh.gs(ApsStrings.limiting_iob, maxIobPref, rh.gs(ApsStrings.maxvalueinpreferences)), this)
            maxIob.setIfSmaller(hardLimits.maxIobSMB(), rh.gs(ApsStrings.limiting_iob, hardLimits.maxIobSMB(), rh.gs(ApsStrings.hardlimit)), this)
        }
        return maxIob
    }

    override fun applyBasalConstraints(absoluteRate: Constraint<Double>, profile: Profile): Constraint<Double> {
        if (isEnabled()) {
            var maxBasal = preferences.get(DoubleKey.ApsMaxBasal)
            if (maxBasal < profile.getMaxDailyBasal()) {
                maxBasal = profile.getMaxDailyBasal()
                absoluteRate.addReason(rh.gs(ApsStrings.increasing_max_basal), this)
            }
            absoluteRate.setIfSmaller(maxBasal, rh.gs(CoreUiStrings.limitingbasalratio, maxBasal, rh.gs(ApsStrings.maxvalueinpreferences)), this)

            // Check percentRate but absolute rate too, because we know real current basal in pump
            val maxBasalMultiplier = preferences.get(DoubleKey.ApsMaxCurrentBasalMultiplier)
            val maxFromBasalMultiplier = floor(maxBasalMultiplier * profile.getBasal() * 100) / 100
            absoluteRate.setIfSmaller(
                maxFromBasalMultiplier,
                rh.gs(CoreUiStrings.limitingbasalratio, maxFromBasalMultiplier, rh.gs(ApsStrings.max_basal_multiplier)),
                this
            )
            val maxBasalFromDaily = preferences.get(DoubleKey.ApsMaxDailyMultiplier)
            val maxFromDaily = floor(profile.getMaxDailyBasal() * maxBasalFromDaily * 100) / 100
            absoluteRate.setIfSmaller(maxFromDaily, rh.gs(CoreUiStrings.limitingbasalratio, maxFromDaily, rh.gs(ApsStrings.max_daily_basal_multiplier)), this)
        }
        return absoluteRate
    }

    override suspend fun isSMBModeEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        val enabled = preferences.get(BooleanKey.ApsUseSmb)
        if (!enabled) value.set(false, rh.gs(ApsStrings.smb_disabled_in_preferences), this)
        return value
    }

    override fun isUAMEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        val enabled = preferences.get(BooleanKey.ApsUseUam)
        if (!enabled) value.set(false, rh.gs(ApsStrings.uam_disabled_in_preferences), this)
        return value
    }

    override fun isAutosensModeEnabled(value: Constraint<Boolean>): Constraint<Boolean> {
        val enabled = preferences.get(BooleanKey.ApsUseAutosens)
        if (!enabled) value.set(false, rh.gs(ApsStrings.autosens_disabled_in_preferences), this)
        return value
    }

    // Rounds value to 'digits' decimal places
    /**
     * AutoISF fork: which values the parabola used, for the APS tab. Only when the setting "parabola on
     * smoothed values" is on; with the default (raw values, as in AutoISF 3.2.1) nothing is added.
     */
    private fun parabolaSourceLine(): String? {
        if (!preferences.get(BooleanKey.ApsAutoIsfParabolaSmoothed)) return null
        val shortest = preferences.get(IntKey.ApsAutoIsfParabolaMinMinutes)
        return if (iobCobCalculator.ads.smoothedReadings != null) "Parabola uses UKF-smoothed values, shortest parabola ${shortest}m"
        else "Parabola: smoothed values are switched on, but there are no UKF 1 minute values, so raw values are used"
    }

    // different for negative numbers fun round(value: Double, digits: Int): Double = BigDecimal(value).setScale(digits, RoundingMode.HALF_EVEN).toDouble()
    fun round(value: Double, digits: Int): Double {
        // Pass NaN AND ±Infinity through untouched - Math.round saturates at Long.MAX_VALUE, which would
        // turn an infinite value into a normal looking number and hide it from the invalidInputs check.
        if (!value.isFinite()) return value
        val scale = 10.0.pow(digits.toDouble())
        return (value * scale).roundToLong() / scale
    }

    fun convert_bg(value: Double): String =
        profileUtil.fromMgdlToStringInUnits(value).replace("-0.0", "0.0")

    fun convert_bg_to_units(value: Double, profile: OapsProfileAutoIsf): Double =
        if (profile.out_units == "mmol/L") value * Constants.MGDL_TO_MMOLL else value

    /** Autosens ratio of the last calculation, or 1.0 when autosens is disabled or not available. */
    private suspend fun lastAutosensRatio(): Double {
        if (!constraintsChecker.isAutosensModeEnabled().value()) return 1.0
        return iobCobCalculator.getLastAutosensDataWithWaitForCalculationFinish("OpenAPSAutoISFPlugin")?.autosensResult?.ratio ?: 1.0
    }

    suspend fun activityMonitor(steps: MonitorSteps?, isTempTarget: Boolean, bg: Double, target_bg: Double, now: Int): Double {
        if (preferences.get(BooleanKey.ActivityMonitorShowStepsFromSmartphone)) {
            val nowMillis = dateUtil.now()
            val phone = phoneSteps()
            val stepsCount = SC(
                duration = 0,
                timestamp = nowMillis,
                steps5min = phone.last5,
                steps10min = phone.last5 + phone.before5,
                steps15min = phone.last15Sum,
                steps30min = phone.last30,
                steps60min = phone.last60,
                steps180min = StepService.getRecentStepCount180Min(),
                device = PHONE_STEPS_DEVICE
            )
            persistenceLayer.insertOrUpdateStepsCounts(listOf(stepsCount))
        }

        val phoneMoved = PhoneMovementDetector.phoneMoved()
        val lastAppStart = preferences.get(LongNonKey.AppStart)
        val time_since_start = (dateUtil.now() - lastAppStart).milliseconds.inWholeMinutes
        val activityDetection = preferences.get(BooleanKey.ActivityMonitorDetection)
        val activity_scale_factor = preferences.get(DoubleKey.ActivityScaleFactor)
        val inactivity_scale_factor = preferences.get(DoubleKey.InactivityScaleFactor)
        var activityRatio = 1.0
        val ignore_inactivity_overnight = preferences.get(BooleanKey.ActivityMonitorOvernight)
        val inactivity_idle_start = preferences.get(IntKey.ActivityMonitorIdleStart)
        val inactivity_idle_end = preferences.get(IntKey.ActivityMonitorIdleEnd)

        val existSleepState = automationStateService.hasStateValues("Sleeping")
        val useSleepState = automationStateService.inState("Sleeping", "True")
        aapsLogger.debug(LTag.APS, "State json for Sleep mode: {\"Sleeping\":\"${automationStateService.getState("Sleeping")}\"}")
        if (useSleepState && steps != null && steps.last15Sum == steps.last30) {
            automationStateService.setState("query_got_up", "query_it")
        }
        aapsLogger.debug(LTag.APS, "State json for got up query: {\"query_got_up\":\"${automationStateService.getState("query_got_up")}\"}")

        if (!activityDetection) {
            consoleLog.add("Activity monitor disabled in settings")
        } else if (isTempTarget) {
            consoleLog.add("Activity monitor disabled: tempTarget")
        } else if (steps == null) {
            consoleLog.add("Activity monitor disabled: no steps from the watch in the last 10m")
        } else if (!steps.fromWatch && !phoneMoved) {
            consoleLog.add("Activity monitor disabled: Phone seems not to be carried for the last 15m")
        } else {
            val source = if (steps.fromWatch) "watch" else "phone"
            if (!steps.fromWatch && time_since_start < 60 && steps.last60 <= 200) {
                consoleLog.add("Activity monitor initialising for ${60 - time_since_start} more minutes: inactivity detection disabled")
            } else if (useSleepState && steps.last60 <= 200) {
                consoleLog.add("Activity monitor disabled inactivity detection: sleeping state")
            } else if (((inactivity_idle_start > inactivity_idle_end && (now >= inactivity_idle_start || now < inactivity_idle_end))
                    || (now >= inactivity_idle_start && now < inactivity_idle_end))
                && steps.last60 <= 200 && ignore_inactivity_overnight && !existSleepState) {
                consoleLog.add("Activity monitor disabled inactivity detection: sleeping hours")
            } else if (steps.last5 > 300 || steps.before5 > 300 || steps.before10 > 300 || steps.last30 > 1500 || steps.last60 > 2500) {
                activityRatio = 1 - 0.3 * activity_scale_factor
                consoleLog.add("Activity monitor detected activity ($source steps), sensitivity ratio: $activityRatio")
            } else if (steps.last5 > 200 || steps.before5 > 200 || steps.before10 > 200
                || steps.last30 > 500 || steps.last60 > 800) {
                activityRatio = 1 - 0.15 * activity_scale_factor
                consoleLog.add("Activity monitor detected partial activity ($source steps), sensitivity ratio: $activityRatio")
            } else if (bg < target_bg && steps.last60 <= 200) {
                consoleLog.add("Activity monitor disabled inactivity detection: bg < target")
            } else if (steps.last60 < 50) {
                activityRatio = 1 + 0.2 * inactivity_scale_factor
                consoleLog.add("Activity monitor detected inactivity ($source steps), sensitivity ratio: $activityRatio")
            } else if (steps.last60 <= 200) {
                activityRatio = 1 + 0.1 * inactivity_scale_factor
                consoleLog.add("Activity monitor detected partial inactivity ($source steps), sensitivity ratio: $activityRatio")
            } else {
                consoleLog.add("Activity monitor detected neutral state")
            }
        }
        preferences.put(DoubleKey.ActivityMonitorRatio, activityRatio)
        var activityMsg = "Activity Monitor json: {\"activity_scale_factor\":$activity_scale_factor,\"inactivity_scale_factor\":$inactivity_scale_factor"
        activityMsg += ",\"recentSteps5Minutes\":${steps?.last5},\"recentSteps10Minutes\":${steps?.before5},\"recentSteps15Minutes\":${steps?.before10}"
        activityMsg += ",\"recentSteps30Minutes\":${steps?.last30},\"recentSteps60Minutes\":${steps?.last60},\"steps_from_watch\":${steps?.fromWatch}"
        activityMsg += ",\"phone_moved\":$phoneMoved,\"time_since_start\":$time_since_start,\"activity_detection\":$activityDetection"
        activityMsg += ",\"ignore_inactivity_overnight\":$ignore_inactivity_overnight,\"inactivity_idle_start\":$inactivity_idle_start,\"inactivity_idle_end\":$inactivity_idle_end}"
        aapsLogger.debug(LTag.APS, activityMsg)
        return activityRatio
    }

    /**
     * @param autosensRatio the autosens ratio of this loop run, so the whole run uses one value. Null reads the
     * last autosens result (for callers outside a run, like tests).
     */
    suspend fun autoISF(profile: Profile, autosensRatio: Double? = null, activity: ActivityState = ActivityState()): Double {
        val autosensRatioOfRun = autosensRatio ?: lastAutosensRatio()
        val sens = profile.getProfileIsfMgdl()
        val glucose_status = glucoseStatusCalculatorAutoIsf.getGlucoseStatusData(allowOldData = false)

        val high_temptarget_raises_sensitivity = exerciseMode || highTemptargetRaisesSensitivity
        var target_bg = hardLimits.verifyHardLimits(profile.getTargetMgdl(), CoreUiStrings.temp_target_value, HardLimits.LIMIT_TARGET_BG)
        var isTempTarget = false
        persistenceLayer.getTemporaryTargetActiveAt(dateUtil.now())?.let { tempTarget ->
            isTempTarget = true
            target_bg = hardLimits.verifyHardLimits(tempTarget.target(), CoreUiStrings.temp_target_value, HardLimits.LIMIT_TEMP_TARGET_BG)
        }
        // Activity result of this run, handed over by invoke() (not read back from the preferences)
        val activityRatio = activity.ratio
        val stepActivityDetected = activity.activityDetected
        val stepInactivityDetected = activity.inactivityDetected
        var sensitivityRatio = 1.0
        val exerciseModeActive = high_temptarget_raises_sensitivity && isTempTarget && Constants.isAboveNormalTarget(target_bg)
        val resistanceModeActive = preferences.get(BooleanKey.ApsAutoIsfLowTtLowersSens) && isTempTarget && Constants.isBelowNormalTarget(target_bg)
        if (exerciseModeActive || resistanceModeActive || stepActivityDetected || stepInactivityDetected) {
            if (exerciseModeActive || resistanceModeActive) {
                val resistanceMax = min(1.5, preferences.get(DoubleKey.AutosensMax))
                val c = (getHalfBasalExerciseTargetMgdl() - normalTarget).toDouble()
                if (c * (c + target_bg - normalTarget) <= 0.0) {
                    sensitivityRatio = resistanceMax
                } else {
                    sensitivityRatio = c / (c + target_bg - normalTarget)
                }
                sensitivityRatio = min(sensitivityRatio, resistanceMax)
                sensitivityRatio = round(sensitivityRatio, 2)
            } else {
                sensitivityRatio = activitySensitivityRatio(autosensRatioOfRun, activityRatio, stepActivityDetected, preferences.get(DoubleKey.AutosensMax))
            }
        } else {
            sensitivityRatio = autosensRatioOfRun
        }
        // One return for both cases; it also tells the compiler that glucose_status is not null below.
        // (A second autosens lookup used to follow here. Its result was never used, and when it found no
        // data it returned the plain profile ISF, without the activity / temp target ratio and unrounded.
        // The run already has its autosens ratio, see autosensRatioOfRun.)
        if (!usingDynamicIsf() || !autoIsfWeights || glucose_status == null) {
            consoleError.add("autoISF weights disabled in Preferences")
            consoleError.add("----------------------------------")
            consoleError.add("end AutoISF")
            consoleError.add("----------------------------------")
            return round(sens / sensitivityRatio, 1)
        }

        val dura05: Double = glucose_status.duraISFminutes
        val avg05: Double = glucose_status.duraISFaverage
        val maxISFReduction: Double = autoISF_max
        var sens_modified = false
        var pp_ISF = 1.0
        var acce_ISF = 1.0
        var acce_weight = 1.0
        val bg_off = target_bg + 10.0 - glucose_status.glucose                      // move from central BG=100 to target+10 as virtual BG'=100

        // calculate acce_ISF from bg acceleration and adapt ISF accordingly
        val fit_corr: Double = glucose_status.corrSqu
        val bg_acce: Double = glucose_status.bgAcceleration
        consoleError.add("Parabola fit results were acceleration:${round(bg_acce, 2)}, correlation:$fit_corr, duration:${glucose_status.parabolaMinutes}m")
        parabolaSourceLine()?.let { consoleError.add(it) }
        if (glucose_status.a2 != 0.0 && fit_corr >= 0.9) {
            var minmax_delta: Double = -glucose_status.a1 / 2 / glucose_status.a2 * 5      // back from 5min block to 1 min
            val minmax_value: Double = round(glucose_status.a0 - minmax_delta * minmax_delta / 25 * glucose_status.a2, 1)
            minmax_delta = round(minmax_delta, 1)
            if (minmax_delta > 0 && bg_acce < 0) {
                consoleError.add("Parabolic fit extrapolates a maximum of ${convert_bg(minmax_value)} in about $minmax_delta minutes")
            } else if (minmax_delta > 0 && bg_acce > 0.0) {

                consoleError.add("Parabolic fit extrapolates a minimum of ${convert_bg(minmax_value)} in about $minmax_delta minutes")
                if (minmax_delta <= 30 && minmax_value < target_bg) {   // start braking
                    acce_weight = -bgBrake_ISF_weight
                    consoleError.add("extrapolation below target soon: use bgBrake_ISF_weight instead")
                }
            }
        }
        if (fit_corr < 0.9) {
            consoleError.add("acce_ISF adaptation by-passed as correlation ${round(fit_corr, 3)} is too low")
        } else {
            val fit_share = 10 * (fit_corr - 0.9)                            // 0 at correlation 0.9, 1 at 1.00
            var cap_weight = 1.0                                             // full contribution above target
            if (acce_weight == 1.0 && glucose_status.glucose < target_bg) {  // below target acce goes towards target
                if (bg_acce > 0) {
                    if (bg_acce > 1) {
                        cap_weight = 0.5
                    }            // halve the effect below target
                    acce_weight = bgBrake_ISF_weight
                } else if (bg_acce < 0) {
                    acce_weight = bgAccel_ISF_weight
                }
            } else if (acce_weight == 1.0) {                                 // above target acce goes away from target
                if (bg_acce < 0.0) {
                    acce_weight = bgBrake_ISF_weight
                } else if (bg_acce > 0.0) {
                    acce_weight = bgAccel_ISF_weight
                }
            }
            acce_ISF = 1.0 + bg_acce * cap_weight * acce_weight * fit_share
            consoleError.add("acce_ISF adaptation is ${round(acce_ISF, 2)}")
            if (acce_ISF != 1.0) {
                sens_modified = true
            }
        }
        autoIsfFactors.acceIsf = acce_ISF

        val bg_ISF = 1 + interpolate(100 - bg_off)
        consoleError.add("bg_ISF adaptation is ${round(bg_ISF, 2)}")
        autoIsfFactors.bgIsf = bg_ISF
        var liftISF: Double
        var final_ISF: Double
        if (bg_ISF < 1.0) {
            liftISF = min(bg_ISF, acce_ISF)
            if (acce_ISF > 1.0) {
                liftISF = bg_ISF * acce_ISF                                 // bg_ISF could become > 1 now
                consoleError.add("bg_ISF adaptation lifted to ${round(liftISF, 2)} as bg accelerates already")
            }
            final_ISF = withinISFlimits(liftISF, autoISF_min, maxISFReduction, sensitivityRatio, exerciseModeActive, resistanceModeActive, stepActivityDetected, stepInactivityDetected)
            return min(720.0, round(sens / final_ISF, 1))         // observe ISF maximum of 720(?)
        } else if (bg_ISF > 1.0) {
            sens_modified = true
        }

        val bg_delta = glucose_status.delta
        val deltaType = "pp"
        when {
            bg_off > 0.0                     -> {
                consoleError.add(deltaType + "_ISF adaptation by-passed as average glucose < $target_bg+10")
            }

            glucose_status.shortAvgDelta < 0 -> {
                consoleError.add(deltaType + "_ISF adaptation by-passed as no rise or too short lived")
            }

            else                             -> {
                pp_ISF = 1.0 + max(0.0, bg_delta * pp_ISF_weight)
                consoleError.add("pp_ISF adaptation is ${round(pp_ISF, 2)}")
                if (pp_ISF != 1.0) {
                    sens_modified = true
                }

            }
        }
        autoIsfFactors.ppIsf = pp_ISF

        var dura_ISF = 1.0
        val weightISF: Double = dura_ISF_weight
        when {
            dura05 < 10.0      -> {
                consoleError.add("dura_ISF by-passed; bg is only $dura05 m at level $avg05")
            }

            avg05 <= target_bg -> {
                consoleError.add("dura_ISF by-passed; avg. glucose $avg05 below target $target_bg")
            }

            else               -> {
                // fight the resistance at high levels
                val dura05Weight = dura05 / 60
                val avg05Weight = weightISF / target_bg
                dura_ISF += dura05Weight * avg05Weight * (avg05 - target_bg)
                sens_modified = true
                consoleError.add("dura_ISF adaptation is ${round(dura_ISF, 2)} because ISF ${round(sens, 1)} did not do it for ${round(dura05, 1)}m")
            }
        }
        autoIsfFactors.duraIsf = dura_ISF
        if (sens_modified) {
            liftISF = max(dura_ISF, max(bg_ISF, max(acce_ISF, pp_ISF)))
            if (acce_ISF < 1.0) {
                consoleError.add("strongest autoISF factor ${round(liftISF, 2)} weakened to ${round(liftISF * acce_ISF, 2)} as bg decelerates already")
                liftISF = liftISF * acce_ISF
            }
            final_ISF = withinISFlimits(liftISF, autoISF_min, maxISFReduction, sensitivityRatio, exerciseModeActive, resistanceModeActive, stepActivityDetected, stepInactivityDetected)
            return round(sens / final_ISF, 1)
        }
        consoleError.add("----------------------------------")
        consoleError.add("end AutoISF")
        consoleError.add("----------------------------------")
        return round(sens / sensitivityRatio, 1)     // nothing changed
    }

    fun interpolate(xdata: Double): Double {   // interpolate ISF behaviour based on polygons defining nonlinear functions defined by value pairs for ...
        //  ...         <----------------------  glucose  ---------------------->
        val polyX = arrayOf(50.0, 60.0, 80.0, 90.0, 100.0, 110.0, 150.0, 180.0, 200.0)
        val polyY = arrayOf(-0.5, -0.5, -0.3, -0.2, 0.0, 0.0, 0.5, 0.7, 0.7)
        val polymax: Int = polyX.size - 1
        var step = polyX[0]
        var sVal = polyY[0]
        var stepT = polyX[polymax]
        var sValold = polyY[polymax]

        var newVal = 1.0
        var lowVal = 1.0
        val topVal: Double
        val lowX: Double
        val topX: Double
        val myX: Double
        var lowLabl = step

        if (step > xdata) {
            // extrapolate backwards
            stepT = polyX[1]
            sValold = polyY[1]
            lowVal = sVal
            topVal = sValold
            lowX = step
            topX = stepT
            myX = xdata
            newVal = lowVal + (topVal - lowVal) / (topX - lowX) * (myX - lowX)
        } else if (stepT < xdata) {
            // extrapolate forwards
            step = polyX[polymax - 1]
            sVal = polyY[polymax - 1]
            lowVal = sVal
            topVal = sValold
            lowX = step
            topX = stepT
            myX = xdata
            newVal = lowVal + (topVal - lowVal) / (topX - lowX) * (myX - lowX)
        } else {
            // interpolate
            for (i: Int in 0..polymax) {
                step = polyX[i]
                sVal = polyY[i]
                if (step == xdata) {
                    newVal = sVal
                    break
                } else if (step > xdata) {
                    topVal = sVal
                    lowX = lowLabl
                    myX = xdata
                    topX = step
                    newVal = lowVal + (topVal - lowVal) / (topX - lowX) * (myX - lowX)
                    break
                }
                lowVal = sVal
                lowLabl = step
            }
        }
        newVal = if (xdata > 100) {
            newVal * higher_ISFrange_weight
        } else {
            newVal * lower_ISFrange_weight
        }
        return newVal
    }

    fun withinISFlimits(
        liftISF: Double, minISFReduction: Double, maxISFReduction: Double, sensitivityRatio: Double,
        exerciseModeActive: Boolean, resistanceModeActive: Boolean, stepActivityDetected: Boolean, stepInactivityDetected: Boolean
    ): Double {
        var liftISFlimited: Double = liftISF
        if (liftISF < minISFReduction) {
            consoleError.add("weakest autoISF factor ${round(liftISF, 2)} limited by autoISF_min $minISFReduction")
            liftISFlimited = minISFReduction
        } else if (liftISF > maxISFReduction) {
            consoleError.add("strongest autoISF factor ${round(liftISF, 2)} limited by autoISF_max $maxISFReduction")
            liftISFlimited = maxISFReduction
        }
        val finalISF: Double
        var originSens = ""
        when {
            exerciseModeActive     -> {
                finalISF = liftISFlimited * sensitivityRatio
                originSens = "including exercise mode impact"
            }
            resistanceModeActive   -> {
                finalISF = liftISFlimited * sensitivityRatio
                originSens = "including resistance mode impact"
            }
            stepActivityDetected   -> {
                finalISF = liftISFlimited * sensitivityRatio
                originSens = "including activity detection impact"
            }
            stepInactivityDetected -> {
                finalISF = liftISFlimited * sensitivityRatio
                originSens = "including inactivity detection impact"
            }
            liftISFlimited >= 1    -> {
                finalISF = max(liftISFlimited, sensitivityRatio)
                if (liftISFlimited < sensitivityRatio) {
                    originSens = "from low TT modifier"
                }
            }
            else                   -> {
                finalISF = min(liftISFlimited, sensitivityRatio)
            }
        }
        consoleError.add("final ISF factor is ${round(finalISF, 2)} " + originSens)
        consoleError.add("----------------------------------")
        consoleError.add("end AutoISF")
        consoleError.add("----------------------------------")
        autoIsfFactors.finalIsf = finalISF
        return finalISF
    }

    fun loop_smb(microBolusAllowed: Boolean, profile: OapsProfileAutoIsf, iob_data_iob: Double, useIobTh: Boolean, iobThEffective: Double): String {
        val iobThUser = preferences.get(IntKey.ApsAutoIsfIobThPercent)
        if (useIobTh) {
            val iobThPercent: Double
            if (profile.max_iob < 0.001) {
                iobThPercent = 0.0
                consoleLog.add("User setting iobTH disabled in LGS mode")
            } else {
                iobThPercent = round(iobThEffective / profile.max_iob * 100.0, 0)
            }
            if (iobThPercent == iobThUser.toDouble()) {
                consoleLog.add("User setting iobTH=$iobThUser% not modulated")
            } else if (iobThPercent > 0.0) {
                consoleLog.add("User setting iobTH=$iobThUser% modulated to ${iobThPercent.toInt()}% or ${round(iobThEffective, 2)}U")
                consoleLog.add("  due to profile %, exercise mode or similar")
            }
        } else {
            consoleLog.add("User setting iobTH=100% disables iobTH method")
        }
        autoIsfFactors.iobThEffective = if (useIobTh) iobThEffective else profile.max_iob

        if (!microBolusAllowed) {
            return "AAPS"                                                 // see message in enable_smb
        }
        if (enableSMB_EvenOn_OddOff_always) {
            //TODO: cleaner conversion back to original mmol/L if applicable
            var target = convert_bg_to_units(profile.target_bg, profile)
            // val msgType: String
            val evenTarget: Boolean
            val msgUnits: String
            val msgTail: String
            if (profile.out_units == "mmol/L") {
                evenTarget = round(target * 10.0, 0).toInt() % 2 == 0
                target = round(target, 1)
                msgUnits = "has"
                msgTail = "decimal"
            } else {
                evenTarget = round(target, 0).toInt() % 2 == 0
                target = round(target, 0)
                msgUnits = "is"
                msgTail = "number"
            }
            val msgEven: String = if (evenTarget) "even" else "odd"

            if (!evenTarget) {
                consoleLog.add("SMB disabled; current target $target $msgUnits $msgEven $msgTail")
                consoleLog.add("Loop allows minimal power")
                return "blocked"
            } else if (profile.max_iob == 0.0) {
                consoleLog.add("SMB disabled because of max_iob=0")
                return "blocked"
            } else if (useIobTh && iobThEffective < iob_data_iob) {
                consoleLog.add("SMB disabled by Full Loop logic: iob $iob_data_iob is above effective iobTH $iobThEffective")
                consoleLog.add("Loop power level temporarily capped")
                return "iobTH"
            } else {
                consoleLog.add("SMB enabled; current target $target $msgUnits $msgEven $msgTail")
                return if (profile.target_bg < 100) {     // indirect assessment; later set it in GUI
                    consoleLog.add("Loop allows maximum power")
                    "fullLoop"                                      // even number
                } else {
                    consoleLog.add("Loop allows medium power")
                    "enforced"                                      // even number
                }
            }
        }
        consoleLog.add("Loop allows AAPS power level")
        return "AAPS"                                                      // leave it to standard AAPS
    }

    fun determine_varSMBratio(bg: Int, target_bg: Double, loop_wanted_smb: String): Double {   // let SMB delivery ratio increase from min to max depending on how much bg exceeds target
        val fix_SMB: Double = smb_delivery_ratio
        val lower_SMB = min(smb_delivery_ratio_min, smb_delivery_ratio_max)
        val higher_SMB = max(smb_delivery_ratio_min, smb_delivery_ratio_max)
        val higher_bg = target_bg + smb_delivery_ratio_bg_range
        var new_SMB: Double = fix_SMB
        if (smb_delivery_ratio_bg_range > 0) {
            new_SMB = lower_SMB + (higher_SMB - lower_SMB) * (bg - target_bg) / smb_delivery_ratio_bg_range
            new_SMB = max(lower_SMB, min(higher_SMB, new_SMB))   // cap if outside target_bg--higher_bg
        }
        if (loop_wanted_smb == "fullLoop") {                                // go for max impact
            consoleLog.add("SMB delivery ratio set to ${round(max(fix_SMB, new_SMB), 2)} as max of fixed and interpolated values")
            return max(fix_SMB, new_SMB)
        }

        if (smb_delivery_ratio_bg_range == 0.0) {                     // deactivated in SMB extended menu
            consoleLog.add("SMB delivery ratio set to fixed value ${round(fix_SMB, 2)}")
            return fix_SMB
        }
        if (bg <= target_bg) {
            consoleLog.add("SMB delivery ratio limited by minimum value ${round(lower_SMB, 2)}")
            return lower_SMB
        }
        if (bg >= higher_bg) {
            consoleLog.add("SMB delivery ratio limited by maximum value ${round(higher_SMB, 2)}")
            return higher_SMB
        }
        consoleLog.add("SMB delivery ratio set to interpolated value ${round(new_SMB, 2)}")
        return new_SMB
    }

    override fun getPreferenceScreenContent() = PreferenceSubScreenDef(
        key = "openapsautoisf_settings",
        title = ApsStrings.openaps_auto_isf,
        items = listOf(
            DoubleKey.ApsMaxBasal,
            DoubleKey.ApsSmbMaxIob,
            BooleanKey.ApsUseAutosens,
            BooleanKey.ApsSensitivityRaisesTarget,
            BooleanKey.ApsResistanceLowersTarget,
            BooleanKey.ApsAutoIsfHighTtRaisesSens,
            BooleanKey.ApsAutoIsfLowTtLowersSens,
            IntKey.ApsAutoIsfHalfBasalExerciseTarget,
            BooleanKey.ApsUseSmb,
            BooleanKey.ApsUseSmbWithHighTt,
            BooleanKey.ApsUseSmbAlways,
            BooleanKey.ApsUseSmbWithCob,
            BooleanKey.ApsUseSmbWithLowTt,
            BooleanKey.ApsUseSmbAfterCarbs,
            BooleanKey.ApsUseUam,
            IntKey.ApsLoopInterval,
            IntKey.ApsMaxSmbFrequency,
            IntKey.ApsMaxMinutesOfBasalToLimitSmb,
            IntKey.ApsUamMaxMinutesOfBasalToLimitSmb,
            IntKey.ApsCarbsRequestThreshold,
            PreferenceSubScreenDef(
                key = "absorption_smb_advanced",
                title = CoreUiStrings.advanced_settings_title,
                items = listOf(
                    ApsIntentKey.LinkToDocs,
                    BooleanKey.ApsAlwaysUseShortDeltas,
                    DoubleKey.ApsMaxDailyMultiplier,
                    DoubleKey.ApsMaxCurrentBasalMultiplier
                )
            ),
            PreferenceSubScreenDef(
                key = "auto_isf_settings",
                title = ApsStrings.autoISF_settings_title,
                items = listOf(
                    BooleanKey.ApsUseAutoIsfWeights,
                    DoubleKey.ApsAutoIsfMin,
                    DoubleKey.ApsAutoIsfMax,
                    DoubleKey.ApsAutoIsfBgAccelWeight,
                    DoubleKey.ApsAutoIsfBgBrakeWeight,
                    BooleanKey.ApsAutoIsfParabolaSmoothed,
                    IntKey.ApsAutoIsfParabolaMinMinutes,
                    DoubleKey.ApsAutoIsfLowBgWeight,
                    DoubleKey.ApsAutoIsfHighBgWeight,
                    DoubleKey.ApsAutoIsfPpWeight,
                    DoubleKey.ApsAutoIsfDuraWeight,
                    IntKey.ApsAutoIsfIobThPercent
                )
            ),
            // AutoISF fork: the same CGM correction settings as in the xDrip source, shown here too so
            // everything is in one place. They always apply, whatever APS algorithm is active.
            PreferenceSubScreenDef(
                key = "autoisf_cgm_correction",
                title = KeysStrings.pref_title_bg_correction,
                items = listOf(
                    DoubleKey.BgSourceCorrectionSlope,
                    DoubleKey.BgSourceCorrectionOffset
                )
            ),
            PreferenceSubScreenDef(
                key = "smb_delivery_settings",
                title = ApsStrings.smb_delivery_settings_title,
                items = listOf(
                    DoubleKey.ApsAutoIsfSmbDeliveryRatio,
                    DoubleKey.ApsAutoIsfSmbDeliveryRatioMin,
                    DoubleKey.ApsAutoIsfSmbDeliveryRatioMax,
                    DoubleKey.ApsAutoIsfSmbDeliveryRatioBgRange,
                    DoubleKey.ApsAutoIsfSmbMaxRangeExtension,
                    BooleanKey.ApsAutoIsfSmbOnEvenTarget
                )
            ),
            PreferenceSubScreenDef(
                key = "activity_monitor",
                title = KeysStrings.pref_title_aps_activity_detection,
                items = listOf(
                    BooleanKey.ActivityMonitorDetection,
                    IntKey.ActivityMonitorStepSource,
                    DoubleKey.ActivityScaleFactor,
                    DoubleKey.InactivityScaleFactor,
                    BooleanKey.ActivityMonitorOvernight,
                    IntKey.ActivityMonitorIdleStart,
                    IntKey.ActivityMonitorIdleEnd,
                    BooleanKey.ActivityMonitorShowStepsFromSmartphone
                )
            )
        ),
        icon = pluginDescription.icon
    )

}
