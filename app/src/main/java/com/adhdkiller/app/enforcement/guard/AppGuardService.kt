package com.adhdkiller.app.enforcement.guard

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.adhdkiller.app.ADHDKillerApp
import com.adhdkiller.app.enforcement.shortvideo.BlockOverlay
import com.adhdkiller.app.enforcement.shortvideo.ShortFormMatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The guard: the accessibility service that refuses to let a blocked app stay in the foreground.
 *
 * It replaces the Device Owner package suspension, which needed a factory-reset phone and an
 * `adb` command. The platform still offers no way to *prevent* an activity from starting, so the
 * guard does what the platform allows: it detects the blocked surface, covers it with a black
 * screen and navigates away from it.
 *
 * It enforces two independent rule sets:
 *
 *  * the **phone lockdown** — every package that is not on the allowlist while a window runs;
 *  * the **short-form blocker** — the exact feed surfaces (Shorts, Reels, TikTok).
 *
 * Both are published through [GuardStateHolder]; this service only reads them.
 */
class AppGuardService : AccessibilityService() {

    companion object {
        const val TAG = "AppGuard"
        const val ACTION_REFRESH = "com.adhdkiller.app.action.REFRESH_BLOCK_RULES"

        private const val SCAN_THROTTLE_MS = 700L
        private const val REPEAT_WINDOW_MS = 1500L
        private const val BACK_ESCALATION_MS = 350L
        private const val MAX_NODES = 600

        @Volatile
        var instance: AppGuardService? = null
            private set
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var rules: List<ShortFormMatcher.Rule> = emptyList()

    @Volatile
    private var blockerEnabled: Boolean = true

    /** The live block list, kept in memory so the event handler stays cheap. */
    @Volatile
    private var snapshot: GuardSnapshot = GuardSnapshot()

    private var overlay: BlockOverlay? = null
    private var lastScanAt: Long = 0
    private var lastBlockedKey: String? = null
    private var lastBlockedAt: Long = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this

        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
            AccessibilityEvent.TYPE_WINDOWS_CHANGED or
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        info.notificationTimeout = 100
        serviceInfo = info

        scope.launch {
            // Follow the live block list: the enforcers publish into it whenever a window opens
            // or closes.
            (application as? ADHDKillerApp)?.container?.guardState?.snapshot?.collect { snapshot = it }
        }
        scope.launch { refreshRules() }
        // Tell the app that the guard is standing: the capability rows in the UI are driven by
        // the status flow, so they have to be refreshed when the service binds.
        scope.launch {
            val container = (application as? ADHDKillerApp)?.container
            runCatching { container?.lockdownEnforcer?.refreshCapabilities() }
        }
    }

    override fun onDestroy() {
        instance = null
        overlay?.dismiss()
        scope.cancel()
        super.onDestroy()
    }

    override fun onInterrupt() {
        overlay?.dismiss()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString() ?: return

        // The lockdown outranks the block rules: during a window, a blocked app is closed
        // regardless of which surface of it is in front.
        if (GuardRules.isBlocked(snapshot, packageName)) {
            handleLockdownBlock(packageName)
            return
        }

        if (!blockerEnabled) return
        if (rules.isEmpty()) return

        val rule = rules.firstOrNull { it.packageName == packageName } ?: return

        val type = event.eventType
        if (type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOWS_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            return
        }

        val activityName = event.className?.toString()

        // Fast path: the activity itself identifies the short-form surface.
        val direct = ShortFormMatcher.match(rule, packageName, activityName, emptyList())
        if (direct != null) {
            handleMatch(direct, packageName)
            return
        }

        // Slow path: the feed is a fragment, so the window tree has to be inspected.
        if (rule.keywords.isNotEmpty()) {
            val now = System.currentTimeMillis()
            if (now - lastScanAt < SCAN_THROTTLE_MS) return
            lastScanAt = now
            scope.launch {
                val hints = collectNodeHints()
                val match = ShortFormMatcher.match(rule, packageName, activityName, hints)
                if (match != null) handleMatch(match, packageName)
            }
        }
    }

    // -----------------------------------------------------------------------------------
    // Lockdown
    // -----------------------------------------------------------------------------------

