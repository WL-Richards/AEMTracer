package com.aembot.lib.tracing;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import net.bytebuddy.implementation.bind.annotation.*;

/**
 * ByteBuddy interceptor that wraps @Traced methods with timing code. This interceptor is bound to
 * methods at class load time and automatically records span timing.
 */
public class TracingInterceptor {

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

    // Get the trace name from annotation or generate from method signature
    Traced annotation = method.getAnnotation(Traced.class);
    String name;
    if (annotation != null && !annotation.value().isEmpty()) {
      name = annotation.value();
    } else {
      name = method.getDeclaringClass().getSimpleName() + "." + method.getName();
    }

    // Begin span, call method, end span
    int spanIndex = Tracer.beginSpan(name);
    try {
      return callable.call();
    } finally {
      Tracer.endSpan(spanIndex);
    }
  }
}
