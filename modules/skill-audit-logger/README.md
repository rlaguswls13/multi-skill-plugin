# skill-audit-logger

Lightweight, zero-dependency audit logging library for Java 21 with sensitive data masking, before-and-after difference tracking, and JSON / Key-Value formatting.

## Features
- **Structured Audit Events**: Immutable record `AuditEvent` with actor, action, resource, clientIp, status, details, and diffs.
- **Before/After Diff Tracking**: Track property changes with `FieldDiff`.
- **Sensitive Data Masking**:
  - Automatically masks common sensitive keys (`password`, `token`, `secret`, `apiKey`, `cardnumber`, `cvv`, etc.).
  - Regex pattern masking for credit cards, Korean resident registration numbers (RRN), phone numbers, and email addresses.
- **Multiple Formats**: JSON serializer and Key-Value log output.
- **Zero Runtime Dependencies**: 100% pure Java 21 standard library.

## Quick Start

```java
import com.msp.auditlog.*;

// Create logger sending JSON logs to System.out or custom sink
AuditLogger logger = AuditLogger.json(System.out::println);

// Build and log an audit event
AuditEvent event = AuditEvent.builder()
        .actor("admin@company.com")
        .action("USER_UPDATE")
        .resource("User/1024")
        .clientIp("10.0.0.1")
        .status("SUCCESS")
        .detail("password", "PlainPasswordToMask")
        .detail("phone", "010-1234-5678")
        .diff("role", "USER", "ADMIN")
        .build();

logger.log(event);
// Emits JSON with passwords and phone numbers masked:
// {"id":"...","actor":"ad***@company.com","details":{"password":"******","phone":"010-****-5678"},...}
```
