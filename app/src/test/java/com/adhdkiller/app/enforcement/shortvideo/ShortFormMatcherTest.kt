package com.adhdkiller.app.enforcement.shortvideo

import com.adhdkiller.app.data.db.entity.BlockMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The rule engine decides what counts as a short-form feed. The most important property is
 * that long-form YouTube is never touched.
 */
class ShortFormMatcherTest {

    private fun youtubeRule() = ShortFormMatcher.defaults().first { it.packageName == "com.google.android.youtube" }

    @Test
    fun `youtube shorts activity is blocked`() {
        val match = ShortFormMatcher.match(
            rule = youtubeRule(),
            packageName = "com.google.android.youtube",
            activityName = "com.google.android.youtube.shorts.ShortsPlayerActivity",
            nodeHints = emptyList(),
        )
        assertNotNull(match)
    }

    @Test
    fun `long form youtube is not blocked`() {
        val match = ShortFormMatcher.match(
            rule = youtubeRule(),
            packageName = "com.google.android.youtube",
            activityName = "com.google.android.youtube.common.YouTubeActivity",
            nodeHints = listOf("com.google.android.youtube:id/reel_player"),
        )
        assertNull(match)
    }

    @Test
    fun `the youtube home feed is never blocked even when a reels node is present`() {
        // The home feed shows a Shorts shelf. The rule must not fire on the home screen.
        val match = ShortFormMatcher.match(
            rule = youtubeRule(),
            packageName = "com.google.android.youtube",
            activityName = "com.google.android.youtube.common.YouTubeActivity",
            nodeHints = listOf(
                "com.google.android.youtube:id/tab_shorts",
                "com.google.android.youtube:id/reel_item",
            ),
        )
        assertNull(match)
    }

    @Test
    fun `the shorts tab in the navigation bar does not trigger a block`() {
        // The bottom navigation mentions "Shorts" but the player is not open.
        val match = ShortFormMatcher.match(
            rule = youtubeRule(),
            packageName = "com.google.android.youtube",
            activityName = "com.google.android.youtube.common.YouTubeActivity",
            nodeHints = listOf("com.google.android.youtube:id/tab_shorts"),
        )
        assertNull(match)
    }

    @Test
    fun `instagram reels are detected by view id`() {
        val instagram = ShortFormMatcher.defaults().first { it.packageName == "com.instagram.android" }
        val match = ShortFormMatcher.match(
            rule = instagram,
            packageName = "com.instagram.android",
            activityName = "com.instagram.mainactivity.MainActivity",
            nodeHints = listOf("com.instagram.android:id/reels_viewer"),
        )
        assertNotNull(match)
    }

    @Test
    fun `instagram feed is not blocked by the reels tab in the navigation bar`() {
        val instagram = ShortFormMatcher.defaults().first { it.packageName == "com.instagram.android" }
        val match = ShortFormMatcher.match(
            rule = instagram,
            packageName = "com.instagram.android",
            activityName = "com.instagram.mainactivity.MainActivity",
            nodeHints = listOf("com.instagram.android:id/bottom_navigation", "com.instagram.android:id/reels_tab"),
        )
        assertNull(match)
    }

    @Test
    fun `rules never apply to a different package`() {
        val match = ShortFormMatcher.match(
            rule = youtubeRule(),
            packageName = "com.example.other",
            activityName = "com.google.android.youtube.shorts.ShortsPlayerActivity",
            nodeHints = emptyList(),
        )
        assertNull(match)
    }

    @Test
    fun `whole app rules block every surface`() {
        val tiktok = ShortFormMatcher.defaults().first { it.packageName == "com.zhiliaoapp.musically" }
        val match = ShortFormMatcher.match(
            rule = tiktok,
            packageName = "com.zhiliaoapp.musically",
            activityName = "com.ss.android.ugc.aweme.feed.G2JFeedShareActivity",
            nodeHints = emptyList(),
        )
        assertNotNull(match)
    }

    @Test
    fun `exclusion keywords veto a keyword match`() {
        val rule = ShortFormMatcher.Rule(
            packageName = "com.example.app",
            appLabel = "Example",
            mode = BlockMode.KEYWORD,
            activityPatterns = emptyList(),
            keywords = listOf("reels_viewer"),
            excludeKeywords = listOf("bottom_navigation"),
            strict = false,
        )
        assertNull(
            ShortFormMatcher.match(
                rule = rule,
                packageName = "com.example.app",
                activityName = "MainActivity",
                nodeHints = listOf("com.example.app:id/bottom_navigation", "com.example.app:id/reels_viewer"),
            )
        )
        assertNotNull(
            ShortFormMatcher.match(
                rule = rule,
                packageName = "com.example.app",
                activityName = "MainActivity",
                nodeHints = listOf("com.example.app:id/reels_viewer"),
            )
        )
    }

    @Test
    fun `entity round trip keeps the rule intact`() {
        val original = ShortFormMatcher.defaults().first { it.packageName == "com.instagram.android" }
        val restored = ShortFormMatcher.fromEntity(ShortFormMatcher.toEntity(original))
        assertEquals(original.packageName, restored.packageName)
        assertEquals(original.mode, restored.mode)
        assertEquals(original.activityPatterns, restored.activityPatterns)
        assertEquals(original.keywords, restored.keywords)
        assertEquals(original.excludeKeywords, restored.excludeKeywords)
        assertEquals(original.strict, restored.strict)
    }
}
