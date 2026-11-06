package app.aaps.plugins.aps.openAPSAIMI

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.plugins.aps.openAPSAIMI.autodrive.estimator.ContinuousStateEstimator
import app.aaps.plugins.aps.openAPSAIMI.autodrive.models.AutoDriveState
import app.aaps.plugins.aps.openAPSAIMI.safety.InsulinLoadGovernor
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.math.max

/**
 * The limits that depend on the weight, for a child: the real case (25 kg, TDD about 17 U) and the
 * whole range from 20 kg and from 10 U a day. Each limit must follow the real weight and TDD, never
 * be larger than before, grow with weight and TDD, and stay as it was for an adult.
 */
class ChildWeightLimitsTest {

    private val logger = mockk<AAPSLogger>(relaxed = true)

    // ---- Autodrive weight -------------------------------------------------------------------------

    @Test
    fun `Autodrive keeps a child weight of 25 kg instead of 40 kg`() {
        assertThat(state(weightKg = 25.0).patientWeightKg).isEqualTo(25.0)
    }

    @Test
    fun `Autodrive weight stays inside 20 to 250 kg`() {
        assertThat(state(weightKg = 10.0).patientWeightKg).isEqualTo(AutoDriveState.MIN_PATIENT_WEIGHT_KG)
        assertThat(state(weightKg = 300.0).patientWeightKg).isEqualTo(AutoDriveState.MAX_PATIENT_WEIGHT_KG)
        assertThat(state(weightKg = 75.0).patientWeightKg).isEqualTo(75.0)
    }

    @Test
    fun `a 25 kg child is not turned into the 70 kg fallback state`() {
        // The constructor check and createSafe use the same bounds: a child weight must not throw and
        // fall back to the default state, which has 70 kg.
        val child = state(weightKg = 25.0)
        assertThat(child.bg).isEqualTo(160.0)
        assertThat(child.patientWeightKg).isEqualTo(25.0)
    }

    @Test
    fun `carb appearance of a 25 kg child is not above the one of 40 kg`() {
        val childRa = ContinuousStateEstimator(logger).apply { repeat(12) { updateAndPredict(risingState(25.0)) } }.getLastRa()
        val floorRa = ContinuousStateEstimator(logger).apply { repeat(12) { updateAndPredict(risingState(40.0)) } }.getLastRa()
        assertThat(childRa).isAtMost(floorRa)
    }

    // ---- IOB budget of the adaptive basal brake ---------------------------------------------------

    @Test
    fun `IOB budget of a 25 kg child with TDD 17 U follows the child, not the 4 U floor`() {
        val budget = InsulinLoadGovernor.physiologicalBudgetU(tdd24hU = 17.0, patientWeightKg = 25.0)
        assertThat(budget).isWithin(0.01).of(2.67) // 25 kg × 8 U / 75 kg; TDD gives 2.47
        assertThat(budget).isLessThan(4.0)
    }

    @Test
    fun `IOB budget without a TDD uses the weight, not the adult 8 U`() {
        assertThat(InsulinLoadGovernor.physiologicalBudgetU(tdd24hU = 0.0, patientWeightKg = 25.0)).isWithin(0.01).of(2.67)
        assertThat(InsulinLoadGovernor.physiologicalBudgetU(tdd24hU = Double.NaN, patientWeightKg = 75.0)).isWithin(0.01).of(8.0)
        assertThat(InsulinLoadGovernor.physiologicalBudgetU(tdd24hU = Double.NaN, patientWeightKg = Double.NaN)).isWithin(0.01).of(8.0)
    }

    @Test
    fun `IOB budget never goes below 1 U`() {
        assertThat(InsulinLoadGovernor.physiologicalBudgetU(tdd24hU = 2.0, patientWeightKg = 5.0))
            .isEqualTo(InsulinLoadGovernor.PHYS_BUDGET_MIN_U)
    }

