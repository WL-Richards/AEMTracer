# AEMTracer

A lightweight, high-performance tracing library for FRC robots. Generate timeline visualizations of your robot code execution with minimal overhead.

View your traces in [Perfetto UI](https://ui.perfetto.dev)

## Features

- **Annotation-based tracing** - Just add `@Traced` to any method
- **Subsystem categories** - Group traces by subsystem (Drivetrain, Vision, etc.) for filtering in Perfetto
- **Command framework integration** - Auto-trace WPILib Command lifecycle methods
- **Zero-allocation design** - Pre-allocated circular buffer avoids GC pauses
- **Multi-thread support** - Automatically detects and separates Notifier threads
- **Loop overrun detection** - Highlights frames exceeding the 20ms budget
- **Perfetto-compatible output** - View traces in [Perfetto UI](https://ui.perfetto.dev) or Chrome's tracing viewer
- **Minimal overhead** - Designed for real-time robot control loops

## Concepts

### Frames

A **frame** represents one iteration of your robot's main loop (`robotPeriodic()`). FRC robots typically run at 50Hz, so each frame is ~20ms. The tracer uses a circular buffer of 500 frames (~10 seconds of history).

You mark frame boundaries explicitly:

```java
public void robotPeriodic() {
    Tracer.beginFrame();   // Start of this loop iteration
    // ... robot code ...
    Tracer.endFrame();     // End of this loop iteration
}
```

### Spans

A **span** is a timed section of code within a frame. Each span captures:
- Start and end timestamps
- Method/section name
- Thread ID (to separate main thread from Notifier threads)
- Optional category (e.g., "Drivetrain", "Vision")

Multiple spans nest within each frame, creating a timeline of what executed during that loop iteration.

```
Frame 0 ─┬─ DriveSubsystem.periodic()  [0.2ms]
         ├─ ShooterSubsystem.periodic() [0.5ms]
         │   └─ calculateTrajectory()   [0.3ms]
         └─ VisionSubsystem.periodic()  [1.1ms]

Frame 1 ─┬─ DriveSubsystem.periodic()  [0.2ms]
         └─ ...
```

## Installation

### Using JitPack (Recommended)

Add the JitPack repository to your `build.gradle`:

```gradle
repositories {
    maven { url 'https://jitpack.io' }
}
```

Add the dependency:

```gradle
dependencies {
    implementation 'com.github.aembot:AEMTracer:1.0.0'
}
```

### Local Installation

1. Clone this repository
2. Run `./gradlew publishToMavenLocal`
3. Add to your project:

```gradle
repositories {
    mavenLocal()
}

dependencies {
    implementation 'com.aembot.lib:AEMTracer:1.0.0'
}
```

## Quick Start

### 1. Initialize the tracer in `Main.java`

```java
import com.aembot.lib.tracing.TracingBootstrap;

public final class Main {
    public static void main(String... args) {
        // Install BEFORE any @Traced classes are loaded
        TracingBootstrap.install();

        // Optional: Auto-trace all Command lifecycle methods
        TracingBootstrap.installCommandTracing();

        RobotBase.startRobot(Robot::new);
    }
}
```

### 2. Add frame boundaries in `Robot.java`

```java
import com.aembot.lib.tracing.Tracer;

@Override
public void robotPeriodic() {
    Tracer.beginFrame();

    // Your robot code here...

    Tracer.endFrame();
}
```

### 3. Annotate methods to trace

Adding `@Traced` to a method creates a span each time it's called:

```java
import com.aembot.lib.tracing.Traced;

@Traced
public void periodic() {
    updateInputs();
    runStateMachine();
    updateOutputs();
}

@Traced("CustomName")
private void updateInputs() {
    // Method body - timing is automatic
}

// Use categories to group spans by subsystem in Perfetto
@Traced(category = "Drivetrain")
public void drivetrainPeriodic() { ... }

@Traced(value = "ProcessTargets", category = "Vision")
private void processVisionTargets() { ... }
```

### 4. Export traces

```java
@Override
public void disabledInit() {
    // Export when robot is disabled
    Tracer.exportToJson("trace_" + System.currentTimeMillis() + ".json");
}
```

### 5. View in Perfetto

1. Open [Perfetto UI](https://ui.perfetto.dev)
2. Drag and drop your `trace_*.json` file
3. Explore the timeline!

![Perfetto UI showing a trace](docs/normal_trace.png)

## Manual Tracing (Alternative)

If you prefer not to use annotations, you can manually trace code blocks:

```java
// Basic tracing
try (var t = Tracer.trace("MyClass.myMethod")) {
    // Code to trace
}

// With category for filtering in Perfetto
try (var t = Tracer.trace("calculateTrajectory", "Shooter")) {
    // Code to trace - will appear under "Shooter" category
}
```

## Command Framework Integration

Enable automatic tracing of all WPILib Command lifecycle methods:

```java
// In Main.java, after TracingBootstrap.install()
TracingBootstrap.installCommandTracing();
```

This automatically traces `initialize()`, `execute()`, `isFinished()`, and `end()` on all Command subclasses. Traces appear under the "Command" category in Perfetto.

No changes to your Command classes are required - it works via bytecode instrumentation.

![Perfetto UI showing traced Commands](docs/commands_traced.png)

## Configuration

### Disable for Competition

```java
// In robotInit() or autonomousInit()
if (DriverStation.isFMSAttached()) {
    Tracer.setEnabled(false);
}
```

### Buffer Size

The default buffer holds 500 frames (~10 seconds at 50Hz). This is configured in `Tracer.java`.

## Output Format

Traces are exported in [Chrome Tracing JSON format](https://docs.google.com/document/d/1CvAClvFfyA5R-PhYUmn5OOQtYMH4h6I0nSsKchNAySU/preview), compatible with:

- [Perfetto UI](https://ui.perfetto.dev) (recommended)
- Chrome's built-in tracer (`chrome://tracing`)
- Custom visualization tools

### Track Layout

| Track | Description |
|-------|-------------|
| `Frame Duration (ms)` | Counter showing frame timing |
| `LoopOverruns` | Markers for frames exceeding 20ms |
| `FrameMarkers` | Frame boundary indicators |
| `robot main` | Main robot thread spans |
| `Notifier-*` | Notifier thread spans |

### Category Filtering

Use Perfetto's category filter to show/hide spans by subsystem:
- `robot` - Default category for uncategorized spans
- `Command` - Command lifecycle methods (if command tracing enabled)
- Custom categories (e.g., `Drivetrain`, `Vision`, `Shooter`)

## Performance

- **Span overhead**: ~100ns per traced method
- **Memory**: ~50KB for default 500-frame buffer
- **No allocations** during normal operation

## Requirements

- Java 17+
- WPILib 2024+
- ByteBuddy (included as transitive dependency)

## License

MIT License - See [LICENSE](LICENSE) for details.

## Contributing

Contributions welcome! Please open an issue or PR on GitHub.

---

Made with :heart: by AEMBOT
