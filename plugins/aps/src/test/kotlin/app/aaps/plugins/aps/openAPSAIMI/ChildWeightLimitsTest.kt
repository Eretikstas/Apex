package app.aaps.plugins.aps.openAPSAIMI

import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.plugins.aps.openAPSAIMI.autodrive.estimator.ContinuousStateEstimator
import app.aaps.plugins.aps.openAPSAIMI.autodrive.models.AutoDriveState
import app.aaps.plugins.aps.openAPSAIMI.safety.InsulinLoadGovernor
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import org.junit.jupiter.api.Test

/**
 * The limits that depend on the weight, for a child: 25 kg and a TDD of about 17 U (the real case),
 * and lighter ones. Each limit must follow the real weight and TDD and never be larger than before,
 * and for an adult it must stay as it was.
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

    // ---- helpers ----------------------------------------------------------------------------------

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
}
