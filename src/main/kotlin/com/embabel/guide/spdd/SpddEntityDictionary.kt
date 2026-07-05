package com.embabel.guide.spdd

import com.embabel.agent.core.DataDictionary
import com.embabel.agent.core.DynamicType

/**
 * Minimal SDLC-SPDD domain schema for leg 3 entity projection (SPIKE-001).
 * Uses [DynamicType] so we can spike without a separate Kotlin NamedEntity module.
 */
object SpddEntityDictionary {

    private val domainTypes = listOf(
        DynamicType("WorkId", "A unit of SPDD work (FEAT-, SPIKE-, BUG-, REF-)", emptyList(), emptyList(), true),
        DynamicType("Canvas", "REASONS canvas for a Work ID", emptyList(), emptyList(), true),
        DynamicType("Area", "Code area bucket or Java package", emptyList(), emptyList(), true),
        DynamicType("Operation", "Canvas operation (T01, T02, …)", emptyList(), emptyList(), true),
        DynamicType("Decision", "Recorded architecture decision", emptyList(), emptyList(), true),
        DynamicType("Pitfall", "Known pitfall", emptyList(), emptyList(), true),
        DynamicType("Pattern", "Reusable pattern", emptyList(), emptyList(), true),
    )

    fun create(): DataDictionary = DataDictionary.fromDomainTypes("sdlc-spdd", domainTypes)
}