    @Test
    fun `IOB budget of an adult is unchanged`() {
        assertThat(InsulinLoadGovernor.physiologicalBudgetU(tdd24hU = 55.0, patientWeightKg = 50.0)).isWithin(0.01).of(8.0)
        assertThat(InsulinLoadGovernor.physiologicalBudgetU(tdd24hU = 30.0, patientWeightKg = 75.0)).isWithin(0.01).of(8.0)
        assertThat(InsulinLoadGovernor.physiologicalBudgetU(tdd24hU = 110.0, patientWeightKg = 75.0)).isWithin(0.01).of(16.0)
    }

    @Test
    fun `a lighter budget brakes the boost sooner`() {
        val childBudget = InsulinLoadGovernor.physiologicalBudgetU(tdd24hU = 17.0, patientWeightKg = 25.0)
        val child = InsulinLoadGovernor.iobBudgetBrakedMultiplier(1.5, iobU = 2.5, budgetU = childBudget)
        val oldFloor = InsulinLoadGovernor.iobBudgetBrakedMultiplier(1.5, iobU = 2.5, budgetU = 4.0)
        assertThat(child).isLessThan(oldFloor)
    }

    // ---- Tight spiral SMB cap ---------------------------------------------------------------------

    @Test
    fun `spiral IOB threshold of a 25 kg child with TDD 17 U is below the old 3 U`() {
        assertThat(tightSpiralSmbCapIobThresholdU(tdd24hU = 17.0, patientWeightKg = 25.0)).isWithin(0.01).of(2.67)
    }

    @Test
    fun `spiral energy threshold for TDD 17 U is below the old 3 U`() {
        assertThat(tightSpiralSmbCapEnergyThresholdU(tdd24hU = 17.0)).isWithin(0.01).of(2.16) // 17 × 7 / 55
    }

    @Test
    fun `spiral thresholds stay inside their bounds and are unchanged for an adult`() {
        assertThat(tightSpiralSmbCapIobThresholdU(tdd24hU = 2.0, patientWeightKg = 5.0)).isWithin(0.001).of(1.0)
        assertThat(tightSpiralSmbCapIobThresholdU(tdd24hU = 200.0, patientWeightKg = 150.0)).isWithin(0.001).of(20.0)
        assertThat(tightSpiralSmbCapIobThresholdU(tdd24hU = 55.0, patientWeightKg = 75.0)).isWithin(0.01).of(8.0)
        assertThat(tightSpiralSmbCapEnergyThresholdU(tdd24hU = 55.0)).isWithin(0.01).of(7.0)
        assertThat(tightSpiralSmbCapEnergyThresholdU(tdd24hU = 1.0)).isWithin(0.001).of(1.0)
    }

    // ---- the whole range: weight from 20 kg, TDD from 10 U --------------------------------------------

    @Test
    fun `for every child weight and TDD the new limits follow them and are never above the old ones`() {
        for (weight in WEIGHTS_KG) for (tdd in TDDS_U) {
            val case = "weight $weight kg, TDD $tdd U"

            val budget = InsulinLoadGovernor.physiologicalBudgetU(tdd, weight)
            assertWithMessage(case).that(budget).isAtMost(oldBudgetU(tdd, weight) + EPS)
            assertWithMessage(case).that(budget).isWithin(0.001).of(max(tdd * 8.0 / 55.0, weight * 8.0 / 75.0).coerceAtLeast(1.0))

            val iobTh = tightSpiralSmbCapIobThresholdU(tdd, weight)
            assertWithMessage(case).that(iobTh).isAtMost(oldSpiralIobU(tdd, weight) + EPS)
            assertWithMessage(case).that(iobTh).isAtLeast(1.0)

            val energyTh = tightSpiralSmbCapEnergyThresholdU(tdd)
            assertWithMessage(case).that(energyTh).isAtMost((tdd * 7.0 / 55.0).coerceIn(3.0, 18.0) + EPS)
            assertWithMessage(case).that(energyTh).isAtLeast(1.0)

            // Autodrive keeps the real weight, so its dose cap (weight × U/kg) is never the 40 kg one.
            assertWithMessage(case).that(state(weight).patientWeightKg).isEqualTo(weight)
        }
    }

