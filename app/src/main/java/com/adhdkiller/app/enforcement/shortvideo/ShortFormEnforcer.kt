package com.adhdkiller.app.enforcement.shortvideo

import android.content.Context
import android.util.Log
import com.adhdkiller.app.core.ScheduleMirror
import com.adhdkiller.app.data.prefs.SettingsRepository
import com.adhdkiller.app.data.repo.BlockRuleRepository
import com.adhdkiller.app.enforcement.app.AppInventory
import com.adhdkiller.app.enforcement.guard.GuardStateHolder
import kotlinx.coroutines.flow.first

/**
 * The "guarantee layer" of the short-form blocker.
 *
 * For apps whose short-form surface cannot be distinguished reliably (or that the user marks
 * as strict), the **whole package** is blocked instead of a single feed. This used to be done by
 * suspending the package through the Device Owner API; without Device Owner the guard refuses the
 * package on every surface, which reaches the same practical result: the app cannot be used while
 * the rule is active.
 */
class ShortFormEnforcer(
    private val context: Context,
    private val inventory: AppInventory,
    private val ruleRepository: BlockRuleRepository,
    private val settings: SettingsRepository,
    private val guard: GuardStateHolder,
    private val mirror: ScheduleMirror,
) {

    companion object {
        const val TAG = "ShortFormEnforcer"

        /** Bump when the shipped default rules change so stale seeds can be migrated. */
        const val SEED_VERSION = 4
    }

    /**
     * Seeds the default rule set the first time the app runs, so the blocker does something
     * useful out of the box. Installs that were seeded with an older default set get their
     * untouched rules migrated to the current defaults — rules the user edited themselves are
     * left alone.
     */
    suspend fun seedDefaultsIfEmpty() {
        val current = ShortFormMatcher.defaults()
        val version = settings.blockRuleSeedVersion.first()

        if (ruleRepository.count() == 0) {
            current.forEach { rule -> ruleRepository.upsert(ShortFormMatcher.toEntity(rule)) }
            settings.setBlockRuleSeedVersion(SEED_VERSION)
            Log.i(TAG, "Seeded ${current.size} default block rules")
            return
        }

        if (version < SEED_VERSION) {
            val stored = ruleRepository.all()
            val migrations = ShortFormMatcher.planMigrationAcrossVersions(
                current = current,
                legacyVersions = ShortFormMatcher.shippedVersions(),
                stored = stored,
            )
            migrations.forEach { ruleRepository.upsert(it) }
            settings.setBlockRuleSeedVersion(SEED_VERSION)
            Log.i(TAG, "Migrated ${migrations.size} default block rule(s) to version $SEED_VERSION")
        }
    }

    /**
     * Publishes the strict rules to the guard. A strict rule blocks its whole package, whether or
     * not a lockdown is running.
     */
    suspend fun reconcile() {
        val blockerEnabled = settings.blockerEnabled.first()
        val rules = ruleRepository.enabled().map { ShortFormMatcher.fromEntity(it) }
        val strictPackages = if (blockerEnabled) {
            rules.filter { it.strict }
                .map { it.packageName }
                .filter { inventory.isInstalled(it) }
                .toSet()
        } else {
            emptySet()
        }

        guard.update { it.copy(strictPackages = strictPackages) }

        // Mirror the strict list into device-protected storage so the guard still knows about it
        // before the first unlock after a reboot.
        runCatching {
            mirror.updateGuard { it.copy(strictPackages = strictPackages.toList()) }
        }

        Log.i(TAG, "Strict rules active for ${strictPackages.size} app(s)")
    }
}
