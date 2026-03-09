package com.aembot.lib.tracing;

import edu.wpi.first.wpilibj.Timer;

/**
 * Container for all trace spans within a single robot loop iteration. Pre-allocated to avoid GC
 * during robot loop.
 */
public final class TraceLoop {
  /** Maximum number of spans per loop (256 to accommodate command tracing) */
  public static final int MAX_SPANS = 256;

  /** Pre-allocated array of spans */
  public final TraceSpan[] spans;

  /** Current number of active spans in this loop */
  public int spanCount;

  /** Loop number (incrementing counter) */
  public int loopNumber;

  /** FPGA timestamp when this loop started */
  public double startTime;

  /** FPGA timestamp when this loop ended */
  public double endTime;

  /** Create a new loop with pre-allocated spans */
  public TraceLoop() {
    spans = new TraceSpan[MAX_SPANS];
    for (int i = 0; i < MAX_SPANS; i++) {
      spans[i] = new TraceSpan();
    }
    spanCount = 0;
    loopNumber = 0;
    startTime = 0;
    endTime = 0;
  }

  /** Reset this loop for reuse */
  public void reset() {
    for (int i = 0; i < spanCount; i++) {
      spans[i].reset();
    }
    spanCount = 0;
    startTime = Timer.getFPGATimestamp();
    endTime = 0;
  }

  /** Get total loop duration in milliseconds */
  public double getDurationMillis() {
    return (endTime - startTime) * 1000.0;
  }
}
