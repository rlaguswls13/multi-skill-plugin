# skill-cron-sla-window-miss-detector

Scheduled Job SLA Window Breach & Missed Execution Detector with Catch-up Policy Evaluator in pure Java 21.

## Architecture Highlights
- **Per-Job SLA Policy Registration**: Configures `expectedInterval`, `slaGraceWindow`, and `CatchUpPolicy` (`FIRE_ONCE_IMMEDIATELY`, `FIRE_ALL_MISSED`, `SKIP_TO_NEXT`, `ALERT_ONLY`).
- **Slot Classification**: Compares expected schedule timestamps against actual execution heartbeats to classify each slot as `ON_TIME`, `LATE_SLA_BREACH` (started after `scheduledTime + slaGraceWindow`), or `MISSED`.
- **Actionable Remediation Report**: Generates `SlaEvaluationReport` with SLA compliance percentage, missed/late breakdowns, and concrete `CatchUpTrigger` actions.

## Usage Example

```java
import com.msp.cronsla.*;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

CronSlaDetector detector = new CronSlaDetector();
detector.registerPolicy(new JobSlaPolicy(
        "hourly-settlement",
        Duration.ofHours(1),
        Duration.ofMinutes(5),
        CatchUpPolicy.FIRE_ONCE_IMMEDIATELY
));

Instant t0 = Instant.parse("2026-11-19T00:00:00Z");
List<Instant> expected = CronSlaDetector.generatePeriodicSchedule(t0, t0.plus(Duration.ofHours(2)), Duration.ofHours(1));
List<Instant> actuals = List.of(t0.plus(Duration.ofMinutes(2))); // 01:00 and 02:00 missed

SlaEvaluationReport report = detector.evaluate("hourly-settlement", expected, actuals, t0.plus(Duration.ofHours(3)));
System.out.println("Compliance: " + report.slaComplianceRatePercent() + "%");
```
