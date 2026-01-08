// Generated code - do not modify
// Additional properties for classes not processed by KSP codegen
package com.embabel.guide.domain

import kotlin.String
import org.drivine.query.dsl.NodeReference
import org.drivine.query.dsl.StringPropertyReference

public class WebUserDataProperties(
  private val alias: String,
) : NodeReference {
  override val nodeAlias: String
    get() = alias

  public val id: StringPropertyReference = org.drivine.query.dsl.StringPropertyReference(alias,
      "id")

  public val displayName: StringPropertyReference =
      org.drivine.query.dsl.StringPropertyReference(alias, "displayName")

  public val userName: StringPropertyReference =
      org.drivine.query.dsl.StringPropertyReference(alias, "userName")

  public val userEmail: StringPropertyReference =
      org.drivine.query.dsl.StringPropertyReference(alias, "userEmail")

  public val passwordHash: StringPropertyReference =
      org.drivine.query.dsl.StringPropertyReference(alias, "passwordHash")

  public val refreshToken: StringPropertyReference =
      org.drivine.query.dsl.StringPropertyReference(alias, "refreshToken")
}

public class DiscordUserInfoDataProperties(
  private val alias: String,
) : NodeReference {
  override val nodeAlias: String
    get() = alias

  public val id: StringPropertyReference = org.drivine.query.dsl.StringPropertyReference(alias,
      "id")

  public val username: StringPropertyReference =
      org.drivine.query.dsl.StringPropertyReference(alias, "username")

  public val discriminator: StringPropertyReference =
      org.drivine.query.dsl.StringPropertyReference(alias, "discriminator")

  public val displayName: StringPropertyReference =
      org.drivine.query.dsl.StringPropertyReference(alias, "displayName")

  public val isBot: StringPropertyReference =
      org.drivine.query.dsl.StringPropertyReference(alias, "isBot")

  public val avatarUrl: StringPropertyReference =
      org.drivine.query.dsl.StringPropertyReference(alias, "avatarUrl")
}

