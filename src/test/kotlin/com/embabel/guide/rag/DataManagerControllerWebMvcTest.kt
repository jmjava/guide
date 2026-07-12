package com.embabel.guide.rag

import com.embabel.guide.stats.GuideStatsService
import com.embabel.hub.JwtTokenService
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Duration

@WebMvcTest(controllers = [DataManagerController::class])
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class DataManagerControllerWebMvcTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @MockitoBean
    lateinit var dataManager: DataManager

    @MockitoBean
    lateinit var jwtTokenService: JwtTokenService

    @MockitoBean
    lateinit var guideStatsService: GuideStatsService

    @Test
    fun `load-references returns 200`() {
        val result = IngestionResult(
            emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), Duration.ZERO,
        )
        `when`(dataManager.loadReferences()).thenReturn(result)

        mockMvc.perform(post("/api/v1/data/load-references"))
            .andExpect(status().isOk)
    }
}
