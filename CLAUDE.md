# AEMTracer - Project Context

A lightweight, annotation-based tracing library for FRC robots. Generates Perfetto-compatible timeline visualizations.

## Architecture

```
TracingBootstrap.install()     <- Called in Main.main() before robot classes load
        │
        ▼
ByteBuddy Agent                <- Instruments @Traced methods at class load time
        │
        ▼
TracingInterceptor             <- Wraps method calls with Tracer.beginSpan/endSpan
        │
        ▼
Tracer                         <- Manages circular buffer of TraceFrames
        │
        ▼
TraceExporter                  <- Exports to Chrome Tracing JSON format
```

## Key Files

| File | Purpose |
|------|---------|
| `Traced.java` | `@Traced` annotation for methods |
| `Tracer.java` | Core API: `beginFrame()`, `endFrame()`, `beginSpan()`, `endSpan()` |
| `TraceFrame.java` | Container for one robot loop iteration (holds TraceSpans) |
| `TraceSpan.java` | Single traced method call with timing and thread info |
| `TraceScope.java` | AutoCloseable for try-with-resources manual tracing |
| `TraceExporter.java` | Exports buffer to Chrome Tracing JSON |
| `TracingBootstrap.java` | ByteBuddy agent installation |
| `TracingInterceptor.java` | ByteBuddy method delegation target |

## Design Decisions

- **Pre-allocated circular buffer**: 500 frames x 128 spans to avoid GC during robot loop
- **ByteBuddy over AspectJ**: AspectJ conflicts with GradleRIO; ByteBuddy self-attaches at runtime
- **Thread detection**: Captures `Thread.currentThread().getId()` and `getName()` to separate Notifier threads
- **Chrome Tracing JSON**: Compatible with Perfetto UI and chrome://tracing

## Build Commands

```bash
# Build the library
./gradlew build

# Publish to maven local for use in FRC projects
./gradlew publishToMavenLocal

# Clean build
./gradlew clean build
```

## Usage in FRC Projects

```gradle
repositories {
    mavenLocal()
}
dependencies {
    implementation 'com.aembot.lib:AEMTracer:1.0.0'
}
```

```java
// Main.java - install before robot classes load
public static void main(String... args) {
    TracingBootstrap.install();
    RobotBase.startRobot(Robot::new);
}

// Robot.java - frame boundaries
public void robotPeriodic() {
    Tracer.beginFrame();
    // robot code...
    Tracer.endFrame();
}

// Any method - add annotation
@Traced
public void periodic() { ... }

// Export when disabled
Tracer.exportToJson("trace_" + System.currentTimeMillis() + ".json");
```

## Output Format

Exports to Chrome Tracing JSON with:
- **Frame Duration counter**: Shows ms per frame
- **LoopOverruns track**: Markers for frames > 20ms
- **FrameMarkers track**: Frame boundary indicators
- **Per-thread tracks**: Main thread and Notifier threads separated

View traces at https://ui.perfetto.dev

## Performance Targets

- Span overhead: ~100ns per traced method
- Memory: ~50KB for 500-frame buffer
- Zero allocations during normal operation
