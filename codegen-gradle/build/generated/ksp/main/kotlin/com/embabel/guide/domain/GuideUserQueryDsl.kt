// Generated code - do not modify
package com.embabel.guide.domain

import kotlin.Int
import kotlin.Unit
import kotlin.collections.List
import org.drivine.manager.GraphObjectManager
import org.drivine.query.dsl.GraphQuerySpec

public class GuideUserQueryDsl {
  public val core: GuideUserDataProperties = GuideUserDataProperties("core")

  public val webUser: WebUserDataProperties = WebUserDataProperties("webUser")

  public val discordUserInfo: DiscordUserInfoDataProperties = DiscordUserInfoDataProperties("discordUserInfo")

  public companion object {
    public val INSTANCE: GuideUserQueryDsl = GuideUserQueryDsl()
  }
}

public inline fun <reified T : GuideUser> GraphObjectManager.loadAll(noinline
    spec: GraphQuerySpec<GuideUserQueryDsl>.() -> Unit): List<T> = loadAll(T::class.java,
    GuideUserQueryDsl.INSTANCE, spec)

public inline fun <reified T : GuideUser> GraphObjectManager.deleteAll(noinline
    spec: GraphQuerySpec<GuideUserQueryDsl>.() -> Unit): Int = deleteAll(T::class.java,
    GuideUserQueryDsl.INSTANCE, spec)

context(builder: org.drivine.query.dsl.WhereBuilder<GuideUserQueryDsl>)
@Suppress("CONTEXT_RECEIVERS_DEPRECATED")
public val query: GuideUserQueryDsl
    get() = builder.queryObject

context(builder: org.drivine.query.dsl.OrderBuilder<GuideUserQueryDsl>)
@Suppress("CONTEXT_RECEIVERS_DEPRECATED")
public val query: GuideUserQueryDsl
    get() = builder.queryObject

context(builder: org.drivine.query.dsl.WhereBuilder<GuideUserQueryDsl>)
@Suppress("CONTEXT_RECEIVERS_DEPRECATED")
public val core: GuideUserDataProperties
    get() = builder.queryObject.core

context(builder: org.drivine.query.dsl.OrderBuilder<GuideUserQueryDsl>)
@Suppress("CONTEXT_RECEIVERS_DEPRECATED")
public val core: GuideUserDataProperties
    get() = builder.queryObject.core

context(builder: org.drivine.query.dsl.WhereBuilder<GuideUserQueryDsl>)
@Suppress("CONTEXT_RECEIVERS_DEPRECATED")
public val webUser: WebUserDataProperties
    get() = builder.queryObject.webUser

context(builder: org.drivine.query.dsl.OrderBuilder<GuideUserQueryDsl>)
@Suppress("CONTEXT_RECEIVERS_DEPRECATED")
public val webUser: WebUserDataProperties
    get() = builder.queryObject.webUser

context(builder: org.drivine.query.dsl.WhereBuilder<GuideUserQueryDsl>)
@Suppress("CONTEXT_RECEIVERS_DEPRECATED")
public val discordUserInfo: DiscordUserInfoDataProperties
    get() = builder.queryObject.discordUserInfo

context(builder: org.drivine.query.dsl.OrderBuilder<GuideUserQueryDsl>)
@Suppress("CONTEXT_RECEIVERS_DEPRECATED")
public val discordUserInfo: DiscordUserInfoDataProperties
    get() = builder.queryObject.discordUserInfo
