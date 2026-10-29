# skill-slow-query-interceptor

Zero-dependency slow database query interceptor, SQL normalizer, performance metrics aggregator, and threshold alert manager for Java 21.

## Features
- **SQL Normalization**: Automatically replaces literal values (strings, numbers, IN lists) with `?` parameters to aggregate execution metrics by query template.
- **Metrics Aggregation**: Tracks execution count, slow count, total/min/max/average execution duration, and last execution timestamp per normalized SQL.
- **Threshold Alerting**: Callback listener mechanism (`SlowQueryListener`) invoked whenever a query exceeds the configurable threshold (e.g., 500ms).
- **Execution Wrappers & Timers**: Supports `execute(Callable)`, `execute(Runnable)`, and try-with-resources `QueryTimer`.
- **Zero Runtime Dependencies**: Pure Java 21 standard library.

## Quick Start

```java
import com.msp.slowquery.*;
import java.time.Duration;

// 1. Initialize tracker with a 200ms threshold
SlowQueryTracker tracker = new SlowQueryTracker(Duration.ofMillis(200));

// 2. Register alert listener
tracker.addListener(alert -> {
    System.err.printf("[SLOW QUERY ALERT] Duration: %d ms | Query: %s%n",
            alert.duration().toMillis(), alert.rawSql());
});

// 3. Track query execution
try (SlowQueryTracker.QueryTimer timer = tracker.start("SELECT * FROM orders WHERE id = 1001")) {
    // perform database query
}

// 4. Inspect slowest queries
List<QueryStats> slowest = tracker.getSlowestQueries(5);
slowest.forEach(stat -> {
    System.out.printf("SQL: %s | Count: %d | Avg: %d ms%n",
            stat.normalizedSql(), stat.executionCount(), stat.avgDuration().toMillis());
});
```
