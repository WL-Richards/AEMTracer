package com.aembot.lib.tracing;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark methods for automatic tracing. Methods annotated with @Traced will have their
 * execution time automatically measured and recorded for timeline visualization.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * @Traced
 * public void periodic() {
 *     // Method body - timing is automatic
 * }
 *
 * @Traced("CustomName")
 * private void updateState() {
 *     // Custom name in trace output
 * }
 *
 * @Traced(category = "Drivetrain")
 * public void drivetrainPeriodic() {
 *     // Categorized for better organization in Perfetto
 * }
 *
 * @Traced(value = "CustomName", category = "Vision")
 * private void processTargets() {
 *     // Both custom name and category
 * }
 * }</pre>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Traced {
  /** Optional custom name for the trace. Defaults to "ClassName.methodName" if empty. */
  String value() default "";

  /**
   * Optional category/subsystem for grouping traces. Examples: "Drivetrain", "Vision", "Intake".
   * Defaults to "robot" if empty. Categories appear in Perfetto's category filter.
   */
  String category() default "";
}
