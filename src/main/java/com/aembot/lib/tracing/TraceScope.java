package com.aembot.lib.tracing;

/**
 * AutoCloseable wrapper for trace spans, enabling try-with-resources usage.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * try (var t = Tracer.trace("MyClass.myMethod")) {
 *     // Method body - automatically timed
 * }
 * }</pre>
 */
public final class TraceScope implements AutoCloseable {
  private final int spanIndex;

  TraceScope(int spanIndex) {
    this.spanIndex = spanIndex;
  }

  @Override
  public void close() {
    Tracer.endSpan(spanIndex);
  }
}