    /**
     * A blocked app came to the front during a lockdown. Cover it, send it away and log it.
     */
    private fun handleLockdownBlock(packageName: String) {
        val now = System.currentTimeMillis()
        val isRepeat = packageName == lastBlockedKey && now - lastBlockedAt < REPEAT_WINDOW_MS
        lastBlockedKey = packageName
        lastBlockedAt = now

        val container = (application as? ADHDKillerApp)?.container
        val label = container?.inventory?.labelOf(packageName) ?: packageName

        if (!isRepeat) {
            scope.launch {
                runCatching {
                    container?.distractionRepository?.log(packageName, label, "Lockdown")
                }
            }
        }

        showOverlay(
            title = "LOCKDOWN",
            appLabel = label,
            reason = "This app is blocked until the focus window ends.",
            footnote = "Emergency calls stay available from the lockdown screen.",
        )
        leaveSurface()
    }

    // -----------------------------------------------------------------------------------
    // Short-form blocker
    // -----------------------------------------------------------------------------------

    private fun handleMatch(match: ShortFormMatcher.Match, packageName: String) {
        val key = "${match.rule.packageName}:${match.rule.appLabel}"
        val now = System.currentTimeMillis()
        val isRepeat = key == lastBlockedKey && now - lastBlockedAt < REPEAT_WINDOW_MS
        lastBlockedKey = key
        lastBlockedAt = now

        val container = (application as? ADHDKillerApp)?.container

        if (!isRepeat) {
            scope.launch {
                runCatching { container?.distractionRepository?.log(packageName, match.rule.appLabel, match.reason) }
                if (container != null) {
                    runCatching {
                        // Resisting a feed is worth a small amount of XP.
                        container.gamification.award(2, "Resisted: ${match.rule.appLabel}")
                    }
                }
            }
        }

        showOverlay(
            title = "SHORT-FORM BLOCKED",
            appLabel = match.rule.appLabel,
            reason = match.reason,
            footnote = "Long-form video and the rest of your phone are still available.",
        )
        leaveSurface()
    }

    private fun showOverlay(title: String, appLabel: String, reason: String, footnote: String) {
        val overlay = overlay ?: BlockOverlay(this).also { overlay = it }
        overlay.show(title = title, appLabel = appLabel, reason = reason, footnote = footnote) {
            leaveSurface()
        }
    }

    /** Navigates away from the blocked surface: back first, home if that is not enough. */
    private fun leaveSurface() {
        performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
        scope.launch {
            delay(BACK_ESCALATION_MS)
            // If the feed is still in front, go home. The overlay hides the content either way.
            performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        }
    }

    /**
     * Walks the active window and collects view ids and content descriptions, which are the
     * signals the keyword rules match on. The walk is bounded so it stays cheap.
     */
    private fun collectNodeHints(): List<String> {
        val root: AccessibilityNodeInfo = try {
            rootInActiveWindow ?: return emptyList()
        } catch (t: Throwable) {
            Log.w(TAG, "Could not read window root: ${t.message}")
            return emptyList()
        }

        val hints = mutableListOf<String>()
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0

        while (queue.isNotEmpty() && visited < MAX_NODES) {
            val node = queue.removeFirst()
            visited++

            runCatching { node.viewIdResourceName }.getOrNull()?.takeIf { it.isNotBlank() }?.let { hints.add(it) }
            runCatching { node.contentDescription }.getOrNull()?.toString()
                ?.takeIf { it.isNotBlank() }?.let { hints.add(it) }
            runCatching { node.text }.getOrNull()?.toString()
                ?.takeIf { it.isNotBlank() && it.length <= 40 }?.let { hints.add(it) }

            for (index in 0 until node.childCount) {
                val child = runCatching { node.getChild(index) }.getOrNull() ?: continue
                queue.add(child)
            }
            runCatching { node.recycle() }
        }
        runCatching { root.recycle() }
        return hints
    }

    /** Re-reads the rules from the database and the blocker toggle. */
    suspend fun refreshRules() {
        val container = (application as? ADHDKillerApp)?.container ?: return
        runCatching {
            blockerEnabled = container.settings.blockerEnabled.first()
        }
        rules = runCatching {
            container.blockRuleRepository.enabled().map { ShortFormMatcher.fromEntity(it) }
        }.getOrDefault(emptyList())
        Log.i(TAG, "Rules refreshed: ${rules.size} app(s)")
    }
}
