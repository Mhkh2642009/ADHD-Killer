package com.adhdkiller.app.enforcement.shortvideo

import com.adhdkiller.app.data.db.entity.BlockMode
import com.adhdkiller.app.data.db.entity.BlockRuleEntity

/**
 * The rule engine for the short-form blocker.
 *
 * A rule describes *which surfaces* of an app are short-form. The engine deliberately works on
 * two independent signals:
 *
 *  * **activity patterns** — exact class names or class-name prefixes. Reliable when the app
 *    exposes a dedicated activity for the feed (YouTube Shorts).
 *  * **keywords** — substrings of a view id or content description. Needed when the feed is a
 *    fragment inside the main activity (Instagram Reels, Facebook Reels). Because these are
 *    heuristic, every keyword rule can carry *exclusion* keywords so that, for example, the
 *    "Shorts" tab in the bottom bar does not trigger a block while the player does.
 */
object ShortFormMatcher {

    data class Rule(
        val packageName: String,
        val appLabel: String,
        val mode: BlockMode,
        val activityPatterns: List<String>,
        val keywords: List<String>,
        val excludeKeywords: List<String>,
        val strict: Boolean,
    )

    data class Match(
        val rule: Rule,
        /** Human readable reason, shown on the block screen. */
        val reason: String,
    )

    fun fromEntity(entity: BlockRuleEntity): Rule = Rule(
        packageName = entity.packageName,
        appLabel = entity.appLabel,
        mode = entity.mode,
        activityPatterns = splitList(entity.activityPatterns),
        keywords = splitList(entity.keywords),
        excludeKeywords = splitList(entity.excludeKeywords),
        strict = entity.strict,
    )

    fun toEntity(rule: Rule, enabled: Boolean = true): BlockRuleEntity = BlockRuleEntity(
        packageName = rule.packageName,
        appLabel = rule.appLabel,
        enabled = enabled,
        mode = rule.mode,
        activityPatterns = rule.activityPatterns.joinToString(","),
        keywords = rule.keywords.joinToString(","),
        excludeKeywords = rule.excludeKeywords.joinToString(","),
        strict = rule.strict,
    )

    private fun splitList(raw: String): List<String> =
        raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    /**
     * @param activityName the class name of the foreground activity, if known.
     * @param nodeHints view ids / content descriptions collected from the window tree.
     */
    fun match(
        rule: Rule,
        packageName: String,
        activityName: String?,
        nodeHints: List<String>,
    ): Match? {
        if (rule.packageName != packageName) return null

        // A veto wins over everything: the user is on a surface that merely *mentions* the
        // keyword (a navigation tab, a settings toggle, …).
        if (rule.excludeKeywords.isNotEmpty() && nodeHints.any { hint ->
                rule.excludeKeywords.any { ex -> hint.contains(ex, ignoreCase = true) }
            }
        ) {
            return null
        }

        // A strict rule blocks the whole app, whatever surface of it is in front. This is what
        // replaces the old Device Owner package suspension: the app is refused everywhere.
        if (rule.strict) {
            return Match(rule, "${rule.appLabel} is blocked entirely")
        }

        return when (rule.mode) {
            BlockMode.WHOLE_APP -> Match(rule, "${rule.appLabel} is a short-form app")

            BlockMode.ACTIVITY -> {
                val activityHit = activityName != null && rule.activityPatterns.any { pattern ->
                    activityName.equals(pattern, ignoreCase = true) ||
                        activityName.startsWith(pattern, ignoreCase = true)
                }
                val keywordHit = rule.keywords.isNotEmpty() && nodeHints.any { hint ->
                    rule.keywords.any { key -> hint.contains(key, ignoreCase = true) }
                }
                when {
                    activityHit -> Match(rule, "Short-form player detected")
                    keywordHit -> Match(rule, "Short-form feed detected")
                    else -> null
                }
            }

            BlockMode.KEYWORD -> {
                val hit = rule.keywords.isNotEmpty() && nodeHints.any { hint ->
                    rule.keywords.any { key -> hint.contains(key, ignoreCase = true) }
                }
                if (hit) Match(rule, "Short-form feed detected") else null
            }
        }
    }

