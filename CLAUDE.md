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
Tracer                         <- Manages circular buffer of TraceLoops
        │
        ▼
TraceExporter                  <- Exports to Chrome Tracing JSON format
```

## Key Files

| File | Purpose |
|------|---------|
| `Traced.java` | `@Traced` annotation for methods (supports `category` attribute) |
| `Tracer.java` | Core API: `beginLoop()`, `endLoop()`, `beginSpan()`, `endSpan()` |
| `TraceLoop.java` | Container for one robot loop iteration (holds TraceSpans) |
| `TraceSpan.java` | Single traced method call with timing, thread, and category info |
| `TraceScope.java` | AutoCloseable for try-with-resources manual tracing |
| `TraceExporter.java` | Exports buffer to Chrome Tracing JSON |
| `TracingBootstrap.java` | ByteBuddy agent installation (includes Command tracing) |
| `TracingInterceptor.java` | ByteBuddy method delegation target for @Traced |
| `CommandTracingInterceptor.java` | ByteBuddy delegation target for Command lifecycle |

## Design Decisions

- **Pre-allocated circular buffer**: 500 loops x 256 spans to avoid GC during robot loop
- **ByteBuddy over AspectJ**: AspectJ conflicts with GradleRIO; ByteBuddy self-attaches at runtime
- **Thread detection**: Captures `Thread.currentThread().getId()` and `getName()` to separate Notifier threads
- **Chrome Tracing JSON**: Compatible with Perfetto UI and chrome://tracing
- **Subsystem categories**: Spans can be categorized (Drivetrain, Vision, etc.) for Perfetto filtering
- **Command integration**: Auto-traces WPILib Command lifecycle (initialize, execute, isFinished, end)

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
    TracingBootstrap.installCommandTracing(); // Optional: auto-trace Commands
    RobotBase.startRobot(Robot::new);
}

// Robot.java - loop boundaries
public void robotPeriodic() {
    Tracer.beginLoop();
    // robot code...
    Tracer.endLoop();
}

// Any method - add annotation
@Traced
public void periodic() { ... }

// With subsystem category for better organization in Perfetto
@Traced(category = "Drivetrain")
public void drivetrainPeriodic() { ... }

// Both custom name and category
@Traced(value = "ProcessTargets", category = "Vision")
private void processVisionTargets() { ... }

// Manual tracing with category
try (var t = Tracer.trace("calculate", "Shooter")) {
    // timed operation
}

// Export when disabled
Tracer.exportToJson("trace_" + System.currentTimeMillis() + ".json");
```

## Output Format

Exports to Chrome Tracing JSON with:
- **Loop Duration counter**: Shows ms per loop
- **LoopOverruns track**: Markers for loops > 20ms
- **LoopMarkers track**: Loop boundary indicators
- **Per-thread tracks**: Main thread and Notifier threads separated
- **Category filtering**: Filter by subsystem (Drivetrain, Vision, Command, etc.) in Perfetto

View traces at [Perfetto UI](https://ui.perfetto.dev)

## Performance Targets

- Span overhead: ~100ns per traced method
- Memory: ~100KB for 500-loop buffer
- Zero allocations during normal operation
