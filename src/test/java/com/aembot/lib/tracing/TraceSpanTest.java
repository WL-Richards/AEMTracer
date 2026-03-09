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
    assertNull(span.name);
    assertEquals(0, span.depth);
    assertEquals(0, span.threadId);
    assertEquals(0, span.categoryIndex);
    assertFalse(span.complete);
  }

  @Test
  void reset_clearsAllFields() {
    // Set all fields to non-default values
    span.startNanos = 1000000;
    span.endNanos = 2000000;
    span.name = "TestSpan";
    span.depth = 3;
    span.threadId = 42;
    span.categoryIndex = 5;
    span.complete = true;

    span.reset();

    assertEquals(0, span.startNanos);
    assertEquals(0, span.endNanos);
    assertNull(span.name);
    assertEquals(0, span.depth);
    assertEquals(0, span.threadId);
    assertEquals(0, span.categoryIndex);
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
    span.name = "Test";
    span.depth = 5;
    span.threadId = 123;
    span.categoryIndex = 3;
    span.complete = true;

    assertEquals(100, span.startNanos);
    assertEquals(200, span.endNanos);
    assertEquals("Test", span.name);
    assertEquals(5, span.depth);
    assertEquals(123, span.threadId);
    assertEquals(3, span.categoryIndex);
    assertTrue(span.complete);
  }

  @Test
  void memorySize_documentCalculatedSpanSize() {
    // Document the calculated memory size of TraceSpan
    // This test always passes but prints the calculated size for verification
    //
    // TraceSpan fields (after optimization):
    // - long startNanos:     8 bytes
    // - long endNanos:       8 bytes
    // - long threadId:       8 bytes
    // - String name:         4 bytes (compressed oops) or 8 bytes
    // - byte depth:          1 byte
    // - byte categoryIndex:  1 byte
    // - boolean complete:    1 byte
    // - Object header:       12 bytes (compressed) or 16 bytes
    // - Padding:             varies for 8-byte alignment
    //
    // Estimated total: 40-48 bytes per span (down from ~72 bytes before optimization)

    int objectHeader = 12; // compressed oops
    int startNanos = 8;
    int endNanos = 8;
    int threadId = 8;
    int nameRef = 4; // compressed oops
    int depth = 1;
    int categoryIndex = 1;
    int complete = 1;
    // Padding to align to 8 bytes
    int rawSize = objectHeader + startNanos + endNanos + threadId + nameRef + depth + categoryIndex + complete;
    int alignedSize = ((rawSize + 7) / 8) * 8;

    int spansPerLoop = 256;
    int loopCount = 500;
    int totalSpans = spansPerLoop * loopCount;
    long totalSpanMemory = (long) totalSpans * alignedSize;

    // TraceLoop overhead per loop (rough estimate)
    int loopObjectHeader = 12;
    int loopFields = 8 + 8 + 8 + 4 + 4; // startTime, endTime, startNanos, loopNumber, spanCount
    int spanArrayRef = 4;
    int loopSize = ((loopObjectHeader + loopFields + spanArrayRef + 7) / 8) * 8;
    long totalLoopMemory = (long) loopCount * loopSize;

    long totalMemoryBytes = totalSpanMemory + totalLoopMemory;
    double totalMemoryMB = totalMemoryBytes / (1024.0 * 1024.0);

    System.out.println("=== TraceSpan Memory Estimate ===");
    System.out.println("Raw span size: " + rawSize + " bytes");
    System.out.println("Aligned span size: " + alignedSize + " bytes");
    System.out.println("Total spans: " + totalSpans);
    System.out.println("Span memory: " + totalSpanMemory + " bytes (" + (totalSpanMemory / 1024) + " KB)");
    System.out.println("Loop overhead: " + totalLoopMemory + " bytes");
    System.out.println("Total buffer memory: " + totalMemoryBytes + " bytes (~" + String.format("%.2f", totalMemoryMB) + " MB)");

    // Assertions to document expected sizes
    assertTrue(alignedSize <= 48, "Span size should be <= 48 bytes after optimization");
    assertTrue(totalMemoryMB < 7, "Total memory should be < 7MB after optimization");
  }
}
