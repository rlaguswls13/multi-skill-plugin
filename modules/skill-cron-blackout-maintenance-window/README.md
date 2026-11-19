# skill-cron-blackout-maintenance-window

Zero-dependency Java 21 **Maintenance Blackout Window Registry** with timezone-aware one-time, daily, and weekly windows, conflict detection, and cascading delayed execution calculation.

## Architecture Highlights
- **Sealed Window Model**: `OneTimeWindow`, `DailyWindow`, and `WeeklyWindow` with full `ZoneId` awareness, overnight wrap-around support (e.g., `23:00–02:00`), and DST-safe boundary evaluation
- **Thread-Safe Registry**: `ConcurrentHashMap`-backed `BlackoutRegistry` with deterministic sorted listing (`listWindows`, `activeWindows`)
- **Cascading Delay Resolution**: `nextAllowedTime(Instant)` iteratively advances past overlapping and back-to-back blackout windows with infinite-coverage protection
- **Flexible Policies**: `BlackoutPolicy.DELAY`, `BlackoutPolicy.SKIP`, and `BlackoutPolicy.REJECT`

## Usage Example
```java
import com.msp.blackout.*;
import java.time.*;
import java.util.Optional;
import java.util.Set;

BlackoutRegistry registry = new BlackoutRegistry();
registry.register(new BlackoutWindow.WeeklyWindow(
        "weekend-db-maint", "Weekly DB maintenance",
        Set.of(DayOfWeek.SUNDAY),
        LocalTime.of(1, 0), LocalTime.of(4, 0),
        ZoneId.of("Asia/Seoul")));

Instant requested = Instant.parse("2026-11-21T16:30:00Z"); // Sunday 01:30 KST
boolean blocked = registry.isInBlackout(requested);         // true
Optional<Instant> delayed = registry.schedule(requested, BlackoutPolicy.DELAY);
// Optional[2026-11-21T19:00:00Z] (Sunday 04:00 KST)
```
