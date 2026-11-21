package com.adhdkiller.app.enforcement.shortvideo

import com.adhdkiller.app.data.db.entity.BlockRuleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The seed migration has to upgrade the rules that are still untouched defaults — and it has
 * to leave alone every rule the user has edited by hand.
 */
class BlockRuleMigrationTest {

    private fun stored(rule: ShortFormMatcher.Rule, enabled: Boolean = true) =
        ShortFormMatcher.toEntity(rule, enabled = enabled)

    @Test
    fun `untouched legacy rules are migrated to the current defaults`() {
        val legacy = ShortFormMatcher.legacyDefaults()
        val current = ShortFormMatcher.defaults()

        val migrations = ShortFormMatcher.planMigration(
            current = current,
            legacy = legacy,
            stored = legacy.map { stored(it) },
        )

        // Every default whose content changed has to be replaced.
        val changed = current.filter { new ->
            legacy.firstOrNull { it.packageName == new.packageName }
                ?.let { old -> !ShortFormMatcher.sameContent(old, new) } == true
        }
        assertTrue("expected at least one changed default", changed.isNotEmpty())
        assertEquals(changed.size, migrations.size)
        assertTrue(migrations.any { it.packageName == "com.google.android.youtube" })
        assertTrue(migrations.any { it.packageName == "com.instagram.android" })
    }

    @Test
    fun `migrated rules carry the current content and keep the enabled flag`() {
        val legacy = ShortFormMatcher.legacyDefaults()
        val current = ShortFormMatcher.defaults()

        val migrations = ShortFormMatcher.planMigration(
            current = current,
            legacy = legacy,
            stored = legacy.map { stored(it, enabled = false) },
        )

        migrations.forEach { migrated ->
            val updated = current.first { it.packageName == migrated.packageName }
            assertEquals(updated.keywords, migrated.keywords.split(',').filter { it.isNotBlank() })
            assertEquals(false, migrated.enabled)
        }
    }

    @Test
    fun `rules the user edited are never overwritten`() {
        val legacy = ShortFormMatcher.legacyDefaults()
        val current = ShortFormMatcher.defaults()

        // The user changed the Instagram keywords.
        val editedInstagram = legacy.first { it.packageName == "com.instagram.android" }
            .copy(keywords = listOf("my_own_marker"))
        val storedRules = legacy.map { rule ->
            if (rule.packageName == "com.instagram.android") stored(editedInstagram) else stored(rule)
        }

        val migrations = ShortFormMatcher.planMigration(current, legacy, storedRules)

        assertTrue(migrations.none { it.packageName == "com.instagram.android" })
    }

    @Test
    fun `rules that already match the current defaults are left alone`() {
        val current = ShortFormMatcher.defaults()
        val migrations = ShortFormMatcher.planMigration(
            current = current,
            legacy = ShortFormMatcher.legacyDefaults(),
            stored = current.map { stored(it) },
        )
        assertTrue(migrations.isEmpty())
    }
}
