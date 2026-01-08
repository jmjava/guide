// Generated code - do not modify
package com.embabel.guide.domain

import kotlin.String
import org.drivine.query.dsl.NodeReference
import org.drivine.query.dsl.StringPropertyReference

public class GuideUserDataProperties(
  private val alias: String,
) : NodeReference {
  override val nodeAlias: String
    get() = alias

  public val id: StringPropertyReference = org.drivine.query.dsl.StringPropertyReference(alias,
      "id")

  public val persona: StringPropertyReference = org.drivine.query.dsl.StringPropertyReference(alias,
      "persona")

  public val customPrompt: StringPropertyReference =
      org.drivine.query.dsl.StringPropertyReference(alias, "customPrompt")
}

public class AnonymousWebUserDataProperties(
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
