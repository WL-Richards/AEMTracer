package com.aembot.lib.tracing;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import net.bytebuddy.implementation.bind.annotation.*;

/**
 * ByteBuddy interceptor that wraps WPILib Command lifecycle methods with tracing.
 * Automatically traces initialize(), execute(), isFinished(), and end() methods.
 */
public class CommandTracingInterceptor {

  /** Category used for all Command traces */
  public static final String COMMAND_CATEGORY = "Command";

  /** Cached category index (resolved once on first use) */
  private static byte cachedCategoryIndex = -1;

  /** Cached getName method for performance */
  private static Method getNameMethod = null;
  private static boolean getNameMethodResolved = false;

  /**
   * Intercepts Command lifecycle method calls, wrapping them with tracing.
   *
   * @param method The method being called (injected by ByteBuddy)
   * @param callable Callable that invokes the original method (injected by ByteBuddy)
   * @param self The Command instance (injected by ByteBuddy)
   * @return The result of the original method
   * @throws Exception If the original method throws
   */
  @RuntimeType
  public static Object intercept(
      @Origin Method method,
      @SuperCall Callable<?> callable,
      @This Object self)
      throws Exception {

    // Skip tracing if disabled
    if (!Tracer.isEnabled()) {
      return callable.call();
    }

    // Build trace name: CommandName.methodName
    // Uses getName() which returns custom name if set via .withName(), else class name
    String commandName = getCommandName(self);
    String methodName = method.getName();
    String traceName = commandName + "." + methodName;

    // Cache category index on first use
    if (cachedCategoryIndex < 0) {
      cachedCategoryIndex = Tracer.getCategoryIndex(COMMAND_CATEGORY);
    }

    // Begin span, call method, end span
    int spanIndex = Tracer.beginSpan(traceName, cachedCategoryIndex);
    try {
      return callable.call();
    } finally {
      Tracer.endSpan(spanIndex);
    }
  }

  /**
   * Get the command name, using getName() if available for better visibility.
   * Falls back to class simple name if getName() fails.
   */
  private static String getCommandName(Object command) {
    // Try to resolve getName method once
    if (!getNameMethodResolved) {
      getNameMethodResolved = true;
      try {
        getNameMethod = command.getClass().getMethod("getName");
      } catch (NoSuchMethodException e) {
        // getName not available, will use class name
      }
    }

    // Try to call getName()
    if (getNameMethod != null) {
      try {
        Object result = getNameMethod.invoke(command);
        if (result instanceof String) {
          return (String) result;
        }
      } catch (Exception e) {
        // Fall through to class name
      }
    }

    return command.getClass().getSimpleName();
  }
}
