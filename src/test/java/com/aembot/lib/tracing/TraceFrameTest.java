package com.aembot.lib.tracing;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Tests for TraceFrame that don't require WPILib runtime.
 * Note: reset() method cannot be tested directly as it depends on Timer.getFPGATimestamp()
 * which requires native WPILib libraries. These tests focus on the data structure behavior.
 */
class TraceFrameTest {

  @Test
  void maxSpans_is256() {
    assertEquals(256, TraceFrame.MAX_SPANS);
  }

  @Test
  void getDurationMillis_calculatesCorrectly() {
    TraceFrame frame = createTestFrame();
    frame.startTime = 1.0; // 1 second
    frame.endTime = 1.025; // 1.025 seconds

    double durationMs = frame.getDurationMillis();

    assertEquals(25.0, durationMs, 0.0001);
  }

  @Test
  void getDurationMillis_returnsZeroForSameStartEnd() {
    TraceFrame frame = createTestFrame();
    frame.startTime = 1.0;
    frame.endTime = 1.0;

    assertEquals(0.0, frame.getDurationMillis(), 0.0001);
  }

  @Test
  void getDurationMillis_handlesLongDurations() {
    TraceFrame frame = createTestFrame();
    frame.startTime = 0.0;
    frame.endTime = 60.0; // 60 seconds

    double durationMs = frame.getDurationMillis();

    assertEquals(60000.0, durationMs, 0.0001);
  }

  @Test
  void getDurationMillis_handlesSubMillisecondDurations() {
    TraceFrame frame = createTestFrame();
    frame.startTime = 0.0;
    frame.endTime = 0.0001; // 0.1ms

    double durationMs = frame.getDurationMillis();

    assertEquals(0.1, durationMs, 0.0001);
  }

  @Test
  void frameNumber_canBeSetAndRead() {
    TraceFrame frame = createTestFrame();
    frame.frameNumber = 42;
    assertEquals(42, frame.frameNumber);

    frame.frameNumber = 999999;
    assertEquals(999999, frame.frameNumber);
  }

  @Test
  void spanCount_canBeSetAndRead() {
    TraceFrame frame = createTestFrame();
    assertEquals(0, frame.spanCount);

    frame.spanCount = 50;
    assertEquals(50, frame.spanCount);
  }

  @Test
  void spans_areAccessible() {
    TraceFrame frame = createTestFrame();
    assertNotNull(frame.spans);
    assertEquals(TraceFrame.MAX_SPANS, frame.spans.length);
  }

  @Test
  void spans_canBeModified() {
    TraceFrame frame = createTestFrame();
    frame.spans[0].name = "TestSpan";
    frame.spans[0].complete = true;

    assertEquals("TestSpan", frame.spans[0].name);
    assertTrue(frame.spans[0].complete);
  }

  @Test
  void startTime_canBeSetAndRead() {
    TraceFrame frame = createTestFrame();
    frame.startTime = 123.456;
    assertEquals(123.456, frame.startTime, 0.0001);
  }

  @Test
  void endTime_canBeSetAndRead() {
    TraceFrame frame = createTestFrame();
    frame.endTime = 789.012;
    assertEquals(789.012, frame.endTime, 0.0001);
  }

  /**
   * Creates a TraceFrame for testing. The constructor only allocates spans
   * and doesn't call Timer (Timer is only called in reset()).
   */
  private TraceFrame createTestFrame() {
    return new TraceFrame();
  }
}
