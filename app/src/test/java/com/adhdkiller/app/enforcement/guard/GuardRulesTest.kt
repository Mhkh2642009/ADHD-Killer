package com.adhdkiller.app.enforcement.guard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The guard is what replaced the Device Owner package suspension, so its decision logic has to
 * be exactly right:
 *
 *  * a strict short-form rule blocks its app whether or not a lockdown is running;
 *  * a lockdown blocks exactly the packages that are not on the allowlist;
 *  * nothing is blocked while no lockdown is running.
 */
class GuardRulesTest {

    private val installed = listOf(
        "com.adhdkiller.app",
        "com.android.settings",
        "com.google.android.youtube",
        "com.zhiliaoapp.musically",
        "com.android.dialer",
    )

    private val allowed = setOf(
        "com.adhdkiller.app",
        "com.android.settings",
        "com.android.dialer",
    )

    @Test
    fun `the lockdown blocks everything that is not on the allowlist`() {
        val blocked = GuardRules.blockedPackages(installed, allowed)
        assertEquals(setOf("com.google.android.youtube", "com.zhiliaoapp.musically"), blocked)
    }

    @Test
    fun `the app itself and the always protected packages stay reachable`() {
        val blocked = GuardRules.blockedPackages(installed, allowed)
        assertFalse(blocked.contains("com.adhdkiller.app"))
        assertFalse(blocked.contains("com.android.dialer"))
        assertFalse(blocked.contains("com.android.settings"))
    }

    @Test
    fun `a blocked package is refused while the lockdown runs`() {
        val snapshot = GuardSnapshot(
            lockdownActive = true,
            blockedPackages = GuardRules.blockedPackages(installed, allowed),
        )
        assertTrue(GuardRules.isBlocked(snapshot, "com.google.android.youtube"))
        assertFalse(GuardRules.isBlocked(snapshot, "com.android.dialer"))
    }

    @Test
    fun `nothing is blocked while no lockdown is running`() {
        val snapshot = GuardSnapshot(
            lockdownActive = false,
            blockedPackages = GuardRules.blockedPackages(installed, allowed),
        )
        assertFalse(GuardRules.isBlocked(snapshot, "com.google.android.youtube"))
    }

    @Test
    fun `a strict rule blocks its app even outside a lockdown`() {
        val snapshot = GuardSnapshot(strictPackages = setOf("com.zhiliaoapp.musically"))
        assertTrue(GuardRules.isBlocked(snapshot, "com.zhiliaoapp.musically"))
        assertFalse(GuardRules.isBlocked(snapshot, "com.google.android.youtube"))
    }

    @Test
    fun `the strict rule wins over an inactive lockdown`() {
        val snapshot = GuardSnapshot(
            lockdownActive = false,
            strictPackages = setOf("com.zhiliaoapp.musically"),
        )
        assertTrue(GuardRules.isBlocked(snapshot, "com.zhiliaoapp.musically"))
    }

    @Test
    fun `an empty package name is never blocked`() {
        val snapshot = GuardSnapshot(
            lockdownActive = true,
            blockedPackages = GuardRules.blockedPackages(installed, allowed),
        )
        assertFalse(GuardRules.isBlocked(snapshot, ""))
    }
}
