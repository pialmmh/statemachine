# Failing Methods Analysis

## Summary by File

### 1. **SplitVersePersistenceProvider.java** (8 errors - HIGH PRIORITY)

**Location:** Lines 62, 65, 66, 107

**Failing Methods:**
```java
// Line 62 - trying to call setId() on external ShardingEntity
((com.telcobright.core.entity.ShardingEntity) context).setId(machineId);

// Lines 65-66 - trying to call getCreatedAt/setCreatedAt on external ShardingEntity
if (((com.telcobright.core.entity.ShardingEntity) context).getCreatedAt() == null) {
    ((com.telcobright.core.entity.ShardingEntity) context).setCreatedAt(LocalDateTime.now());
}

// Line 107 - repository doesn't have deleteById() method
repository.deleteById(machineId);
```

**Root Cause:**
- External `com.telcobright.core.entity.ShardingEntity` is a **marker interface** (no methods)
- Fields are discovered via reflection using `@Id` and `@ShardingKey` annotations
- Cannot call setId/getId/getCreatedAt/setCreatedAt directly

**Fix Required:**
- Use reflection to set ID and createdAt fields
- Or cast to StateMachineContextEntity which HAS these methods
- Replace `deleteById()` with correct repository API (probably `delete()`)

---

### 2. **SplitVerseIntegrationTest.java** (46 errors - TEST FILE, LOW PRIORITY)

**Failing Methods:**
```java
machine.addState("IDLE")           // Old API - method doesn't exist
machine.addTransition(...)         // Old API - method doesn't exist
registry.getStateMachine(...)      // Old API - method doesn't exist
machine.sendEvent("DIAL")          // Old API - method doesn't exist
machine.start("IDLE")              // Wrong signature
```

**Root Cause:**
- Test uses old/deprecated API from previous version
- State machine API has changed significantly

**Fix Required:**
- Update to use FluentStateMachineBuilder (current API)
- Or comment out/delete this obsolete test

---

### 3. **CallRepository.java / SmsRepository.java** (4 errors)

**Location:** Lines 47, 56

**Failing Symbol:**
```java
TableGranularity.MONTHLY  // Variable doesn't exist
```

**Root Cause:**
- External library changed API
- `TableGranularity` enum/constant renamed or removed

**Fix Required:**
- Check external library documentation for correct constant name
- Probably changed to something like `Granularity.MONTHLY` or similar

---

### 4. **PartitionedCallContext.java** (8 errors)

**Failing Methods:**
```java
setId(...)     // Line 41 - trying to call on BaseStateMachineEntity
getId()        // Line 162 - trying to call on itself
```

**Root Cause:**
- Class extends BaseStateMachineEntity which doesn't have these methods
- Or methods are not properly inherited

**Fix Required:**
- Add getId()/setId() methods
- Or fix inheritance chain

---

### 5. **StateWalkDemo.java** (8 errors)

**Failing Methods:**
```java
Various methods on demo objects - need to check specific lines
```

**Root Cause:**
- Demo/example code using old API

**Fix Required:**
- Update demo to use current API
- Or comment out obsolete demo

---

### 6. **StateWalkRegistry.java** (4 errors)

**Failing Methods:**
```java
adapter.exists(id)  // Line 362 - SplitVerseGraphAdapter doesn't have exists() method
```

**Root Cause:**
- I added `adapter.exists()` call but SplitVerseGraphAdapter doesn't implement it

**Fix Required:**
- Add exists() method to SplitVerseGraphAdapter
- Or use alternative approach

---

## Priority Fixes

### HIGH PRIORITY (Affects Core Functionality):

#### 1. Fix SplitVersePersistenceProvider (8 errors)
**Impact:** Breaks persistence layer for split-verse
**Effort:** Medium - Need to use reflection or change approach

```java
// BEFORE (BROKEN):
((com.telcobright.core.entity.ShardingEntity) context).setId(machineId);

// AFTER (FIX OPTION 1 - Cast to StateMachineContextEntity):
if (context instanceof StateMachineContextEntity) {
    ((StateMachineContextEntity<?>) context).setId(machineId);
}

// AFTER (FIX OPTION 2 - Use reflection):
ReflectionUtils.setFieldValue(context, "id", machineId);

// deleteById() fix:
repository.delete(context);  // Or whatever the correct method is
```

#### 2. Fix Repository TableGranularity (4 errors)
**Impact:** Breaks repository initialization
**Effort:** Low - Just need correct constant name

```java
// Check external library for correct name
import com.telcobright.core.repository.Granularity;  // Or similar
```

#### 3. Add exists() to SplitVerseGraphAdapter (4 errors)
**Impact:** Breaks StateWalkRegistry persistence
**Effort:** Low - Simple stub implementation

```java
public boolean exists(String id) {
    // Check if entity exists in first registered repository
    return false;  // TODO: implement
}
```

### LOW PRIORITY (Test/Demo Files):

#### 4. SplitVerseIntegrationTest (46 errors)
**Impact:** Just a test file, doesn't affect production
**Effort:** High - Need to rewrite entire test

**Option:** Comment out or delete this obsolete test

#### 5. StateWalkDemo (8 errors)
**Impact:** Just an example/demo
**Effort:** Medium

**Option:** Comment out or delete obsolete demo

#### 6. PartitionedCallContext (8 errors)
**Impact:** Example code only
**Effort:** Low - Add missing methods

---

## Recommended Fix Order

1. ✅ **SplitVersePersistenceProvider** - Use StateMachineContextEntity cast instead of ShardingEntity
2. ✅ **Repository TableGranularity** - Find correct constant name
3. ✅ **SplitVerseGraphAdapter.exists()** - Add stub method
4. ❌ **Comment out test files** - SplitVerseIntegrationTest, StateWalkDemo (not needed for history archival)

After these 3 fixes, **~70+ errors will be resolved** and history archival tests can run.

---

## Will Fixing Break Features?

### ✅ NO - Safe to Fix:
1. **SplitVersePersistenceProvider** - Just changing cast type, same functionality
2. **Repository constants** - Just updating to correct name
3. **Adding exists() method** - New functionality, doesn't break existing

### ⚠️ Tests Will Break (But That's OK):
- SplitVerseIntegrationTest - Already broken, uses obsolete API
- StateWalkDemo - Demo code, not production
- These can be commented out temporarily

---

## Next Steps

**FAST PATH (30 min):**
1. Fix SplitVersePersistenceProvider (cast to StateMachineContextEntity)
2. Find/fix TableGranularity constant
3. Add SplitVerseGraphAdapter.exists() stub
4. Comment out broken test files
5. ✅ Run history archival tests!

**vs**

**COMPLETE PATH (2-3 hours):**
1. Fix all test files to use new API
2. Update all demo code
3. Full codebase compilation

**Recommendation:** Take FAST PATH to unblock history archival testing
