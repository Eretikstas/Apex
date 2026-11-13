package app.aaps.plugins.main.skins

/** Selects the home renderer without coupling the Compose shell to a concrete skin class. */
enum class DashboardHomeVariant {
    OVERVIEW,
    DASHBOARD_V1,
    GLASS,

    /** The Ring skin: ring, "next hour" tile and plain "why" (docs/RING_SKIN.md). */
    RING,
}
