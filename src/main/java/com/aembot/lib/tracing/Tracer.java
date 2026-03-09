package com.aembot.lib.tracing;

import edu.wpi.first.wpilibj.Timer;

/**
 * Lightweight tracing system for measuring function execution times. Uses a circular buffer of
 * pre-allocated loops to avoid GC during robot loop.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * // In robotPeriodic():
 * Tracer.beginLoop();
 * // ... robot code (traced via @Traced annotations)
 * Tracer.endLoop();
 *
 * // Export when disabled:
 * Tracer.exportToJson("/U/logs/trace.json");
 * }</pre>
 */
public final class Tracer {
  /** Number of loops to keep in circular buffer (~10 seconds at 50Hz) */
  private static final int BUFFER_SIZE = 500;

  /** Maximum nesting depth for traced calls */
  private static final int MAX_DEPTH = 32;

  /** Circular buffer of trace loops */
  private static final TraceLoop[] loops = new TraceLoop[BUFFER_SIZE];

  /** Current loop being written to */
  private static TraceLoop currentLoop;

  /** Index of current loop in circular buffer */
  private static int loopIndex = 0;

  /** Total loop count (for loop numbering) */
  private static int totalLoopCount = 0;

  /** Current nesting depth */
  private static byte currentDepth = 0;

  /** Whether tracing is enabled */
  private static boolean enabled = true;

  /** Static initializer - pre-allocate all loops */
  static {
    for (int i = 0; i < BUFFER_SIZE; i++) {
      loops[i] = new TraceLoop();
    }
    currentLoop = loops[0];
  }

  private Tracer() {} // Static only

  /**
   * Begin a new trace loop. Call this at the start of robotPeriodic().
   *
   * <p>This advances the circular buffer and resets the current loop.
   */
  public static void beginLoop() {
    if (!enabled) return;

    loopIndex = (loopIndex + 1) % BUFFER_SIZE;
    currentLoop = loops[loopIndex];
    currentLoop.reset();
    currentLoop.loopNumber = totalLoopCount++;
    currentDepth = 0;

    // Debug: log every 250 loops (~5 seconds)
    if (totalLoopCount % 250 == 0) {
      System.out.println(
          "[Tracer] Loop "
              + totalLoopCount
              + ", spans in last loop: "
              + getLastLoopSpanCount());
    }
  }

  /** Get span count from previous loop for debugging */
  private static int getLastLoopSpanCount() {
    int prevIndex = (loopIndex - 1 + BUFFER_SIZE) % BUFFER_SIZE;
    return loops[prevIndex].spanCount;
  }

  /**
   * End the current trace loop. Call this at the end of robotPeriodic().
   *
   * <p>Records the loop end time.
   */
  public static void endLoop() {
    if (!enabled) return;
    currentLoop.endTime = Timer.getFPGATimestamp();
  }

  /** Default category used when none is specified */
  public static final String DEFAULT_CATEGORY = "robot";

  /**
   * Create a trace scope for a function. Use with try-with-resources.
   *
   * <p>Usage:
   *
   * <pre>{@code
   * try (var t = Tracer.trace("MyClass.myMethod")) {
   *     // Method body - automatically timed
   * }
   * }</pre>
   *
   * @param name The name of the function/operation being traced
   * @return A TraceScope that will automatically end the span when closed
   */
  public static TraceScope trace(String name) {
    return new TraceScope(beginSpan(name, DEFAULT_CATEGORY));
  }

  /**
   * Create a trace scope for a function with a category. Use with try-with-resources.
   *
   * <p>Usage:
   *
   * <pre>{@code
   * try (var t = Tracer.trace("periodic", "Drivetrain")) {
   *     // Method body - automatically timed and categorized
   * }
   * }</pre>
   *
   * @param name The name of the function/operation being traced
   * @param category The category/subsystem (e.g., "Drivetrain", "Vision")
   * @return A TraceScope that will automatically end the span when closed
   */
  public static TraceScope trace(String name, String category) {
    return new TraceScope(beginSpan(name, category));
  }

  /**
   * Begin a trace span for a function call.
   *
   * @param name The name of the function/operation being traced
   * @return The span index (pass to endSpan), or -1 if tracing is disabled/full
   */
  public static int beginSpan(String name) {
    return beginSpan(name, DEFAULT_CATEGORY);
  }

  /**
   * Begin a trace span for a function call with a category.
   *
   * @param name The name of the function/operation being traced
   * @param category The category/subsystem (e.g., "Drivetrain", "Vision")
   * @return The span index (pass to endSpan), or -1 if tracing is disabled/full
   */
  public static int beginSpan(String name, String category) {
    if (!enabled) return -1;
    if (currentLoop.spanCount >= TraceLoop.MAX_SPANS) return -1;
    if (currentDepth >= MAX_DEPTH) return -1;

    int idx = currentLoop.spanCount++;
    TraceSpan span = currentLoop.spans[idx];
    span.name = name;
    span.category = (category == null || category.isEmpty()) ? DEFAULT_CATEGORY : category;
    span.depth = currentDepth++;
    Thread currentThread = Thread.currentThread();
    span.threadId = currentThread.getId();
    span.threadName = currentThread.getName();
    span.startNanos = System.nanoTime();
    span.startFPGA = Timer.getFPGATimestamp();
    span.complete = false;

    return idx;
  }

  /**
   * End a trace span.
   *
   * @param spanIndex The index returned by beginSpan
   */
  public static void endSpan(int spanIndex) {
    if (!enabled || spanIndex < 0) return;

    TraceSpan span = currentLoop.spans[spanIndex];
    span.endNanos = System.nanoTime();
    span.complete = true;
    if (currentDepth > 0) {
      currentDepth--;
    }
  }

  /** Check if tracing is enabled */
  public static boolean isEnabled() {
    return enabled;
  }

  /**
   * Enable or disable tracing. Disable for competition to eliminate all overhead.
   *
   * @param enable Whether to enable tracing
   */
  public static void setEnabled(boolean enable) {
    enabled = enable;
  }

  /** Get the current loop being written to */
  public static TraceLoop getCurrentLoop() {
    return currentLoop;
  }

  /** Get all loops in the buffer */
  public static TraceLoop[] getLoops() {
    return loops;
  }

  /** Get the buffer size */
  public static int getBufferSize() {
    return BUFFER_SIZE;
  }

  /** Get the current loop index in the circular buffer */
  public static int getLoopIndex() {
    return loopIndex;
  }

  /** Get total number of loops recorded */
  public static int getTotalLoopCount() {
    return totalLoopCount;
  }

  /**
   * Export traces to Chrome Tracing JSON format.
   *
   * @param path The file path to write to
   */
  public static void exportToJson(String path) {
    TraceExporter.exportToJson(loops, loopIndex, totalLoopCount, path);
  }

  /**
   * Export the most recent N loops to Chrome Tracing JSON format.
   *
   * @param path The file path to write to
   * @param numLoops Number of recent loops to export
   */
  public static void exportRecentToJson(String path, int numLoops) {
    TraceExporter.exportRecentToJson(loops, loopIndex, numLoops, path);
  }
}
