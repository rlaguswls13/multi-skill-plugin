package com.msp.starter.observability;

import com.msp.auditlog.AuditEvent;
import com.msp.auditlog.AuditFormat;
import com.msp.auditlog.AuditLogger;
import com.msp.exanalyzer.ExceptionAnalyzer;
import com.msp.health.HealthChecker;
import com.msp.health.HealthRegistry;
import com.msp.logformat.LogEvent;
import com.msp.logformat.StructuredLogFormatter;
import com.msp.profiler.Profiler;
import com.msp.slowquery.SlowQueryTracker;
import com.msp.trace.TraceContext;
import com.msp.trace.TraceIdPropagator;

import java.time.Duration;

/**
 * 분산 추적, 감사 로그, 슬로우 쿼리, 예외 분석, 헬스체크, 구조화 로그, 프로파일러 종합 관측성 스타터 파사드.
 */
public final class ObservabilityKit {

    private ObservabilityKit() {}

    public static String currentTraceId() {
        return TraceIdPropagator.current().map(TraceContext::traceId).orElse(null);
    }

    public static AutoCloseable openTraceScope(TraceContext context) {
        return TraceIdPropagator.open(context);
    }

    public static Throwable getRootCause(Throwable throwable) {
        return ExceptionAnalyzer.getRootCause(throwable);
    }

    public static Profiler startProfiler(String name) {
        return Profiler.start(name);
    }

    public static SlowQueryTracker createSlowQueryTracker(Duration threshold) {
        return new SlowQueryTracker(threshold);
    }

    public static HealthChecker createHealthChecker(HealthRegistry registry) {
        return new HealthChecker(registry);
    }

    public static String formatStructuredLog(LogEvent event) {
        return StructuredLogFormatter.compact().format(event);
    }
}
