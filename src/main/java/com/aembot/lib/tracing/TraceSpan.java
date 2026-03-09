package com.aembot.lib.tracing;

/**
 * Represents a single traced span (function call) with timing information. Pre-allocated to avoid
 * GC during robot loop.
 *
 * <p>Memory-optimized: threadName and category are stored externally in lookup tables.
 * FPGA timestamps are computed at export time from nanos using TraceLoop's reference point.
 */
public final class TraceSpan {
  /** Start time in nanoseconds (System.nanoTime) for high precision duration */
  public long startNanos;

  /** End time in nanoseconds */
  public long endNanos;

  /** Name of the traced function (ClassName.methodName or custom) */
  public String name;

  /** Nesting depth (0 = top level, increases with nested calls) */
  public byte depth;

  /** Thread ID that this span ran on */
  public long threadId;

  /** Category index into Tracer.getCategories() */
  public byte categoryIndex;

  /** Whether this span has completed (endSpan was called) */
  public boolean complete;

  /** Reset this span for reuse */
  public void reset() {
    startNanos = 0;
    endNanos = 0;
    name = null;
    depth = 0;
    threadId = 0;
    categoryIndex = 0;
    complete = false;
  }

  /** Get duration in microseconds */
  public double getDurationMicros() {
    return (endNanos - startNanos) / 1000.0;
  }

  /** Get duration in milliseconds */
  public double getDurationMillis() {
    return (endNanos - startNanos) / 1_000_000.0;
  }
}
