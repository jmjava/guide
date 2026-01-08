# Test ApplicationContext Fix Summary

## Problem
Tests were failing with `ApplicationContext failure threshold exceeded` due to:
1. Missing `DrivineStore` bean (required by `DataManager` and `ChatActions`)
2. `ClassNotFoundException` for `ContentElementRepositoryInfo` (wrong package path)
3. Tests trying to connect to non-existent local Neo4j on port 7687

## Solution Overview
Created a mock `DrivineStore` bean for tests and configured tests to use TestContainers for Neo4j.

## Required Changes

### 1. Create Test Configuration (`TestDrivineStoreConfiguration.kt`)

**File:** `src/test/kotlin/com/embabel/guide/TestDrivineStoreConfiguration.kt`

**Purpose:** Provides a mocked `DrivineStore` bean so tests don't require full RAG setup.

**Key Points:**
- Use correct import: `com.embabel.agent.rag.store.ContentElementRepositoryInfo` (NOT `com.embabel.agent.rag.neo.drivine.*`)
- Mock `provision()` to do nothing
- Mock `info()` to return a `ContentElementRepositoryInfo` with all counts = 0

**Template:**
```kotlin
@Configuration
@Profile("test")
@TestConfiguration
class TestDrivineStoreConfiguration {
    @Bean
    @Primary
    @ConditionalOnMissingBean
    fun drivineStore(): DrivineStore {
        val mockStore = Mockito.mock(DrivineStore::class.java)
        Mockito.doNothing().`when`(mockStore).provision()
        
        val mockInfo = Mockito.mock(ContentElementRepositoryInfo::class.java)
        Mockito.`when`(mockInfo.chunkCount).thenReturn(0)
        Mockito.`when`(mockInfo.documentCount).thenReturn(0)
        Mockito.`when`(mockInfo.contentElementCount).thenReturn(0)
        
        Mockito.`when`(mockStore.info()).thenReturn(mockInfo)
        return mockStore
    }
}
```

### 2. Import Configuration in All Test Classes

**Files to modify:**
- `src/test/kotlin/com/embabel/hub/HubServiceTest.kt`
- `src/test/kotlin/com/embabel/hub/HubApiControllerTest.kt`
- `src/test/kotlin/com/embabel/guide/domain/GuideUserServiceTest.kt`
- `src/test/kotlin/com/embabel/guide/domain/drivine/DrivineGuideUserRepositoryTest.kt`
- `src/test/kotlin/com/embabel/guide/domain/drivine/GraphObjectGuideUserRepositoryTest.kt`
- `src/test/kotlin/com/embabel/guide/chat/security/McpSecurityTest.kt`

**Change:** Add import and annotation to each test class:
```kotlin
import com.embabel.guide.TestDrivineStoreConfiguration
import org.springframework.context.annotation.Import

@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = [Neo4jPropertiesInitializer::class])
@Import(TestDrivineStoreConfiguration::class)  // ADD THIS LINE
class YourTestClass {
    // ...
}
```

### 3. Import Configuration in TestAppContext (Optional but Recommended)

**File:** `src/test/kotlin/com/embabel/guide/TestApplicationContext.kt`

**Change:** Add to `TestAppContext` class:
```kotlin
@Configuration
@ComponentScan(basePackages = ["com.embabel"])
@PropertySource("classpath:application.yml")
@EnableAspectJAutoProxy(proxyTargetClass = true)
@EnableDrivineTestConfig
@Import(TestDrivineStoreConfiguration::class)  // ADD THIS LINE
class TestAppContext
```

**Why:** Makes the mock bean available globally to all tests using this context.

### 4. Configure Tests to Use TestContainers

**File:** `src/test/resources/application-test.yml`

**Change:**
```yaml
test:
  neo4j:
    use-local: false  # Changed from true
```

**Why:** 
- `true` requires Neo4j running on `localhost:7687`
- `false` uses TestContainers (automatic Docker container, isolated per test run)

## Verification

After making changes, verify with:
```bash
mvn clean test
```

Expected result: All 52 tests should pass.

## Key Dependencies

Ensure these are in your `pom.xml`:
- Mockito (for mocking)
- `com.embabel.agent:embabel-agent-rag-neo-drivine` (for `DrivineStore` interface)
- `com.embabel.agent:embabel-agent-rag-core` (for `ContentElementRepositoryInfo`)

## Why These Changes Work

1. **Mock Bean:** Provides `DrivineStore` without requiring full RAG infrastructure
2. **Correct Class Path:** `ContentElementRepositoryInfo` is in `com.embabel.agent.rag.store.*`, not `com.embabel.agent.rag.neo.drivine.*`
3. **TestContainers:** Eliminates need for manual Neo4j setup - container is created/destroyed automatically
4. **@Import Annotation:** Tells Spring to load the test configuration alongside the main application context

## Notes

- The mock `DrivineStore` only mocks `provision()` and `info()` methods
- If tests need additional mocked behavior, extend `TestDrivineStoreConfiguration`
- TestContainers adds ~5-10 seconds to test startup but provides full isolation
- To use local Neo4j instead, set `use-local: true` and ensure Neo4j is running on port 7687

