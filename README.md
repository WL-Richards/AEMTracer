# AEMTracer

A lightweight, high-performance tracing library for FRC robots. Generate timeline visualizations of your robot code execution with minimal overhead.

![Perfetto Timeline](https://ui.perfetto.dev/assets/brand.png)

## Features

- **Annotation-based tracing** - Just add `@Traced` to any method
- **Zero-allocation design** - Pre-allocated circular buffer avoids GC pauses
- **Multi-thread support** - Automatically detects and separates Notifier threads
- **Loop overrun detection** - Highlights frames exceeding the 20ms budget
- **Perfetto-compatible output** - View traces in [Perfetto UI](https://ui.perfetto.dev) or Chrome's tracing viewer
- **Minimal overhead** - Designed for real-time robot control loops

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

## Manual Tracing (Alternative)

If you prefer not to use annotations, you can manually trace code blocks:

```java
try (var t = Tracer.trace("MyClass.myMethod")) {
    // Code to trace
}
```

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
