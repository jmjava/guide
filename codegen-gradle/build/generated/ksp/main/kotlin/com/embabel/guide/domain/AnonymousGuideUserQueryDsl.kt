// Generated code - do not modify
package com.embabel.guide.domain

import kotlin.Int
import kotlin.Unit
import kotlin.collections.List
import org.drivine.manager.GraphObjectManager
import org.drivine.query.dsl.GraphQuerySpec

public class AnonymousGuideUserQueryDsl {
  public val core: GuideUserDataProperties = GuideUserDataProperties("core")

  public val webUser: AnonymousWebUserDataProperties = AnonymousWebUserDataProperties("webUser")

  public companion object {
    public val INSTANCE: AnonymousGuideUserQueryDsl = AnonymousGuideUserQueryDsl()
  }
}

public inline fun <reified T : AnonymousGuideUser> GraphObjectManager.loadAll(noinline
    spec: GraphQuerySpec<AnonymousGuideUserQueryDsl>.() -> Unit): List<T> = loadAll(T::class.java,
    AnonymousGuideUserQueryDsl.INSTANCE, spec)

public inline fun <reified T : AnonymousGuideUser> GraphObjectManager.deleteAll(noinline
    spec: GraphQuerySpec<AnonymousGuideUserQueryDsl>.() -> Unit): Int = deleteAll(T::class.java,
    AnonymousGuideUserQueryDsl.INSTANCE, spec)

context(builder: org.drivine.query.dsl.WhereBuilder<AnonymousGuideUserQueryDsl>)
@Suppress("CONTEXT_RECEIVERS_DEPRECATED")
public val core: GuideUserDataProperties
    get() = builder.queryObject.core

context(builder: org.drivine.query.dsl.OrderBuilder<AnonymousGuideUserQueryDsl>)
@Suppress("CONTEXT_RECEIVERS_DEPRECATED")
public val core: GuideUserDataProperties
    get() = builder.queryObject.core

context(builder: org.drivine.query.dsl.WhereBuilder<AnonymousGuideUserQueryDsl>)
@Suppress("CONTEXT_RECEIVERS_DEPRECATED")
public val webUser: AnonymousWebUserDataProperties
    get() = builder.queryObject.webUser

context(builder: org.drivine.query.dsl.OrderBuilder<AnonymousGuideUserQueryDsl>)
@Suppress("CONTEXT_RECEIVERS_DEPRECATED")
public val webUser: AnonymousWebUserDataProperties
    get() = builder.queryObject.webUser

