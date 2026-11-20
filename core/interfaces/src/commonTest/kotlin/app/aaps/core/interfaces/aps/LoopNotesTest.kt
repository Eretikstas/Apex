package app.aaps.core.interfaces.aps

import kotlin.test.Test
import kotlin.test.assertEquals

class LoopNotesTest {

    @Test
    fun oneNotePerSourceAndOldNotesHidden() {
        val notes = LoopNotes()
        notes.put(LoopNote("learner", "a", time = 100, validUntil = 1_000))
        notes.put(LoopNote("learner", "b", time = 200, validUntil = 1_000))
        notes.put(LoopNote("guard", "c", time = 300, validUntil = 500))
        assertEquals(listOf("c", "b"), notes.valid(400).map { it.text })
        assertEquals(listOf("b"), notes.valid(600).map { it.text })
        notes.clear("learner")
        assertEquals(emptyList(), notes.valid(400).map { it.text }.filter { it != "c" })
    }
}
