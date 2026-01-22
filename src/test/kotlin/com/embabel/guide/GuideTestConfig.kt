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

import com.embabel.hub.WelcomeGreeter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/**
 * Test-specific configuration for integration tests.
 */
@Configuration
@Profile("test")
class GuideTestConfig {

    /**
     * No-op WelcomeGreeter for tests to avoid fire-and-forget coroutines
     * that can interfere with transactional test rollback.
     */
    @Bean
    @org.springframework.context.annotation.Primary
    fun testWelcomeGreeter(): WelcomeGreeter {
        return object : WelcomeGreeter {
            override fun greetNewUser(guideUserId: String, webUserId: String, displayName: String) {
                // No-op for tests
            }
        }
    }
}
