# Original Changes That Led to Build Failure

This document describes the sequence of changes that introduced the `ApplicationContext` loading failure in tests.

## Timeline of Changes

### Commit: `012d2bd` - "ContentElementRepository no longer has a count method"
**Date:** Before the current failure  
**Author:** Unknown

**Problem Introduced:**
- The `ContentElementRepository` interface removed the `count()` method
- This broke `DataManager.getStats()` which was using `store.count()`

**Files Changed:**
- `src/main/java/com/embabel/guide/rag/DataManager.java`

**Original Code (Before):**
```java
public Stats getStats() {
    int chunkCount = store.count();
    return new Stats(chunkCount);
}
```

**After `count()` was removed:**
```java
//    public Stats getStats() {
//        int chunkCount = store.count();  // COMMENTED OUT - method no longer exists
//        return new Stats(chunkCount);
//    }
```

---

### Commit: `0c3311f` - "Use DrivineStore::info() for stats endpoint"
**Date:** Sat Dec 27 16:21:30 2025 +1100  
**Author:** jasper blues

**Changes Made:**

1. **Changed dependency type in `DataManager`:**
   - **Before:** `ChunkingContentElementRepository store`
   - **After:** `DrivineStore store`

2. **Uncommented and updated `getStats()` method:**
   - **Before:** Used `store.count()` (commented out)
   - **After:** Uses `store.info()` and extracts stats from `ContentElementRepositoryInfo`

3. **Updated `Stats` record:**
   - **Before:** `Stats(int chunks)`
   - **After:** `Stats(int chunkCount, int documentCount, int contentElementCount)`

**Files Changed:**
- `src/main/java/com/embabel/guide/rag/DataManager.java`
- `src/main/java/com/embabel/guide/rag/DataManagerController.java`

**Diff for `DataManager.java`:**
```diff
-import com.embabel.agent.rag.store.ChunkingContentElementRepository;
+import com.embabel.agent.rag.neo.drivine.DrivineStore;

  public record Stats(
-            int chunks) {
+            int chunkCount,
+            int documentCount,
+            int contentElementCount) {
  }

-    private final ChunkingContentElementRepository store;
+    private final DrivineStore store;

  public DataManager(
-            ChunkingContentElementRepository store,
+            DrivineStore store,
             GuideProperties guideProperties
  ) {
      // ...
  }

+    public Stats getStats() {
+        var info = store.info();
+        return new Stats(info.getChunkCount(), info.getDocumentCount(), info.getContentElementCount());
+    }
```

---

### Existing: `ChatActions` Dependency on `DrivineStore`
**Commit:** `6d7d892` - "Add agent bot" (Sep 10, 2025)  
**Author:** Rod Johnson

**Context:**
- `ChatActions` was already created with a dependency on `DrivineStore`
- Constructor requires `DrivineStore drivineStore` parameter
- Used for RAG operations with `ToolishRag` and `TryHyDE`

**File:** `src/main/java/com/embabel/guide/ChatActions.java`

```java
public class ChatActions {
    private final DataManager dataManager;
    private final DrivineGuideUserRepository guideUserRepository;
    private final GuideProperties guideProperties;
    private final DrivineStore drivineStore;  // <-- Direct dependency

    public ChatActions(
            DataManager dataManager,
            DrivineGuideUserRepository guideUserRepository,
            DrivineStore drivineStore,  // <-- Required parameter
            GuideProperties guideProperties) {
        // ...
    }
}
```

---

## The Cascade Effect

### Dependency Chain
```
Spring ApplicationContext
  └─> ChatActions (requires DrivineStore)
  └─> DataManager (requires DrivineStore)
      └─> DrivineStore.info() returns ContentElementRepositoryInfo
```

### Why Tests Failed

**Before Commit `0c3311f`:**
- `DataManager` used `ChunkingContentElementRepository` (likely auto-configured or provided by test infrastructure)
- `getStats()` was commented out (not called during tests)
- Tests could load `ApplicationContext` successfully

**After Commit `0c3311f`:**
1. `DataManager` now requires `DrivineStore` bean
2. `ChatActions` already requires `DrivineStore` bean  
3. Spring tries to create both beans during test context loading
4. **Problem:** No `DrivineStore` bean is provided in test context
5. **Result:** `UnsatisfiedDependencyException: No qualifying bean of type 'DrivineStore'`

### Test Configuration Gap

**What Was Missing:**
- Tests had no `DrivineStore` bean definition
- `DrivineStore` is a complex bean that requires:
  - `PersistenceManager`
  - List of `RetrievableEnhancer`
  - `NeoRagServiceProperties`
  - `CypherSearch`
  - `ContentElementMapper`
  - `ModelProvider`
  - `PlatformTransactionManager`
- Setting up all these dependencies for tests would be complex and unnecessary

**Why Mocking Was Chosen:**
- Tests don't actually need a working RAG store
- They only need the `DrivineStore` bean to exist
- Mocking `provision()` and `info()` is sufficient for most tests
- Keeps tests fast and isolated

---

## Summary of Root Causes

1. **API Change:** `ContentElementRepository.count()` was removed, breaking existing code
2. **Migration:** `DataManager` migrated from `ChunkingContentElementRepository` to `DrivineStore`
3. **Dependency Introduction:** Both `ChatActions` and `DataManager` now require `DrivineStore`
4. **Missing Test Configuration:** No test configuration provided a `DrivineStore` bean
5. **Test Isolation:** Tests were trying to load full application context without necessary beans

## The Fix

Created `TestDrivineStoreConfiguration` to:
- Provide a mock `DrivineStore` bean for tests
- Mock only the methods actually used (`provision()`, `info()`)
- Allow tests to load `ApplicationContext` without full RAG infrastructure
- Use TestContainers for Neo4j instead of requiring local instance

This allows tests to run in isolation without setting up the entire RAG infrastructure.