    @Test
    fun `the limits grow with weight and TDD, never the other way`() {
        for (tdd in TDDS_U) WEIGHTS_KG.zipWithNext().forEach { (light, heavy) ->
            assertWithMessage("TDD $tdd U, $light → $heavy kg").that(InsulinLoadGovernor.physiologicalBudgetU(tdd, light))
                .isAtMost(InsulinLoadGovernor.physiologicalBudgetU(tdd, heavy))
            assertWithMessage("TDD $tdd U, $light → $heavy kg").that(tightSpiralSmbCapIobThresholdU(tdd, light))
                .isAtMost(tightSpiralSmbCapIobThresholdU(tdd, heavy))
        }
        for (weight in WEIGHTS_KG) TDDS_U.zipWithNext().forEach { (low, high) ->
            assertWithMessage("$weight kg, TDD $low → $high U").that(InsulinLoadGovernor.physiologicalBudgetU(low, weight))
                .isAtMost(InsulinLoadGovernor.physiologicalBudgetU(high, weight))
            assertWithMessage("$weight kg, TDD $low → $high U").that(tightSpiralSmbCapIobThresholdU(low, weight))
                .isAtMost(tightSpiralSmbCapIobThresholdU(high, weight))
        }
        TDDS_U.zipWithNext().forEach { (low, high) ->
            assertWithMessage("TDD $low → $high U").that(tightSpiralSmbCapEnergyThresholdU(low))
                .isAtMost(tightSpiralSmbCapEnergyThresholdU(high))
        }
    }

    @Test
    fun `carb appearance never grows when the weight goes down`() {
        val ra = WEIGHTS_KG.map { w ->
            ContinuousStateEstimator(logger).apply { repeat(12) { updateAndPredict(risingState(w)) } }.getLastRa()
        }
        ra.zipWithNext().forEachIndexed { i, (light, heavy) ->
            assertWithMessage("${WEIGHTS_KG[i]} → ${WEIGHTS_KG[i + 1]} kg").that(light).isAtMost(heavy)
        }
    }

    // ---- helpers ----------------------------------------------------------------------------------

    /** The formulas before this change: IOB budget at least 4 U, spiral IOB at least 3 U, 8 U without a TDD. */
    private fun oldBudgetU(tdd: Double, weight: Double) = max(tdd * 8.0 / 55.0, weight * 8.0 / 75.0).coerceAtLeast(4.0)
    private fun oldSpiralIobU(tdd: Double, weight: Double) = max(tdd * 8.0 / 55.0, weight * 8.0 / 75.0).coerceIn(3.0, 20.0)

    private fun state(weightKg: Double) = AutoDriveState.createSafe(
        bg = 160.0,
        bgVelocity = 2.0,
        iob = 0.5,
        estimatedSI = 0.005,
        patientWeightKg = weightKg,
    )

    private fun risingState(weightKg: Double) = AutoDriveState.createSafe(
        bg = 118.0,
        bgVelocity = 8.0,
        iob = 0.6,
        cob = 0.0,
        estimatedSI = 0.005,
        patientWeightKg = weightKg,
        physiologicalStressMask = doubleArrayOf(0.0, 0.0, 0.0),
        hour = 6,
        steps = 0,
        combinedDelta = 6.0,
        uamConfidence = 0.8,
    )

    private companion object {

        /** From 20 kg (the lowest weight now) to an adult. */
        val WEIGHTS_KG = listOf(20.0, 22.5, 25.0, 30.0, 35.0, 40.0, 50.0, 75.0)

        /** Rounding: the code and the test compute `w × (8 / 75)` and `w × 8 / 75`, a last-digit difference. */
        const val EPS = 1e-9

        /** From 10 U a day up. */
        val TDDS_U = listOf(10.0, 12.0, 15.0, 17.0, 20.0, 25.0, 30.0, 40.0, 55.0)
    }
}
