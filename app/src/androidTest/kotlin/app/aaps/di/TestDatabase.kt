package app.aaps.di

/**
 * The database file of the next test graph, or null for the usual in-memory database.
 *
 * A test that restarts AAPS (a new graph over the same pump) sets a file before its first graph is built, so the
 * insulin AAPS has on record lives through the restart, as on a phone. Read by [BaseTestApp] when it builds a graph;
 * the test sets it back to null at its end.
 */
object TestDatabase {

    @Volatile var file: String? = null
}
