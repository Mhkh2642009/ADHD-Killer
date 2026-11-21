package com.adhdkiller.app.enforcement.shortvideo

import com.adhdkiller.app.data.db.entity.BlockMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The migration only fires when a stored rule is byte-for-byte identical to the rule in
 * [ShortFormMatcher.legacyDefaults]. If that list drifts from what version 1 actually seeded,
 * the migration silently does nothing — and the stale rule survives every upgrade.
 *
 * That is not hypothetical: the legacy list once claimed a YouTube rule with `reel_player`
 * keywords, while version 1 had actually seeded `Shorts_vewier,shorts_vewier,shorts` with no
 * exclusion keywords. The broad `shorts` keyword then kept matching the YouTube home feed —
 * the one thing the product promises never to block.
 *
 * This test pins the version 1 seed literally, so the list cannot drift again.
 */
class LegacySeedTest {

    @Test
    fun `the legacy list is the rule set version 1 actually seeded`() {
        val legacy = ShortFormMatcher.legacyDefaults().associateBy { it.packageName }

        val youtube = legacy.getValue("com.google.android.youtube")
        assertEquals(BlockMode.ACTIVITY, youtube.mode)
        assertEquals(
            listOf("com.google.android.youtube.shorts", "com.google.android.youtube.short"),
            youtube.activityPatterns,
        )
        assertEquals(listOf("Shorts_vewier", "shorts_vewier", "shorts"), youtube.keywords)
        assertEquals(emptyList<String>(), youtube.excludeKeywords)
        assertEquals(false, youtube.strict)

        val instagram = legacy.getValue("com.instagram.android")
        assertEquals(BlockMode.ACTIVITY, instagram.mode)
        assertEquals(listOf("com.instagram.ui.reels", "ReelsActivity"), instagram.activityPatterns)
        assertEquals(listOf("reels_viewer", "reel_viewer", "Reels"), instagram.keywords)
        assertEquals(
            listOf("reels_tab", "tab_reels", "bottom_navigation", "navigation_bar"),
            instagram.excludeKeywords,
        )
        assertEquals(false, instagram.strict)

        val facebook = legacy.getValue("com.facebook.katana")
        assertEquals(BlockMode.WHOLE_APP, facebook.mode)
        assertEquals(listOf("reels_viewer", "reel_viewer"), facebook.keywords)
        assertEquals(
            listOf("reels_tab", "tab_reels", "bottom_navigation", "navigation_bar"),
            facebook.excludeKeywords,
        )
        assertEquals(true, facebook.strict)

        listOf(
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.aweme",
            "com.snapchat.android",
        ).forEach { pkg ->
            val rule = legacy.getValue(pkg)
            assertEquals(BlockMode.WHOLE_APP, rule.mode)
            assertEquals(emptyList<String>(), rule.activityPatterns)
            assertEquals(emptyList<String>(), rule.keywords)
            assertEquals(emptyList<String>(), rule.excludeKeywords)
            assertEquals(true, rule.strict)
        }
    }

    @Test
    fun `the legacy list covers exactly the six shipped apps`() {
        assertEquals(
            setOf(
                "com.google.android.youtube",
                "com.instagram.android",
                "com.zhiliaoapp.musically",
                "com.ss.android.ugc.aweme",
                "com.snapchat.android",
                "com.facebook.katana",
            ),
            ShortFormMatcher.legacyDefaults().map { it.packageName }.toSet(),
        )
    }

    @Test
    fun `a rule seeded by version 1 is actually migrated to the current defaults`() {
        // The whole point of the legacy list: a rule that version 1 wrote has to be recognised.
        val stored = ShortFormMatcher.legacyDefaults().map {
            ShortFormMatcher.toEntity(it, enabled = true)
        }
        val migrations = ShortFormMatcher.planMigration(
            current = ShortFormMatcher.defaults(),
            legacy = ShortFormMatcher.legacyDefaults(),
            stored = stored,
        )

        val migrated = migrations.associateBy { it.packageName }
        // The YouTube rule loses its broad keywords, so the home feed can no longer be caught.
        val youtubeKeywords = migrated.getValue("com.google.android.youtube").keywords
            .split(',').filter { it.isNotBlank() }
        assertFalse(
            "the broad 'shorts' keyword must never come back",
            youtubeKeywords.any { it.contains("short", ignoreCase = true) },
        )
        assertTrue(
            "the new YouTube app is detected by its reel ids",
            youtubeKeywords.any { it.contains("reel", ignoreCase = true) },
        )
        // Facebook is no longer a whole-app block.
        assertEquals(BlockMode.KEYWORD, migrated.getValue("com.facebook.katana").mode)
        assertEquals(false, migrated.getValue("com.facebook.katana").strict)
        // The whole-app apps are already current, so they are left alone.
        assertEquals(false, migrated.containsKey("com.zhiliaoapp.musically"))
        assertEquals(false, migrated.containsKey("com.snapchat.android"))
    }

    @Test
    fun `the current youtube rule cannot catch the home feed`() {
        // The new YouTube app shows the home feed and the Shorts player in the same activity.
        // The rule may only use ids that exist on the Shorts surface — the home feed has
        // `inline_player_*` and a single `reel_time_bar`, but never these two.
        val rule = ShortFormMatcher.defaults().first { it.packageName == "com.google.android.youtube" }
        val homeFeedHints = listOf(
            "com.google.android.youtube:id/inline_player_container",
            "com.google.android.youtube:id/inline_player_overlay_container",
            "com.google.android.youtube:id/reel_time_bar",
            "com.google.android.youtube:id/tab_shorts",
        )
        assertNull(
            ShortFormMatcher.match(
                rule = rule,
                packageName = "com.google.android.youtube",
                activityName = "com.google.android.youtube.app.honeycomb.Shell\$HomeActivity",
                nodeHints = homeFeedHints,
            )
        )

        val shortsHints = listOf(
            "com.google.android.youtube:id/reel_watch_fragment_root",
            "com.google.android.youtube:id/reel_player_overlay_container",
        )
        assertNotNull(
            ShortFormMatcher.match(
                rule = rule,
                packageName = "com.google.android.youtube",
                activityName = "com.google.android.youtube.app.honeycomb.Shell\$HomeActivity",
                nodeHints = shortsHints,
            )
        )
    }
}
