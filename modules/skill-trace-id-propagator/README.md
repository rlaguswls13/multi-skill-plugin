# skill-trace-id-propagator

High-performance, zero-dependency distributed trace context propagation library conforming to W3C TraceContext and Zipkin B3 specifications with `ThreadLocal` scope management and async wrappers for Java 21.

## Features
- **W3C TraceContext**: Full parsing & serialization for `traceparent` (`00-{traceId}-{spanId}-{flags}`).
- **Zipkin B3**: Support for both single header (`{traceId}-{spanId}-{sampled}-{parentSpanId}`) and multi-headers (`X-B3-TraceId`, etc.).
- **ThreadLocal Scope**: AutoCloseable `Scope` for clean `try-with-resources` lifecycle and nested span handling.
- **Async Propagation**: Functional wrappers (`wrap(Runnable)`, `wrap(Callable)`, `wrap(Supplier)`, etc.) to transfer context across threads.
- **Zero Dependencies**: Pure Java 21 implementation using `ThreadLocalRandom` and `HexFormat`.

## Quick Start

```java
import com.msp.trace.TraceContext;
import com.msp.trace.TraceIdPropagator;

// 1. Generate new trace context
TraceContext context = TraceContext.create();
System.out.println("TraceId: " + context.traceId());
System.out.println("W3C traceparent: " + context.toTraceParent());

// 2. Attach context to current thread scope
try (TraceIdPropagator.Scope scope = TraceIdPropagator.open(context)) {
    // Current thread has context
    TraceContext current = TraceIdPropagator.current().orElseThrow();
    
    // Create child span for downstream service
    TraceContext child = current.childSpan();
    String outgoingHeader = child.toTraceParent();
    
    // 3. Propagate to background thread
    Runnable task = TraceIdPropagator.wrap(() -> {
        System.out.println("Background TraceId: " + TraceIdPropagator.current().get().traceId());
    });
    new Thread(task).start();
}
// Context is automatically cleaned up after try-block exits
```
