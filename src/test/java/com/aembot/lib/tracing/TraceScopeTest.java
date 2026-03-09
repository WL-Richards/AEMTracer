package com.aembot.lib.tracing;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for TraceScope that don't require WPILib runtime.
 */
class TraceScopeTest {

  @BeforeEach
  void setUp() {
    Tracer.setEnabled(true);
  }

  @AfterEach
  void tearDown() {
    Tracer.setEnabled(true);
  }

  @Test
  void implementsAutoCloseable() {
    assertTrue(AutoCloseable.class.isAssignableFrom(TraceScope.class));
  }

  @Test
  void close_withNegativeSpanIndex_doesNotThrow() {
    // When tracing is disabled, beginSpan returns -1
    TraceScope scope = new TraceScope(-1);

    assertDoesNotThrow(scope::close);
  }

  @Test
  void close_canBeCalledMultipleTimes() {
    TraceScope scope = new TraceScope(-1);

    scope.close();

    // Second close should not throw
    assertDoesNotThrow(scope::close);
  }

  @Test
  void scopeFromDisabledTracer_closesWithoutError() {
    Tracer.setEnabled(false);
    TraceScope scope = Tracer.trace("DisabledTest");

    assertDoesNotThrow(scope::close);
  }

  @Test
  void tryWithResources_withDisabledTracer_works() {
    Tracer.setEnabled(false);

    assertDoesNotThrow(() -> {
      try (TraceScope scope = Tracer.trace("DisabledTest")) {
        // Do nothing
      }
    });
  }

  @Test
  void tryWithResources_withDisabledTracer_handlesException() {
    Tracer.setEnabled(false);

    assertThrows(RuntimeException.class, () -> {
      try (TraceScope scope = Tracer.trace("ExceptionTest")) {
        throw new RuntimeException("Test exception");
      }
    });
    // If we get here, the scope closed properly despite the exception
  }

  @Test
  void nestedScopes_withDisabledTracer_work() {
    Tracer.setEnabled(false);

    assertDoesNotThrow(() -> {
      try (TraceScope outer = Tracer.trace("Outer")) {
        try (TraceScope inner = Tracer.trace("Inner")) {
          // Both scopes work when disabled
        }
      }
    });
  }

  @Test
  void constructor_acceptsAnySpanIndex() {
    // Should not throw for any index value
    assertDoesNotThrow(() -> new TraceScope(0));
    assertDoesNotThrow(() -> new TraceScope(-1));
    assertDoesNotThrow(() -> new TraceScope(100));
    assertDoesNotThrow(() -> new TraceScope(Integer.MAX_VALUE));
    assertDoesNotThrow(() -> new TraceScope(Integer.MIN_VALUE));
  }
}
