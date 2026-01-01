package com.embabel.guide.domain

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import org.drivine.annotation.NodeFragment
import org.drivine.annotation.NodeId

/**
 * Node fragment representing a GuideUser in the graph.
 */
@NodeFragment(labels = ["GuideUser"])
@JsonIgnoreProperties(ignoreUnknown = true)
data class GuideUserData(
    @NodeId
    var id: String,
    var persona: String? = null,
    var customPrompt: String? = null
) : HasGuideUserData {

    override fun guideUserData(): GuideUserData = this
}