    /**
     * The rule set shipped with version 1 of the seed. It is kept so that installs created
     * before the defaults were tightened can be migrated without overwriting rules the user
     * has edited themselves.
     *
     * **This list must be an exact copy of what version 1 actually seeded.** The migration
     * only replaces a stored rule when it is byte-for-byte identical to the rule here, so a
     * drifted copy silently disables the migration for every install it was written for —
     * which is exactly what happened once: the list here claimed a YouTube rule with
     * `reel_player` keywords, while version 1 had seeded `Shorts_vewier,shorts_vewier,shorts`
     * and no exclusion keywords at all. The stale rule then survived every upgrade and kept
     * the broad `shorts` keyword that can match the YouTube home feed.
     *
     * `BlockRuleMigrationTest` pins these values so the list cannot drift again.
     */
    fun legacyDefaults(): List<Rule> = listOf(
        Rule(
            packageName = "com.google.android.youtube",
            appLabel = "YouTube",
            mode = BlockMode.ACTIVITY,
            activityPatterns = listOf(
                "com.google.android.youtube.shorts",
                "com.google.android.youtube.short",
            ),
            keywords = listOf("Shorts_vewier", "shorts_vewier", "shorts"),
            excludeKeywords = emptyList(),
            strict = false,
        ),
        Rule(
            packageName = "com.instagram.android",
            appLabel = "Instagram",
            mode = BlockMode.ACTIVITY,
            activityPatterns = listOf("com.instagram.ui.reels", "ReelsActivity"),
            keywords = listOf("reels_viewer", "reel_viewer", "Reels"),
            excludeKeywords = listOf("reels_tab", "tab_reels", "bottom_navigation", "navigation_bar"),
            strict = false,
        ),
        Rule(
            packageName = "com.zhiliaoapp.musically",
            appLabel = "TikTok",
            mode = BlockMode.WHOLE_APP,
            activityPatterns = emptyList(),
            keywords = emptyList(),
            excludeKeywords = emptyList(),
            strict = true,
        ),
        Rule(
            packageName = "com.ss.android.ugc.aweme",
            appLabel = "TikTok (global)",
            mode = BlockMode.WHOLE_APP,
            activityPatterns = emptyList(),
            keywords = emptyList(),
            excludeKeywords = emptyList(),
            strict = true,
        ),
        Rule(
            packageName = "com.snapchat.android",
            appLabel = "Snapchat",
            mode = BlockMode.WHOLE_APP,
            activityPatterns = emptyList(),
            keywords = emptyList(),
            excludeKeywords = emptyList(),
            strict = true,
        ),
        Rule(
            packageName = "com.facebook.katana",
            appLabel = "Facebook",
            mode = BlockMode.WHOLE_APP,
            activityPatterns = emptyList(),
            keywords = listOf("reels_viewer", "reel_viewer"),
            excludeKeywords = listOf("reels_tab", "tab_reels", "bottom_navigation", "navigation_bar"),
            strict = true,
        ),
    )

    /** True when two rules are identical apart from their enabled flag. */
    fun sameContent(a: Rule, b: Rule): Boolean =
        a.packageName == b.packageName &&
            a.mode == b.mode &&
            a.activityPatterns == b.activityPatterns &&
            a.keywords == b.keywords &&
            a.excludeKeywords == b.excludeKeywords &&
            a.strict == b.strict

    /**
     * The rule set of version 3, which was shipped between the v1 seed and the current one.
     * It is kept so that installs that already migrated to v3 can migrate again when the
     * YouTube rule was corrected for the new YouTube app.
     */
    fun supersededDefaults(): List<Rule> = listOf(
        Rule(
            packageName = "com.google.android.youtube",
            appLabel = "YouTube",
            mode = BlockMode.ACTIVITY,
            activityPatterns = listOf(
                "com.google.android.youtube.shorts",
                "com.google.android.youtube.short",
            ),
            keywords = emptyList(),
            excludeKeywords = emptyList(),
            strict = false,
        ),
    )

    /**
     * Every rule set this app has ever shipped. A stored rule is migrated when it is identical
     * to one of them — that is the only way to tell "the user never touched this rule" from
     * "the user edited this rule".
     */
    fun shippedVersions(): List<List<Rule>> = listOf(legacyDefaults(), supersededDefaults())

