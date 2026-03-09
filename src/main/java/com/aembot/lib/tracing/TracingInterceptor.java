package com.aembot.lib.tracing;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import net.bytebuddy.implementation.bind.annotation.*;

/**
 * ByteBuddy interceptor that wraps @Traced methods with timing code. This interceptor is bound to
 * methods at class load time and automatically records span timing.
 */
public class TracingInterceptor {

  /** Cached method metadata to avoid repeated reflection and string operations */
  private static final ConcurrentHashMap<Method, MethodTraceInfo> methodCache =
      new ConcurrentHashMap<>();

  /** Pre-computed trace info for a method */
  private static final class MethodTraceInfo {
    final String name;
    final byte categoryIndex;

    MethodTraceInfo(String name, byte categoryIndex) {
      this.name = name;
      this.categoryIndex = categoryIndex;
    }
  }

  /**
   * Get or compute the trace info for a method.
   *
   * @param method The method to get trace info for
   * @return The cached or newly computed trace info
   */
  private static MethodTraceInfo getTraceInfo(Method method) {
    return methodCache.computeIfAbsent(method, m -> {
      Traced annotation = m.getAnnotation(Traced.class);
      String name;
      String category;
      if (annotation != null) {
        name = annotation.value().isEmpty()
            ? m.getDeclaringClass().getSimpleName() + "." + m.getName()
            : annotation.value();
        category = annotation.category();
      } else {
        name = m.getDeclaringClass().getSimpleName() + "." + m.getName();
        category = Tracer.DEFAULT_CATEGORY;
      }
      return new MethodTraceInfo(name, Tracer.getCategoryIndex(category));
    });
  }

  /**
   * Intercepts a @Traced method call, wrapping it with tracing.
   *
   * @param method The method being called (injected by ByteBuddy)
   * @param callable Callable that invokes the original method (injected by ByteBuddy)
   * @return The result of the original method
   * @throws Exception If the original method throws
   */
  @RuntimeType
  public static Object intercept(
      @Origin Method method, @SuperCall Callable<?> callable, @This(optional = true) Object self)
      throws Exception {

    // Skip tracing if disabled
    if (!Tracer.isEnabled()) {
      return callable.call();
    }

    // Get cached trace info (computed once per method)
    MethodTraceInfo info = getTraceInfo(method);

    // Begin span, call method, end span
    int spanIndex = Tracer.beginSpan(info.name, info.categoryIndex);
    try {
      return callable.call();
    } finally {
      Tracer.endSpan(spanIndex);
    }
  }
}
