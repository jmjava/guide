/*
 * Copyright 2024-2025 Embabel Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.embabel.guide

import com.embabel.agent.rag.neo.drivine.DrivineStore
import com.embabel.agent.rag.store.ContentElementRepositoryInfo
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.mockito.Mockito

/**
 * Test configuration that provides a mocked DrivineStore bean for tests.
 * This allows tests to run without requiring the full RAG store setup.
 */
@Configuration
@Profile("test")
@TestConfiguration
class TestDrivineStoreConfiguration {
    
    @Bean
    @Primary
    @ConditionalOnMissingBean
    fun drivineStore(): DrivineStore {
        val mockStore = Mockito.mock(DrivineStore::class.java)
        
        // Mock provision() to do nothing
        Mockito.doNothing().`when`(mockStore).provision()
        
        // Create a mock for ContentElementRepositoryInfo
        val mockInfo = Mockito.mock(ContentElementRepositoryInfo::class.java)
        Mockito.`when`(mockInfo.chunkCount).thenReturn(0)
        Mockito.`when`(mockInfo.documentCount).thenReturn(0)
        Mockito.`when`(mockInfo.contentElementCount).thenReturn(0)
        
        // Set up the info() method to return our mock
        Mockito.`when`(mockStore.info()).thenReturn(mockInfo)
        
        return mockStore
    }
}
