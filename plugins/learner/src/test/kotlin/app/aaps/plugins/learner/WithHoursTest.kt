package app.aaps.plugins.learner

import app.aaps.core.data.model.data.Block
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class WithHoursTest {

    private val h = 3_600_000L

    @Test
    fun `only the changed hours change, half hours elsewhere stay`() {
        // 00:00-00:30 0.5, 00:30-03:00 0.3, 03:00-24:00 0.2
        val blocks = listOf(Block(h / 2, 0.5), Block(h * 5 / 2, 0.3), Block(21 * h, 0.2))
        val out = withHours(blocks, mapOf(4 to 0.25))
        assertThat(out.first()).isEqualTo(Block(h / 2, 0.5))
        assertThat(out.sumOf { it.duration }).isEqualTo(24 * h)
        assertThat(out).isEqualTo(listOf(Block(h / 2, 0.5), Block(h * 5 / 2, 0.3), Block(h, 0.2), Block(h, 0.25), Block(19 * h, 0.2)))
    }

    @Test
    fun `nothing to change gives the same blocks`() {
        val blocks = listOf(Block(h / 2, 0.5), Block(h * 47 / 2, 0.3))
        assertThat(withHours(blocks, emptyMap())).isEqualTo(blocks)
    }
}
