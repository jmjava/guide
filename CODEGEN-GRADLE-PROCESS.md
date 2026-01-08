# How Generated Files Are Created in Codegen-Gradle

This document explains the process by which KSP (Kotlin Symbol Processing) generates Drivine DSL code in the `codegen-gradle` directory.

## Overview

The `codegen-gradle` directory uses **KSP (Kotlin Symbol Processing)** to generate type-safe query DSL code from Drivine annotations (`@GraphView` and `@NodeFragment`). This is a temporary workaround because Maven's KSP plugin doesn't support Kotlin 2.2.0 yet.

## The Generation Process

### Step 1: Source Annotations

**Location:** `src/main/kotlin/com/embabel/guide/domain/`

The following classes are annotated with Drivine annotations:

1. **`GuideUser.kt`** - Uses `@GraphView`

   ```kotlin
   @GraphView
   data class GuideUser(
       @Root
       val core: GuideUserData,
       @GraphRelationship(type = "IS_WEB_USER", direction = Direction.OUTGOING)
       val webUser: WebUserData? = null,
       @GraphRelationship(type = "IS_DISCORD_USER", direction = Direction.OUTGOING)
       val discordUserInfo: DiscordUserInfoData? = null
   )
   ```

2. **`GuideUserData.kt`** - Uses `@NodeFragment`

   ```kotlin
   @NodeFragment(labels = ["GuideUser"])
   data class GuideUserData(...)
   ```

3. **`WebUserData.kt`** - Uses `@NodeFragment`

   ```kotlin
   @NodeFragment(labels = ["WebUser"])
   open class WebUserData(...)
   ```

4. **`DiscordUserInfoData.kt`** - Uses `@NodeFragment`

   ```kotlin
   @NodeFragment(labels = ["DiscordUserInfo"])
   data class DiscordUserInfoData(...)
   ```

5. **`AnonymousGuideUser.kt`** - Uses `@GraphView`
   ```kotlin
   @GraphView
   data class AnonymousGuideUser(...)
   ```

### Step 2: Gradle Build Configuration

**File:** `codegen-gradle/build.gradle.kts`

The Gradle build is configured to:

1. **Include parent project sources:**

   ```kotlin
   java {
       sourceSets {
           main {
               java.srcDirs("../src/main/java")
           }
       }
   }

   kotlin {
       sourceSets {
           main {
               kotlin.srcDirs("../src/main/kotlin")
           }
       }
   }
   ```

   This tells Gradle to read Kotlin/Java sources from the parent project.

2. **Configure KSP processor:**

   ```kotlin
   dependencies {
       implementation("org.drivine:drivine4j:0.0.12")
       ksp("org.drivine:drivine4j-codegen:0.0.12")  // KSP processor
   }
   ```

   The `drivine4j-codegen` dependency contains the KSP processor that scans for annotations.

3. **Set output directory:**
   ```kotlin
   kotlin {
       sourceSets {
           main {
               kotlin.srcDirs(
                   "../src/main/kotlin",
                   "build/generated/ksp/main/kotlin"  // Generated code goes here
               )
           }
       }
   }
   ```

### Step 3: Running KSP Code Generation

**Manual execution:**

```bash
cd codegen-gradle
./gradlew kspKotlin
```

**Automatic execution (via Maven):**

When you run `mvn clean package` or `mvn generate-sources`, Maven executes:

1. **`exec-maven-plugin`** (in `pom.xml`):

   ```xml
   <execution>
       <id>gradle-ksp-codegen</id>
       <phase>generate-sources</phase>
       <goals>
           <goal>exec</goal>
       </goals>
       <configuration>
           <executable>${project.basedir}/codegen-gradle/gradlew</executable>
           <workingDirectory>${project.basedir}/codegen-gradle</workingDirectory>
           <arguments>
               <argument>kspKotlin</argument>
           </arguments>
       </configuration>
   </execution>
   ```

   This runs `./gradlew kspKotlin` during the `generate-sources` phase.

2. **KSP processor scans:**

   - Reads all Kotlin/Java files from `../src/main/kotlin` and `../src/main/java`
   - Finds classes annotated with `@GraphView` and `@NodeFragment`
   - Analyzes relationships, properties, and annotations

3. **Code generation:**
   - Generates type-safe query DSL classes
   - Writes to `codegen-gradle/build/generated/ksp/main/kotlin/`

### Step 4: Generated Files

**Location:** `codegen-gradle/build/generated/ksp/main/kotlin/com/embabel/guide/domain/`

The KSP processor generates:

1. **`GuideUserQueryDsl.kt`**

   - Generated from `@GraphView` on `GuideUser`
   - Provides type-safe query builder: `GuideUserQueryDsl.INSTANCE`
   - Includes property references: `core`, `webUser`, `discordUserInfo`

