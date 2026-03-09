package com.aembot.lib.tracing;

import edu.wpi.first.wpilibj.Timer;

/**
 * Lightweight tracing system for measuring function execution times. Uses a circular buffer of
 * pre-allocated frames to avoid GC during robot loop.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * // In robotPeriodic():
 * Tracer.beginFrame();
 * // ... robot code (traced via @Traced annotations)
 * Tracer.endFrame();
 *
 * // Export when disabled:
 * Tracer.exportToJson("/U/logs/trace.json");
 * }</pre>
 */
public final class Tracer {
  /** Number of frames to keep in circular buffer (~10 seconds at 50Hz) */
  private static final int BUFFER_SIZE = 500;

  /** Maximum nesting depth for traced calls */
  private static final int MAX_DEPTH = 32;

  /** Circular buffer of trace frames */
  private static final TraceFrame[] frames = new TraceFrame[BUFFER_SIZE];

  /** Current frame being written to */
  private static TraceFrame currentFrame;

  /** Index of current frame in circular buffer */
  private static int frameIndex = 0;

  /** Total frame count (for frame numbering) */
  private static int totalFrameCount = 0;

  /** Current nesting depth */
  private static byte currentDepth = 0;

  /** Whether tracing is enabled */
  private static boolean enabled = true;

  /** Static initializer - pre-allocate all frames */
  static {
    for (int i = 0; i < BUFFER_SIZE; i++) {
      frames[i] = new TraceFrame();
    }
    currentFrame = frames[0];
  }

  private Tracer() {} // Static only

  /**
   * Begin a new trace frame. Call this at the start of robotPeriodic().
   *
   * <p>This advances the circular buffer and resets the current frame.
   */
  public static void beginFrame() {
    if (!enabled) return;

    frameIndex = (frameIndex + 1) % BUFFER_SIZE;
    currentFrame = frames[frameIndex];
    currentFrame.reset();
    currentFrame.frameNumber = totalFrameCount++;
    currentDepth = 0;

    // Debug: log every 250 frames (~5 seconds)
    if (totalFrameCount % 250 == 0) {
      System.out.println(
          "[Tracer] Frame "
              + totalFrameCount
              + ", spans in last frame: "
              + getLastFrameSpanCount());
    }
  }

  /** Get span count from previous frame for debugging */
  private static int getLastFrameSpanCount() {
    int prevIndex = (frameIndex - 1 + BUFFER_SIZE) % BUFFER_SIZE;
    return frames[prevIndex].spanCount;
  }

  /**
   * End the current trace frame. Call this at the end of robotPeriodic().
   *
   * <p>Records the frame end time.
   */
  public static void endFrame() {
    if (!enabled) return;
    currentFrame.endTime = Timer.getFPGATimestamp();
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
    if (currentFrame.spanCount >= TraceFrame.MAX_SPANS) return -1;
    if (currentDepth >= MAX_DEPTH) return -1;

    int idx = currentFrame.spanCount++;
    TraceSpan span = currentFrame.spans[idx];
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

    TraceSpan span = currentFrame.spans[spanIndex];
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

  /** Get the current frame being written to */
  public static TraceFrame getCurrentFrame() {
    return currentFrame;
  }

  /** Get all frames in the buffer */
  public static TraceFrame[] getFrames() {
    return frames;
  }

  /** Get the buffer size */
  public static int getBufferSize() {
    return BUFFER_SIZE;
  }

  /** Get the current frame index in the circular buffer */
  public static int getFrameIndex() {
    return frameIndex;
  }

  /** Get total number of frames recorded */
  public static int getTotalFrameCount() {
    return totalFrameCount;
  }

  /**
   * Export traces to Chrome Tracing JSON format.
   *
   * @param path The file path to write to
   */
  public static void exportToJson(String path) {
    TraceExporter.exportToJson(frames, frameIndex, totalFrameCount, path);
  }

  /**
   * Export the most recent N frames to Chrome Tracing JSON format.
   *
   * @param path The file path to write to
   * @param numFrames Number of recent frames to export
   */
  public static void exportRecentToJson(String path, int numFrames) {
    TraceExporter.exportRecentToJson(frames, frameIndex, numFrames, path);
  }
}
