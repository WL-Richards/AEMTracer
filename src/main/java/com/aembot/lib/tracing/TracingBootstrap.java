package com.aembot.lib.tracing;

import java.lang.instrument.Instrumentation;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatchers;

/**
 * Bootstraps the @Traced annotation system using ByteBuddy. Call {@link #install()} early in
 * Main.main() before any @Traced classes are loaded.
 *
 * <p>This uses ByteBuddy's self-attach capability to instrument classes at load time. If
 * self-attach fails (e.g., on restricted JVMs), tracing via annotations won't work but manual
 * Tracer.trace() calls will still function.
 *
 * <p>Usage in Main.java:
 *
 * <pre>{@code
 * public static void main(String... args) {
 *     TracingBootstrap.install();
 *     RobotBase.startRobot(Robot::new);
 * }
 * }</pre>
 */
public final class TracingBootstrap {

  private static boolean installed = false;
  private static boolean installFailed = false;
  private static boolean commandTracingInstalled = false;
  private static Instrumentation instrumentation = null;

  private TracingBootstrap() {} // Static only

  /**
   * Install the ByteBuddy agent and register the {@code @Traced} transformer. Must be called before
   * any {@code @Traced} classes are loaded (ideally first thing in main()).
   *
   * @return true if installation succeeded, false if it failed
   */
  public static boolean install() {
    if (installed) {
      return true;
    }
    if (installFailed) {
      return false;
    }

    try {
      System.out.println("[TracingBootstrap] Installing ByteBuddy agent...");

      // Self-attach to the running JVM to get an Instrumentation instance
      instrumentation = ByteBuddyAgent.install();

      // Create an agent that intercepts all @Traced methods
      new AgentBuilder.Default()
          // Ignore JDK and library classes for performance
          .ignore(ElementMatchers.nameStartsWith("java."))
          .ignore(ElementMatchers.nameStartsWith("jdk."))
          .ignore(ElementMatchers.nameStartsWith("sun."))
          .ignore(ElementMatchers.nameStartsWith("com.sun."))
          .ignore(ElementMatchers.nameStartsWith("net.bytebuddy."))
          // Only transform classes with @Traced methods
          .type(ElementMatchers.declaresMethod(ElementMatchers.isAnnotatedWith(Traced.class)))
          .transform(
              (builder, typeDescription, classLoader, module, protectionDomain) ->
                  builder
                      .method(ElementMatchers.isAnnotatedWith(Traced.class))
                      .intercept(MethodDelegation.to(TracingInterceptor.class)))
          // Use retransformation to handle already-loaded classes
          .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
          // Log transformations to stdout for debugging
          .with(AgentBuilder.Listener.StreamWriting.toSystemOut().withTransformationsOnly())
          .installOn(instrumentation);

      installed = true;
      System.out.println("[TracingBootstrap] Successfully installed @Traced instrumentation");
      return true;

    } catch (Exception e) {
      installFailed = true;
      System.err.println("[TracingBootstrap] Failed to install ByteBuddy agent: " + e.getMessage());
      System.err.println(
          "[TracingBootstrap] @Traced annotations will not work. Use Tracer.trace() manually.");
      e.printStackTrace();
      return false;
    }
  }

  /** Check if the tracing instrumentation was successfully installed */
  public static boolean isInstalled() {
    return installed;
  }

  /**
   * Install automatic tracing for WPILib Command lifecycle methods.
   * Traces initialize(), execute(), isFinished(), and end() on all Command subclasses.
   *
   * <p>Must be called after {@link #install()} and before Command classes are loaded.
   *
   * <p>Usage:
   * <pre>{@code
   * public static void main(String... args) {
   *     TracingBootstrap.install();
   *     TracingBootstrap.installCommandTracing();
   *     RobotBase.startRobot(Robot::new);
   * }
   * }</pre>
   *
   * @return true if installation succeeded, false if it failed
   */
  public static boolean installCommandTracing() {
    if (commandTracingInstalled) {
      return true;
    }
    if (!installed || instrumentation == null) {
      System.err.println("[TracingBootstrap] Must call install() before installCommandTracing()");
      return false;
    }

    try {
      System.out.println("[TracingBootstrap] Installing Command tracing...");

      // Intercept Command lifecycle methods
      new AgentBuilder.Default()
          // Ignore JDK and library classes
          .ignore(ElementMatchers.nameStartsWith("java."))
          .ignore(ElementMatchers.nameStartsWith("jdk."))
          .ignore(ElementMatchers.nameStartsWith("sun."))
          .ignore(ElementMatchers.nameStartsWith("com.sun."))
          .ignore(ElementMatchers.nameStartsWith("net.bytebuddy."))
          // Match classes that extend edu.wpi.first.wpilibj2.command.Command
          .type(ElementMatchers.hasSuperType(
              ElementMatchers.named("edu.wpi.first.wpilibj2.command.Command")))
          .transform(
              (builder, typeDescription, classLoader, module, protectionDomain) ->
                  builder
                      // Intercept initialize(), execute(), isFinished(), end()
                      .method(ElementMatchers.named("initialize")
                          .or(ElementMatchers.named("execute"))
                          .or(ElementMatchers.named("isFinished"))
                          .or(ElementMatchers.named("end")))
                      .intercept(MethodDelegation.to(CommandTracingInterceptor.class)))
          .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
          .with(AgentBuilder.Listener.StreamWriting.toSystemOut().withTransformationsOnly())
          .installOn(instrumentation);

      commandTracingInstalled = true;
      System.out.println("[TracingBootstrap] Successfully installed Command tracing");
      return true;

    } catch (Exception e) {
      System.err.println("[TracingBootstrap] Failed to install Command tracing: " + e.getMessage());
      e.printStackTrace();
      return false;
    }
  }

  /** Check if Command tracing was successfully installed */
  public static boolean isCommandTracingInstalled() {
    return commandTracingInstalled;
  }
}