    /**
     * Works out which stored rules have to be replaced by the current defaults.
     *
     * A rule is only migrated when it is still byte-for-byte identical to one of the shipped
     * rule sets ([shippedVersions]) — a rule the user has edited is never touched.
     */
    fun planMigration(
        current: List<Rule>,
        legacy: List<Rule>,
        stored: List<BlockRuleEntity>,
    ): List<BlockRuleEntity> = planMigrationAcrossVersions(current, listOf(legacy), stored)

    /**
     * Works out which stored rules have to be replaced by the current defaults.
     *
     * A rule is only migrated when it is still byte-for-byte identical to the rule of one of the
     * shipped versions — a rule the user has edited is never touched.
     */
    fun planMigrationAcrossVersions(
        current: List<Rule>,
        legacyVersions: List<List<Rule>>,
        stored: List<BlockRuleEntity>,
    ): List<BlockRuleEntity> {
        val result = mutableListOf<BlockRuleEntity>()

        current.forEach { updated ->
            val existing = stored.firstOrNull { it.packageName == updated.packageName } ?: return@forEach
            val existingRule = fromEntity(existing)
            val untouched = legacyVersions.any { legacy ->
                legacy.firstOrNull { it.packageName == updated.packageName }
                    ?.let { old -> sameContent(existingRule, old) } == true
            }
            if (!untouched) return@forEach
            if (sameContent(existingRule, updated)) return@forEach
            result.add(toEntity(updated, enabled = existing.enabled))
        }

        return result
    }

    /**
     * The default rule set.
     *
     * The YouTube rule carries **two** signals, because YouTube changed its structure:
     *
     *  * the **activity patterns** `…youtube.shorts.*` still match the old YouTube app, which
     *    played Shorts in a dedicated activity;
     *  * the **keywords** `reel_watch_fragment` / `reel_player_overlay_container` match the new
     *    YouTube app, where the home feed and the Shorts player share one activity
     *    (`…youtube.app.honeycomb.Shell$HomeActivity`) and only the view ids tell them apart.
     *
     * The keywords are deliberately narrow: the home feed of the new YouTube app contains
     * `inline_player_*` and a single `reel_time_bar`, but never `reel_player_overlay_container`
     * or `reel_watch_fragment_root`. So long-form YouTube — the home feed, search, subscriptions
     * and the regular player — can never be caught by this rule, while the Shorts player always
     * is.
     */
    fun defaults(): List<Rule> = listOf(
        Rule(
            packageName = "com.google.android.youtube",
            appLabel = "YouTube",
            mode = BlockMode.ACTIVITY,
            activityPatterns = listOf(
                "com.google.android.youtube.shorts",
                "com.google.android.youtube.short",
            ),
            keywords = listOf("reel_watch_fragment", "reel_player_overlay_container"),
            excludeKeywords = listOf("bottom_navigation", "navigation_bar"),
            strict = false,
        ),
        Rule(
            packageName = "com.instagram.android",
            appLabel = "Instagram",
            mode = BlockMode.ACTIVITY,
            activityPatterns = listOf("com.instagram.ui.reels", "ReelsActivity"),
            keywords = listOf("reels_viewer", "reel_viewer"),
            excludeKeywords = listOf("reels_tab", "tab_reels", "bottom_navigation", "navigation_bar"),
            strict = false,
        ),
        Rule(
            packageName = "com.zhiliaoapp.musically",
            appLabel = "TikTok",
            mode = BlockMode.WHOLE_APP,
            activityPatterns = emptyList(),
            keywords = emptyList(),
            excludeKeywords = emptyList(),
            strict = true,
        ),
        Rule(
            packageName = "com.ss.android.ugc.aweme",
            appLabel = "TikTok (global)",
            mode = BlockMode.WHOLE_APP,
            activityPatterns = emptyList(),
            keywords = emptyList(),
            excludeKeywords = emptyList(),
            strict = true,
        ),
        Rule(
            packageName = "com.snapchat.android",
            appLabel = "Snapchat",
            mode = BlockMode.WHOLE_APP,
            activityPatterns = emptyList(),
            keywords = emptyList(),
            excludeKeywords = emptyList(),
            strict = true,
        ),
        Rule(
            packageName = "com.facebook.katana",
            appLabel = "Facebook",
            mode = BlockMode.KEYWORD,
            activityPatterns = emptyList(),
            keywords = listOf("reels_viewer", "reel_viewer"),
            excludeKeywords = listOf("reels_tab", "tab_reels", "bottom_navigation", "navigation_bar"),
            strict = false,
        ),
    )
}
