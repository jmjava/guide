package com.embabel.guide.fitness

import com.tngtech.archunit.core.domain.JavaClass
import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.core.importer.Location
import com.tngtech.archunit.lang.ArchCondition
import com.tngtech.archunit.lang.ConditionEvents
import com.tngtech.archunit.lang.SimpleConditionEvent
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets

/**
 * Production bytecode must not name github.com/embabel/guide as a push URL
 * or a pull-request target. Git remotes are already refused by
 * scripts/forbid-embabel-upstream.sh; this test covers compiled production code.
 */
class NoEmbabelGuideUpstreamTargetArchTest {

    @Test
    fun productionCodeMustNotReferenceEmbabelGuidePushOrPrTarget() {
        val imported = ClassFileImporter()
            .withImportOption(OnlyProductionClasses())
            .importPackages("com.embabel")
        assertTrue(imported.size > 0, "compiled production classes are required")
        classes()
            .should(notReferenceEmbabelGuidePushOrPrTarget())
            .check(imported)
    }
}

private class OnlyProductionClasses : ImportOption {
    override fun includes(location: Location): Boolean {
        return location.contains("/target/classes/") &&
            !location.contains("test-classes")
    }
}

private fun notReferenceEmbabelGuidePushOrPrTarget(): ArchCondition<JavaClass> {
    return object : ArchCondition<JavaClass>(
        "not reference a push URL or PR target for github.com/embabel/guide",
    ) {
        override fun check(item: JavaClass, events: ConditionEvents) {
            val text = classFileText(item)
            val hit = forbiddenHit(text)
            if (hit == null) {
                return
            }
            val message = item.name + " references an embabel/guide push URL or PR target (" + hit + ")"
            events.add(SimpleConditionEvent.violated(item, message))
        }
    }
}

private fun classFileText(item: JavaClass): String {
    val resource = item.name.replace('.', '/') + ".class"
    val stream = item.reflect().classLoader.getResourceAsStream(resource)
    if (stream == null) {
        return UNREADABLE
    }
    return stream.use { String(it.readBytes(), StandardCharsets.ISO_8859_1) }
}

private fun forbiddenHit(text: String): String? {
    if (text == UNREADABLE) {
        return "unreadable class file"
    }
    var found: String? = null
    for (needle in NEEDLES) {
        var from = 0
        while (from < text.length && found == null) {
            val at = text.indexOf(needle, from)
            if (at < 0) {
                break
            }
            val after = at + needle.length
            val next = if (after < text.length) text[after] else '\u0000'
            val continues = next.isLetterOrDigit() || next == '_' || next == '-'
            if (!continues) {
                found = needle
            }
            from = at + 1
        }
    }
    return found
}

private const val UNREADABLE = "\u0000unreadable-class-file"

private val NEEDLES = listOf(
    "github.com/embabel/guide",
    "github.com:embabel/guide",
    "git@github.com:embabel/guide",
)
