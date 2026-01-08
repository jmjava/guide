# Where to Find More About Drivine DSL

This document points you to resources for learning about Drivine's type-safe query DSL.

## 📚 In This Codebase

### 1. **Generated DSL Code** (Best Starting Point)

**Location:** `codegen-gradle/build/generated/ksp/main/kotlin/com/embabel/guide/domain/`

**Files to examine:**
- **`GuideUserQueryDsl.kt`** - Generated query DSL for `GuideUser` GraphView
- **`AnonymousGuideUserQueryDsl.kt`** - Generated query DSL for `AnonymousGuideUser`
- **`GeneratedProperties.kt`** - Property reference classes for each `@NodeFragment`

**What to look for:**
- How the DSL is structured
- Available property references (e.g., `core`, `webUser`, `discordUserInfo`)
- Context receiver functions for `where` and `order` clauses

### 2. **Usage Examples in Code**

**Primary Example:** `src/main/kotlin/com/embabel/guide/domain/GraphObjectGuideUserRepository.kt`

This file shows real-world usage of the generated DSL:

```kotlin
// Simple query with where clause
graphObjectManager.loadAll<GuideUser> {
    where {
        query.webUser.id eq webUserId
    }
}

// Query with property reference
graphObjectManager.loadAll<GuideUser> {
    where {
        query.discordUserInfo.id eq discordUserId
    }
}

// Query with string operations
graphObjectManager.deleteAll<GuideUser> {
    where {
        query.webUser.userName startsWith prefix
    }
}

// Empty query (load all)
graphObjectManager.loadAll<GuideUser> { }
```

**Key imports:**
```kotlin
import org.drivine.manager.GraphObjectManager
import org.drivine.query.dsl.*
```

### 3. **Source Annotations** (How DSL is Generated)

**Location:** `src/main/kotlin/com/embabel/guide/domain/`

**Files to examine:**
- **`GuideUser.kt`** - `@GraphView` annotation example
- **`GuideUserData.kt`** - `@NodeFragment` annotation example
- **`WebUserData.kt`** - `@NodeFragment` with properties
- **`DiscordUserInfoData.kt`** - `@NodeFragment` example

**Key annotations:**
- `@GraphView` - Marks a class as a graph view (generates QueryDsl)
- `@NodeFragment` - Marks a class as a node fragment (generates Properties)
- `@Root` - Marks the root node in a GraphView
- `@GraphRelationship` - Defines relationships between nodes

### 4. **Test Examples**

**Location:** `src/test/kotlin/com/embabel/guide/domain/drivine/`

**Files:**
- `GraphObjectGuideUserRepositoryTest.kt` - Tests using the DSL
- `DrivineGuideUserRepositoryTest.kt` - Comparison with raw Cypher queries

## 🔍 Key Classes and Packages

### Drivine DSL Package
```
org.drivine.query.dsl.*
```

**Key classes:**
- `GraphQuerySpec` - Query specification builder
- `WhereBuilder` - Where clause builder
- `OrderBuilder` - Order by clause builder
- `GraphObjectManager` - Manager for executing DSL queries

### Generated DSL Classes
```
com.embabel.guide.domain.GuideUserQueryDsl
com.embabel.guide.domain.AnonymousGuideUserQueryDsl
```

**Usage pattern:**
```kotlin
graphObjectManager.loadAll<GuideUser> {
    where {
        query.webUser.id eq userId  // 'query' is GuideUserQueryDsl.INSTANCE
    }
}
```

## 📖 External Resources

### 1. **Drivine GitHub Repository**

**URL:** `https://github.com/drivine/drivine4j` (or similar)

**What to look for:**
- README with DSL examples
- Documentation/wiki
- Example projects
- API documentation

### 2. **Maven Central / Artifacts**

**Group ID:** `org.drivine`
**Artifacts:**
- `drivine4j` - Core library
- `drivine4j-codegen` - KSP processor
- `drivine4j-spring-boot-starter` - Spring Boot integration

**Check Javadoc/KDoc:**
- Maven Central often links to documentation
- Look for package `org.drivine.query.dsl`

### 3. **Spring Boot Starter Documentation**

Since you're using `drivine4j-spring-boot-starter`, check:
- Spring Boot auto-configuration documentation
- Configuration properties
- Example configurations

## 🎯 Common DSL Patterns

### Basic Query
```kotlin
graphObjectManager.loadAll<GuideUser> {
    where {
        query.core.id eq userId
    }
}
```

### Multiple Conditions
```kotlin
graphObjectManager.loadAll<GuideUser> {
    where {
        query.webUser.userName eq userName
        // Add more conditions with 'and' / 'or'
    }
}
```

### String Operations
```kotlin
// Starts with
query.webUser.userName startsWith prefix

// Contains
query.webUser.userName contains substring

// Equals
query.webUser.id eq userId
```

### Ordering
```kotlin
graphObjectManager.loadAll<GuideUser> {
    where { ... }
    orderBy {
        query.core.id ascending
    }
}
```

### Delete Operations
```kotlin
graphObjectManager.deleteAll<GuideUser> {
    where {
        query.webUser.userName startsWith prefix
    }
}
```

## 🔧 Understanding the Generated Code

### 1. **QueryDsl Class Structure**

From `GuideUserQueryDsl.kt`:
```kotlin
public class GuideUserQueryDsl {
  public val core: GuideUserDataProperties = GuideUserDataProperties("core")
  public val webUser: WebUserDataProperties = WebUserDataProperties("webUser")
  public val discordUserInfo: DiscordUserInfoDataProperties = ...
  
  public companion object {
    public val INSTANCE: GuideUserQueryDsl = GuideUserQueryDsl()
  }
}
```

### 2. **Property References**

From `GeneratedProperties.kt`:
```kotlin
public class GuideUserDataProperties(
  private val alias: String,
) : NodeReference {
  public val id: StringPropertyReference = ...
  public val persona: StringPropertyReference = ...
  public val customPrompt: StringPropertyReference = ...
}
```

### 3. **Context Receivers**

The DSL uses Kotlin context receivers:
```kotlin
context(builder: WhereBuilder<GuideUserQueryDsl>)
public val core: GuideUserDataProperties
    get() = builder.queryObject.core
```

This allows `query.core` syntax inside `where { }` blocks.

## 🚀 Next Steps

1. **Examine the generated files** - Start with `GuideUserQueryDsl.kt`
2. **Read the usage examples** - Look at `GraphObjectGuideUserRepository.kt`
3. **Check the source annotations** - See how `@GraphView` and `@NodeFragment` work
4. **Run the tests** - See DSL in action: `mvn test -Dtest=GraphObjectGuideUserRepositoryTest`
5. **Search Drivine documentation** - Look for official docs or GitHub repo

## 💡 Tips

- **Type safety:** The DSL provides compile-time checking - use IDE autocomplete!
- **Generated code:** Don't modify generated files - they're regenerated on each build
- **KSP processor:** The `drivine4j-codegen` dependency contains the code generator
- **Version matching:** Ensure `drivine4j` and `drivine4j-codegen` versions match

## 📝 Related Files in This Project

- `codegen-gradle/build.gradle.kts` - KSP configuration
- `pom.xml` - Maven build configuration for codegen
- `CODEGEN-GRADLE-PROCESS.md` - How code generation works
- `src/main/kotlin/com/embabel/guide/domain/GuideUser.kt` - GraphView definition
- `src/main/kotlin/com/embabel/guide/domain/GraphObjectGuideUserRepository.kt` - DSL usage

