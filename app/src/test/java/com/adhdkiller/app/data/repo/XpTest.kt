package com.adhdkiller.app.data.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The XP curve drives the level, the accent colour and the reward gallery. It has to be
 * monotonic and it has to reward early progress more than late progress.
 */
class XpTest {

    @Test
    fun `level one is the starting point`() {
        assertEquals(1, Xp.levelFor(0))
        assertEquals(1, Xp.levelFor(99))
    }

    @Test
    fun `levels increase with xp`() {
        assertEquals(2, Xp.levelFor(100))
        assertEquals(3, Xp.levelFor(400))
        assertEquals(4, Xp.levelFor(900))
    }

    @Test
    fun `negative xp never drops below level one`() {
        assertEquals(1, Xp.levelFor(-500))
    }

    @Test
    fun `level thresholds are consistent with the curve`() {
        for (level in 1..12) {
            val threshold = Xp.xpForLevel(level)
            assertEquals("level $level at $threshold xp", level, Xp.levelFor(threshold))
        }
    }

    @Test
    fun `progress inside a level stays between zero and one`() {
        assertEquals(0f, Xp.levelProgress(0), 0.0001f)
        assertEquals(0.5f, Xp.levelProgress(250), 0.0001f)
        assertTrue(Xp.levelProgress(100) >= 0f)
        assertTrue(Xp.levelProgress(100) <= 1f)
    }

    @Test
    fun `progress is zero at the start of a level`() {
        assertEquals(0f, Xp.levelProgress(Xp.xpForLevel(3)), 0.0001f)
    }

    @Test
    fun `completing a task is worth ten xp`() {
        assertEquals(10, Xp.TASK)
        assertEquals(25, Xp.LOCKDOWN_COMPLETED)
        assertTrue(Xp.LOCKDOWN_EARLY_EXIT < 0)
    }
}
