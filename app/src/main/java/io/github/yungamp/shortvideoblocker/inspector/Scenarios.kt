package io.github.yungamp.shortvideoblocker.inspector

object TargetApps {
    const val YOUTUBE = "com.google.android.youtube"
    const val INSTAGRAM = "com.instagram.android"
    val all = listOf(YOUTUBE, INSTAGRAM)

    fun label(pkg: String): String = when (pkg) {
        YOUTUBE -> "YouTube"
        INSTAGRAM -> "Instagram"
        else -> pkg
    }
}

/** A screen state we want a dump of, so that phase 1/2 rules can be derived from it. */
data class Scenario(val id: String, val pkg: String, val label: String, val hint: String)

object Scenarios {
    const val OTHER_SUFFIX = "other"

    val all: List<Scenario> = listOf(
        Scenario("yt_home", TargetApps.YOUTUBE, "Startseite", "Startseite, so gescrollt, dass der Shorts-Bereich zu sehen ist"),
        Scenario("yt_shorts_tab", TargetApps.YOUTUBE, "Shorts über Tab", "Unten auf „Shorts“ getippt, ein Short läuft"),
        Scenario("yt_short_from_home", TargetApps.YOUTUBE, "Short aus Startseite", "Ein Short aus dem Shorts-Bereich der Startseite geöffnet"),
        Scenario("yt_short_swiped", TargetApps.YOUTUBE, "Short nach Wischen", "Im Shorts-Player einmal zum nächsten Short gewischt"),
        Scenario("yt_short_external", TargetApps.YOUTUBE, "Short über Link", "Short-Link aus einer anderen App (z. B. WhatsApp) geöffnet"),
        Scenario("yt_video", TargetApps.YOUTUBE, "Normales Video", "Ein normales Video im Vollbild-Player (nicht minimiert)"),
        Scenario("yt_video_mini", TargetApps.YOUTUBE, "Minimierter Player", "Normales Video minimiert, Startseite sichtbar"),
        Scenario("yt_search", TargetApps.YOUTUBE, "Suchergebnisse", "Suchergebnisse, in denen auch Shorts auftauchen"),
        Scenario("yt_subscriptions", TargetApps.YOUTUBE, "Abos", "Abos-Tab"),

        Scenario("ig_feed", TargetApps.INSTAGRAM, "Feed", "Startseite/Feed, möglichst mit einem Reel im Feed"),
        Scenario("ig_reels_tab", TargetApps.INSTAGRAM, "Reels-Tab", "Unten auf den Reels-Tab getippt, ein Reel läuft"),
        Scenario("ig_reel_from_feed", TargetApps.INSTAGRAM, "Reel aus Feed", "Ein Reel im Feed angetippt, Vollbild-Viewer offen"),
        Scenario("ig_reel_swiped", TargetApps.INSTAGRAM, "Reel nach Wischen", "Im Reels-Viewer einmal zum nächsten Reel gewischt"),
        Scenario("ig_dm_list", TargetApps.INSTAGRAM, "DM-Übersicht", "Liste der Direktnachrichten"),
        Scenario("ig_dm_thread", TargetApps.INSTAGRAM, "DM-Chat mit Reel", "Ein Chat, in dem ein Reel geteilt wurde"),
        Scenario("ig_reel_from_dm", TargetApps.INSTAGRAM, "Reel aus DM", "Das geteilte Reel aus dem Chat geöffnet"),
        Scenario("ig_reel_from_dm_swiped", TargetApps.INSTAGRAM, "Reel aus DM + Wischen", "Im aus der DM geöffneten Reel weitergewischt (falls möglich)"),
        Scenario("ig_reel_external", TargetApps.INSTAGRAM, "Reel über Link", "Reel-Link aus einer anderen App geöffnet"),
        Scenario("ig_explore", TargetApps.INSTAGRAM, "Suche/Explore", "Such-/Explore-Seite"),
        Scenario("ig_profile_reels", TargetApps.INSTAGRAM, "Profil → Reels", "Reels-Raster auf einem Profil"),
        Scenario("ig_story", TargetApps.INSTAGRAM, "Story", "Eine Story wird angesehen (darf NICHT blockiert werden)"),
    )

    fun forPackage(pkg: String): List<Scenario> = all.filter { it.pkg == pkg }

    fun other(pkg: String): Scenario {
        val prefix = if (pkg == TargetApps.YOUTUBE) "yt" else if (pkg == TargetApps.INSTAGRAM) "ig" else "app"
        return Scenario("${prefix}_$OTHER_SUFFIX", pkg, "Sonstiges", "Beliebiger anderer Bildschirm")
    }

    fun byId(id: String): Scenario? = all.firstOrNull { it.id == id }
}
