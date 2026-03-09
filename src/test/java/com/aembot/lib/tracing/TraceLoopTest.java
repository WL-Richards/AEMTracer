package com.aembot.lib.tracing;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Tests for TraceLoop that don't require WPILib runtime.
 * Note: reset() method cannot be tested directly as it depends on Timer.getFPGATimestamp()
 * which requires native WPILib libraries. These tests focus on the data structure behavior.
 */
class TraceLoopTest {

  @Test
  void maxSpans_is256() {
    assertEquals(256, TraceLoop.MAX_SPANS);
  }

  @Test
  void getDurationMillis_calculatesCorrectly() {
    TraceLoop frame = createTestLoop();
    frame.startTime = 1.0; // 1 second
    frame.endTime = 1.025; // 1.025 seconds

    double durationMs = frame.getDurationMillis();

    assertEquals(25.0, durationMs, 0.0001);
  }

  @Test
  void getDurationMillis_returnsZeroForSameStartEnd() {
    TraceLoop frame = createTestLoop();
    frame.startTime = 1.0;
    frame.endTime = 1.0;

    assertEquals(0.0, frame.getDurationMillis(), 0.0001);
  }

  @Test
  void getDurationMillis_handlesLongDurations() {
    TraceLoop frame = createTestLoop();
    frame.startTime = 0.0;
    frame.endTime = 60.0; // 60 seconds

    double durationMs = frame.getDurationMillis();

    assertEquals(60000.0, durationMs, 0.0001);
  }

  @Test
  void getDurationMillis_handlesSubMillisecondDurations() {
    TraceLoop frame = createTestLoop();
    frame.startTime = 0.0;
    frame.endTime = 0.0001; // 0.1ms

    double durationMs = frame.getDurationMillis();

    assertEquals(0.1, durationMs, 0.0001);
  }

  @Test
  void loopNumber_canBeSetAndRead() {
    TraceLoop frame = createTestLoop();
    frame.loopNumber = 42;
    assertEquals(42, frame.loopNumber);

    frame.loopNumber = 999999;
    assertEquals(999999, frame.loopNumber);
  }

  @Test
  void spanCount_canBeSetAndRead() {
    TraceLoop frame = createTestLoop();
    assertEquals(0, frame.spanCount);

    frame.spanCount = 50;
    assertEquals(50, frame.spanCount);
  }

  @Test
  void spans_areAccessible() {
    TraceLoop frame = createTestLoop();
    assertNotNull(frame.spans);
    assertEquals(TraceLoop.MAX_SPANS, frame.spans.length);
  }

  @Test
  void spans_canBeModified() {
    TraceLoop frame = createTestLoop();
    frame.spans[0].name = "TestSpan";
    frame.spans[0].complete = true;

    assertEquals("TestSpan", frame.spans[0].name);
    assertTrue(frame.spans[0].complete);
  }

  @Test
  void startTime_canBeSetAndRead() {
    TraceLoop frame = createTestLoop();
    frame.startTime = 123.456;
    assertEquals(123.456, frame.startTime, 0.0001);
  }

  @Test
  void endTime_canBeSetAndRead() {
    TraceLoop frame = createTestLoop();
    frame.endTime = 789.012;
    assertEquals(789.012, frame.endTime, 0.0001);
  }

  /**
   * Creates a TraceLoop for testing. The constructor only allocates spans
   * and doesn't call Timer (Timer is only called in reset()).
   */
  private TraceLoop createTestLoop() {
    return new TraceLoop();
  }
}
