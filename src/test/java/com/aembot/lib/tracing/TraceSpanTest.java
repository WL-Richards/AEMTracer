package com.aembot.lib.tracing;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TraceSpanTest {

  private TraceSpan span;

  @BeforeEach
  void setUp() {
    span = new TraceSpan();
  }

  @Test
  void constructor_initializesFieldsToDefaults() {
    assertEquals(0, span.startNanos);
    assertEquals(0, span.endNanos);
    assertEquals(0, span.startFPGA);
    assertNull(span.name);
    assertEquals(0, span.depth);
    assertEquals(0, span.threadId);
    assertNull(span.threadName);
    assertNull(span.category);
    assertFalse(span.complete);
  }

  @Test
  void reset_clearsAllFields() {
    // Set all fields to non-default values
    span.startNanos = 1000000;
    span.endNanos = 2000000;
    span.startFPGA = 1.5;
    span.name = "TestSpan";
    span.depth = 3;
    span.threadId = 42;
    span.threadName = "TestThread";
    span.category = "Drivetrain";
    span.complete = true;

    span.reset();

    assertEquals(0, span.startNanos);
    assertEquals(0, span.endNanos);
    assertEquals(0, span.startFPGA);
    assertNull(span.name);
    assertEquals(0, span.depth);
    assertEquals(0, span.threadId);
    assertNull(span.threadName);
    assertNull(span.category);
    assertFalse(span.complete);
  }

  @Test
  void getDurationMicros_calculatesCorrectly() {
    span.startNanos = 1_000_000; // 1ms in nanos
    span.endNanos = 2_500_000; // 2.5ms in nanos

    double durationMicros = span.getDurationMicros();

    assertEquals(1500.0, durationMicros, 0.001);
  }

  @Test
  void getDurationMicros_returnsZeroForSameStartEnd() {
    span.startNanos = 1_000_000;
    span.endNanos = 1_000_000;

    assertEquals(0.0, span.getDurationMicros(), 0.001);
  }

  @Test
  void getDurationMillis_calculatesCorrectly() {
    span.startNanos = 1_000_000; // 1ms in nanos
    span.endNanos = 3_500_000; // 3.5ms in nanos

    double durationMillis = span.getDurationMillis();

    assertEquals(2.5, durationMillis, 0.0001);
  }

  @Test
  void getDurationMillis_handlesLargeDurations() {
    span.startNanos = 0;
    span.endNanos = 1_000_000_000L; // 1 second in nanos

    double durationMillis = span.getDurationMillis();

    assertEquals(1000.0, durationMillis, 0.0001);
  }

  @Test
  void getDurationMicros_handlesNegativeDuration() {
    // This can happen if endNanos is less than startNanos (shouldn't occur in practice)
    span.startNanos = 2_000_000;
    span.endNanos = 1_000_000;

    double durationMicros = span.getDurationMicros();

    assertEquals(-1000.0, durationMicros, 0.001);
  }

  @Test
  void fieldsArePubliclyAccessible() {
    // Verify all fields can be directly set and read
    span.startNanos = 100;
    span.endNanos = 200;
    span.startFPGA = 0.5;
    span.name = "Test";
    span.depth = 5;
    span.threadId = 123;
    span.threadName = "Worker";
    span.category = "Vision";
    span.complete = true;

    assertEquals(100, span.startNanos);
    assertEquals(200, span.endNanos);
    assertEquals(0.5, span.startFPGA);
    assertEquals("Test", span.name);
    assertEquals(5, span.depth);
    assertEquals(123, span.threadId);
    assertEquals("Worker", span.threadName);
    assertEquals("Vision", span.category);
    assertTrue(span.complete);
  }
}
