package app.aaps.core.interfaces.aps

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * A short note for the "why" of the overview, written by a plugin that is not the loop (for example the learner).
 * [text] is already in the user's language. The note is shown until [validUntil].
 */
data class LoopNote(val source: String, val text: String, val time: Long, val validUntil: Long)

/**
 * Notes from plugins for the "why" of the overview. One note per [LoopNote.source]: a new note of the same source
 * replaces the old one. Only shows text; nothing here changes what the loop does.
 */
class LoopNotes {

    private val _notes = MutableStateFlow<Map<String, LoopNote>>(emptyMap())
    val notes: StateFlow<Map<String, LoopNote>> = _notes.asStateFlow()

    fun put(note: LoopNote) = _notes.update { it + (note.source to note) }

    fun clear(source: String) = _notes.update { it - source }

    /** The notes still valid at [now], newest first. */
    fun valid(now: Long): List<LoopNote> = _notes.value.values.filter { it.validUntil > now }.sortedByDescending { it.time }
}