2. **`AnonymousGuideUserQueryDsl.kt`**

   - Generated from `@GraphView` on `AnonymousGuideUser`
   - Similar structure to `GuideUserQueryDsl`

3. **`GeneratedProperties.kt`**

   - Contains property reference classes for each `@NodeFragment`
   - Examples: `GuideUserDataProperties`, `WebUserDataProperties`, `DiscordUserInfoDataProperties`
   - Each property class has references for all fields (e.g., `id`, `displayName`, `userName`)

4. **`AdditionalProperties.kt`** (manually created workaround)
   - Contains property classes for `WebUserDataProperties` and `DiscordUserInfoDataProperties`
   - **Why manually created:** The KSP processor (`drivine4j-codegen:0.0.12`) didn't generate these classes automatically
   - **Reason:** These `@NodeFragment` classes (`WebUserData`, `DiscordUserInfoData`) are used in relationships within `GuideUser` but weren't directly processed by KSP
   - **Note:** File header says "Generated code - do not modify" but it's actually manually maintained as a workaround
   - **Future:** Should be removed once KSP processor generates these automatically

### Step 5: Including Generated Sources in Maven Build

**File:** `pom.xml` - `build-helper-maven-plugin`

After KSP generates the code, Maven includes it in the build:

```xml
<execution>
    <id>add-ksp-generated-sources</id>
    <phase>generate-sources</phase>
    <goals>
        <goal>add-source</goal>
    </goals>
    <configuration>
        <sources>
            <source>${project.basedir}/codegen-gradle/build/generated/ksp/main/kotlin</source>
        </sources>
    </configuration>
</execution>
```

This adds the generated directory to Maven's source path, so the generated classes are available during compilation.

### Step 6: Using Generated Code

**Example usage in repository:**

```kotlin
// In GraphObjectGuideUserRepository.kt
import com.embabel.guide.domain.GuideUserQueryDsl

fun findByWebUserId(userId: String): Optional<GuideUser> {
    return graphObjectManager.loadAll<GuideUser> {
        where {
            webUser.id eq userId  // Type-safe property reference!
        }
    }.firstOrNull()?.let { Optional.of(it) } ?: Optional.empty()
}
```

The `GuideUserQueryDsl` provides:

- Type-safe property references (`webUser.id`, `core.persona`, etc.)
- Compile-time checking (prevents typos in property names)
- IDE autocomplete support

## Complete Flow Diagram

```
┌─────────────────────────────────────────────────────────────┐
│ 1. Source Files (src/main/kotlin/)                         │
│    - GuideUser.kt (@GraphView)                              │
│    - GuideUserData.kt (@NodeFragment)                       │
│    - WebUserData.kt (@NodeFragment)                        │
│    - DiscordUserInfoData.kt (@NodeFragment)                │
└─────────────────────────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│ 2. Maven generate-sources phase                             │
│    exec-maven-plugin runs: ./gradlew kspKotlin             │
└─────────────────────────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│ 3. Gradle KSP Processing                                    │
│    - KSP processor (drivine4j-codegen) scans sources       │
│    - Finds @GraphView and @NodeFragment annotations         │
│    - Analyzes relationships and properties                 │
└─────────────────────────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│ 4. Code Generation                                          │
│    Output: codegen-gradle/build/generated/ksp/main/kotlin/  │
│    - GuideUserQueryDsl.kt                                   │
│    - AnonymousGuideUserQueryDsl.kt                         │
│    - GeneratedProperties.kt                                 │
└─────────────────────────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│ 5. Maven build-helper-maven-plugin                          │
│    Adds generated sources to Maven build path               │
└─────────────────────────────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│ 6. Maven Compilation                                        │
│    Generated classes available for use in source code       │
└─────────────────────────────────────────────────────────────┘
```

## Key Points

1. **KSP is annotation-based:** It scans for `@GraphView` and `@NodeFragment` annotations
2. **Gradle reads parent sources:** The `codegen-gradle` build references `../src/main/kotlin`
3. **Maven orchestrates:** Maven runs Gradle during `generate-sources` phase
4. **Generated code is included:** `build-helper-maven-plugin` adds generated sources to build
5. **Type-safe queries:** Generated DSL provides compile-time safety for graph queries

## Troubleshooting

**If generated files are missing:**

1. Check that `codegen-gradle/gradlew` exists
2. Run manually: `cd codegen-gradle && ./gradlew kspKotlin`
3. Check Maven profile: `codegen-available` should activate if `gradlew` exists
4. Verify `codegen.skip` is `false` when profile is active

**If generation fails:**

1. Check KSP processor version matches `drivine4j` version (both should be `0.0.12`)
2. Verify Kotlin version is `2.2.0` (required for KSP `2.2.20-2.0.4`)
3. Check that source files have correct annotations
4. Review Gradle build output for errors
