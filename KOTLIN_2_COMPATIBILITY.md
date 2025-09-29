# Kotlin 2.0+ Compatibility for Store

## Store v5 Status ✅

**Store v5 (current version) already has full Kotlin 2.0+ compatibility.** The Duration API issues were resolved as part of the Store v5.0.0 release (see CHANGELOG.md: "Removal of experimental duration APIs").

Store v5 uses the correct, non-deprecated Duration APIs:
- `import kotlin.time.Duration.Companion.hours`
- `24.hours`, `1.hours`, `100.milliseconds` etc.

## Store v4 Migration Guide

If you're using Store v4 and experiencing Kotlin 2.0+ compatibility issues, you have these options:

### Option 1: Upgrade to Store v5 (Recommended)
Store v5 has no breaking API changes from Store v4 and includes many improvements:
- Full Kotlin 2.0+ compatibility
- MutableStore support
- Validator functionality
- Fallback mechanisms
- Better KMP support

### Option 2: Manual Store v4 Patches

If you must stay on Store v4, apply these changes to fix Kotlin 2.0+ compatibility:

#### Issue: Deprecated Duration.Companion imports
**Problem:** `import kotlin.time.Duration.Companion.hours` usage may cause issues

**Solution:** Replace with proper extension imports:
```kotlin
// Before (if problematic in your Store v4)
import kotlin.time.Duration.Companion.hours

// After
import kotlin.time.Duration.Companion.hours  // This is actually correct
```

#### Issue: Deprecated Duration constructor functions
**Problem:** If your Store v4 uses `Duration.hours(24)` syntax

**Solution:** Replace with extension properties:
```kotlin
// Before
Duration.hours(24)
Duration.minutes(30)
Duration.seconds(10)

// After
24.hours
30.minutes
10.seconds
```

#### Key Files to Check in Store v4:
- Search for `Duration.hours(`, `Duration.minutes(`, etc.
- Replace with `N.hours`, `N.minutes` extension syntax
- Ensure imports use `kotlin.time.Duration.Companion.hours` pattern

### Option 3: Use Store v5 Compatibility Layer

Store v5 maintains API compatibility with Store v4. Simply update your dependencies:

```kotlin
// Gradle
implementation("org.mobilenativefoundation.store:store5:5.1.0")

// No code changes needed - your Store v4 code will work as-is
```

## Verification

To verify Kotlin 2.0+ compatibility:
1. Ensure you're using Kotlin 2.0+
2. Build your project with the changes
3. Look for any remaining Duration-related compilation errors

## Support

If you encounter issues:
- Store v5 is the actively maintained version with full Kotlin 2.0+ support
- Consider migrating to Store v5 for best compatibility and ongoing support