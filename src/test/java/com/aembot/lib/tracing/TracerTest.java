package com.aembot.lib.tracing;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for Tracer that don't require WPILib runtime.
 * Note: Methods that call Timer.getFPGATimestamp() cannot be fully tested
 * without WPILib native libraries. These tests focus on enable/disable behavior
 * and static configuration.
 */
class TracerTest {

  @BeforeEach
  void setUp() {
    Tracer.setEnabled(true);
  }

  @AfterEach
  void tearDown() {
    Tracer.setEnabled(true);
  }

  @Test
  void bufferSize_is500() {
    assertEquals(500, Tracer.getBufferSize());
  }

  @Test
  void getFrames_returnsNonNullArray() {
    TraceFrame[] frames = Tracer.getFrames();
    assertNotNull(frames);
    assertEquals(Tracer.getBufferSize(), frames.length);
  }

  @Test
  void getFrames_containsPreAllocatedFrames() {
    TraceFrame[] frames = Tracer.getFrames();
    for (int i = 0; i < frames.length; i++) {
      assertNotNull(frames[i], "Frame at index " + i + " should not be null");
    }
  }

  @Test
  void isEnabled_returnsTrueByDefault() {
    assertTrue(Tracer.isEnabled());
  }

  @Test
  void setEnabled_changesEnabledState() {
    Tracer.setEnabled(false);
    assertFalse(Tracer.isEnabled());

    Tracer.setEnabled(true);
    assertTrue(Tracer.isEnabled());
  }

  @Test
  void getCurrentFrame_returnsNonNull() {
    assertNotNull(Tracer.getCurrentFrame());
  }

  @Test
  void getFrameIndex_returnsValidIndex() {
    int index = Tracer.getFrameIndex();
    assertTrue(index >= 0);
    assertTrue(index < Tracer.getBufferSize());
  }

  @Test
  void getTotalFrameCount_returnsNonNegative() {
    assertTrue(Tracer.getTotalFrameCount() >= 0);
  }

  @Test
  void beginSpan_whenDisabled_returnsNegativeOne() {
    Tracer.setEnabled(false);

    int spanIndex = Tracer.beginSpan("TestSpan");

    assertEquals(-1, spanIndex);
  }

  @Test
  void endSpan_withNegativeIndex_doesNotThrow() {
    assertDoesNotThrow(() -> Tracer.endSpan(-1));
  }

  @Test
  void endSpan_whenDisabled_doesNotThrow() {
    Tracer.setEnabled(false);
    assertDoesNotThrow(() -> Tracer.endSpan(0));
  }

  @Test
  void trace_whenDisabled_returnsNonNullScope() {
    Tracer.setEnabled(false);

    TraceScope scope = Tracer.trace("DisabledTest");

    assertNotNull(scope);
  }

  @Test
  void trace_whenDisabled_scopeCloseDoesNotThrow() {
    Tracer.setEnabled(false);

    try (TraceScope scope = Tracer.trace("DisabledTest")) {
      // Should not throw
    }
  }

  @Test
  void frames_havePreAllocatedSpans() {
    TraceFrame[] frames = Tracer.getFrames();
    for (TraceFrame frame : frames) {
      assertNotNull(frame.spans);
      assertEquals(TraceFrame.MAX_SPANS, frame.spans.length);
      for (int i = 0; i < TraceFrame.MAX_SPANS; i++) {
        assertNotNull(frame.spans[i], "Span should be pre-allocated");
      }
    }
  }

  @Test
  void currentFrame_isInFramesArray() {
    TraceFrame current = Tracer.getCurrentFrame();
    TraceFrame[] frames = Tracer.getFrames();

    boolean found = false;
    for (TraceFrame frame : frames) {
      if (frame == current) {
        found = true;
        break;
      }
    }
    assertTrue(found, "Current frame should be in frames array");
  }
}
