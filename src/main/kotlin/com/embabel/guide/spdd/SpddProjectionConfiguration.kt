package com.embabel.guide.spdd

import com.embabel.agent.rag.neo.drivine.DrivineNamedEntityDataRepository
import com.embabel.agent.rag.service.NamedEntityDataRepository
import com.embabel.common.ai.model.EmbeddingService
import com.embabel.agent.rag.neo.drivine.NeoRagServiceProperties
import com.fasterxml.jackson.databind.ObjectMapper
import org.drivine.manager.GraphObjectManager
import org.drivine.manager.PersistenceManager
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@ConditionalOnProperty(prefix = "guide.spdd-projection", name = ["enabled"], havingValue = "true")
class SpddProjectionConfiguration {

    @Bean
    fun spddNamedEntityDataRepository(
        @Qualifier("neo") persistenceManager: PersistenceManager,
        neoRagProperties: NeoRagServiceProperties,
        embeddingService: EmbeddingService,
        @Qualifier("neoGraphObjectManager") graphObjectManager: GraphObjectManager,
        objectMapper: ObjectMapper,
    ): NamedEntityDataRepository =
        DrivineNamedEntityDataRepository(
            persistenceManager,
            neoRagProperties,
            SpddEntityDictionary.create(),
            embeddingService,
            graphObjectManager,
            objectMapper,
        )
}
