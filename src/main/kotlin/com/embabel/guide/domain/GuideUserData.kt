package com.embabel.guide.domain

import com.embabel.chat.store.model.SessionUser
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.drivine.annotation.NodeFragment
import org.drivine.annotation.NodeId

/**
 * Node fragment representing a GuideUser in the graph.
 * Implements SessionUser to enable integration with embabel-chat-store library.
 */
@NodeFragment(labels = ["GuideUser", "SessionUser"])
@JsonIgnoreProperties(ignoreUnknown = true)
data class GuideUserData(
    @NodeId
    override var id: String,
    override var displayName: String = "",
    var persona: String? = null,
    var customPrompt: String? = null
) : HasGuideUserData, SessionUser {

    override fun guideUserData(): GuideUserData = this
}
