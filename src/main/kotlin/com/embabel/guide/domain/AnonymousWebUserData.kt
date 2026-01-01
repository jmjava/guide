package com.embabel.guide.domain

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.drivine.annotation.NodeFragment

/**
 * Node fragment for anonymous web users.
 * Has both WebUser and Anonymous labels in the graph.
 */
@NodeFragment(labels = ["WebUser", "Anonymous"])
@JsonIgnoreProperties(ignoreUnknown = true)
class AnonymousWebUserData(
    id: String,
    displayName: String,
    userName: String,
    userEmail: String?,
    passwordHash: String?,
    refreshToken: String?
) : WebUserData(id, displayName, userName, userEmail, passwordHash, refreshToken) {

    override fun toString(): String =
        "AnonymousWebUserData{id='$id', displayName='$displayName', userName='$userName'}"
}
