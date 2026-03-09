package com.aembot.lib.tracing;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Exports trace data to Chrome Tracing JSON format. This format is compatible with:
 *
 * <ul>
 *   <li>Chrome's built-in tracing viewer (chrome://tracing)
 *   <li>Perfetto UI (https://ui.perfetto.dev)
 *   <li>Custom trace viewers
 * </ul>
 */
public final class TraceExporter {

  private TraceExporter() {} // Static only

  /**
   * Export all loops in the buffer to Chrome Tracing JSON format.
   *
   * @param loops The loop buffer
   * @param currentIndex Current write index in circular buffer
   * @param totalLoopCount Total loops recorded
   * @param categories Category registry for index lookup
   * @param threadNames Thread name cache for ID lookup
   * @param path Output file path
   */
  public static void exportToJson(
      TraceLoop[] loops,
      int currentIndex,
      int totalLoopCount,
      List<String> categories,
      Map<Long, String> threadNames,
      String path) {
    int numLoops = Math.min(totalLoopCount, loops.length);
    exportRecentToJson(loops, currentIndex, numLoops, categories, threadNames, path);
  }

  /**
   * Export the most recent N loops to Chrome Tracing JSON format.
   *
   * @param loops The loop buffer
   * @param currentIndex Current write index in circular buffer
   * @param numLoops Number of loops to export
   * @param categories Category registry for index lookup
   * @param cachedThreadNames Thread name cache for ID lookup
   * @param path Output file path
   */
  public static void exportRecentToJson(
      TraceLoop[] loops,
      int currentIndex,
      int numLoops,
      List<String> categories,
      Map<Long, String> cachedThreadNames,
      String path) {
    StringBuilder json = new StringBuilder(1024 * 1024); // 1MB initial capacity
    json.append("{\"traceEvents\":[\n");

    int bufferSize = loops.length;
    int startIndex = (currentIndex - numLoops + 1 + bufferSize) % bufferSize;

    // First pass: discover all unique thread IDs and assign export tids
    Map<Long, Integer> threadIdToTid = new HashMap<>();
    int nextTid = 10; // Start robot threads at tid=10

    for (int i = 0; i < numLoops; i++) {
      int loopIdx = (startIndex + i) % bufferSize;
      TraceLoop loop = loops[loopIdx];
      if (loop.spanCount == 0) continue;

      for (int s = 0; s < loop.spanCount; s++) {
        TraceSpan span = loop.spans[s];
        if (!span.complete) continue;

        long tid = span.threadId;
        if (!threadIdToTid.containsKey(tid)) {
          threadIdToTid.put(tid, nextTid++);
        }
      }
    }

    // Add thread name metadata events for special tracks
    json.append(
        "{\"name\":\"thread_name\",\"ph\":\"M\",\"pid\":1,\"tid\":2,\"args\":{\"name\":\"LoopOverruns\"}},\n");
    json.append(
        "{\"name\":\"thread_name\",\"ph\":\"M\",\"pid\":1,\"tid\":3,\"args\":{\"name\":\"LoopMarkers\"}},\n");

    // Add thread name metadata for discovered threads
    for (Map.Entry<Long, Integer> entry : threadIdToTid.entrySet()) {
      long threadId = entry.getKey();
      int tid = entry.getValue();
      String threadName = cachedThreadNames.getOrDefault(threadId, "Thread-" + threadId);
      json.append("{\"name\":\"thread_name\",\"ph\":\"M\",\"pid\":1,\"tid\":");
      json.append(tid);
      json.append(",\"args\":{\"name\":\"");
      escapeJsonString(json, threadName);
      json.append("\"}},\n");
    }

    json.append(
        "{\"name\":\"process_name\",\"ph\":\"M\",\"pid\":1,\"args\":{\"name\":\"FRC Robot\"}}");

    boolean firstEvent = false; // We already have events above

    // Loop timing constant
    final double LOOP_PERIOD_MS = 20.0;

    for (int i = 0; i < numLoops; i++) {
      int loopIdx = (startIndex + i) % bufferSize;
      TraceLoop loop = loops[loopIdx];

      // Skip empty loops
      if (loop.spanCount == 0) continue;

      double loopDurationMs = loop.getDurationMillis();
      double loopStartUs = loop.startTime * 1_000_000;
      double loopEndUs = loop.endTime * 1_000_000;
      boolean isOverrun = loopDurationMs > LOOP_PERIOD_MS;

      // Add loop duration counter (shows as a graph in Perfetto)
      if (!firstEvent) {
        json.append(",\n");
      }
      firstEvent = false;
      json.append("{\"name\":\"Loop Duration (ms)\",\"cat\":\"timing\",\"ph\":\"C\",\"ts\":");
      json.append(String.format("%.3f", loopStartUs));
      json.append(",\"pid\":1,\"tid\":0,\"args\":{\"duration\":");
      json.append(String.format("%.3f", loopDurationMs));
      json.append("}}");

      // Add 20ms budget line marker at loop start
      json.append(",\n{\"name\":\"20ms Budget\",\"cat\":\"timing\",\"ph\":\"C\",\"ts\":");
      json.append(String.format("%.3f", loopStartUs));
      json.append(",\"pid\":1,\"tid\":0,\"args\":{\"budget\":20.0}}");

      // Add LOOP OVERRUN marker if loop exceeded 20ms
      if (isOverrun) {
        json.append(",\n{\"name\":\"LOOP OVERRUN\",\"cat\":\"error\",\"ph\":\"X\",\"ts\":");
        json.append(String.format("%.3f", loopStartUs));
        json.append(",\"dur\":");
        json.append(String.format("%.3f", loopEndUs - loopStartUs));
        json.append(",\"pid\":1,\"tid\":2,\"args\":{\"loop\":");
        json.append(loop.loopNumber);
        json.append(",\"duration_ms\":");
        json.append(String.format("%.3f", loopDurationMs));
        json.append(",\"overrun_ms\":");
        json.append(String.format("%.3f", loopDurationMs - LOOP_PERIOD_MS));
        json.append("}}");
      }

      // Add loop boundary instant event on LoopMarkers track
      json.append(",\n{\"name\":\"Loop ");
      json.append(loop.loopNumber);
      if (isOverrun) {
        json.append(" [OVERRUN]");
      }
      json.append("\",\"cat\":\"loop\",\"ph\":\"i\",\"ts\":");
      json.append(String.format("%.3f", loopStartUs));
      json.append(",\"pid\":1,\"tid\":3,\"s\":\"t\",\"args\":{\"duration_ms\":");
      json.append(String.format("%.3f", loopDurationMs));
      json.append("}}");

      // Output spans as Complete Duration events ("X")
      for (int s = 0; s < loop.spanCount; s++) {
        TraceSpan span = loop.spans[s];
        if (!span.complete) continue;

        // Compute FPGA timestamp from nanos using loop's reference point
        double spanFpgaSeconds = loop.startTime + (span.startNanos - loop.startNanos) / 1_000_000_000.0;
        double startUs = spanFpgaSeconds * 1_000_000;
        double durUs = span.getDurationMicros();
        int tid = threadIdToTid.getOrDefault(span.threadId, 10);

        // Look up category from index
        int catIdx = span.categoryIndex & 0xFF; // unsigned byte
        String cat = (catIdx < categories.size()) ? categories.get(catIdx) : "robot";

        json.append(",\n{\"name\":\"");
        escapeJsonString(json, span.name);
        json.append("\",\"cat\":\"");
        escapeJsonString(json, cat);
        json.append("\",\"ph\":\"X\",\"ts\":");
        json.append(String.format("%.3f", startUs));
        json.append(",\"dur\":");
        json.append(String.format("%.3f", durUs));
        json.append(",\"pid\":1,\"tid\":");
        json.append(tid);
        json.append(",\"args\":{\"loop\":");
        json.append(loop.loopNumber);
        json.append(",\"thread\":");
        json.append(span.threadId);
        json.append(",\"category\":\"");
        escapeJsonString(json, cat);
        json.append("\"}}");
      }
    }

    json.append("\n]}");

    // Write to file
    try (PrintWriter writer = new PrintWriter(new FileWriter(path))) {
      writer.print(json.toString());
      System.out.println("[Tracer] Exported " + numLoops + " loops to " + path);
    } catch (IOException e) {
      System.err.println("[Tracer] Failed to export traces: " + e.getMessage());
    }
  }

  /** Escape special characters in JSON strings */
  private static void escapeJsonString(StringBuilder sb, String s) {
    if (s == null) {
      sb.append("null");
      return;
    }
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      switch (c) {
        case '"':
          sb.append("\\\"");
          break;
        case '\\':
          sb.append("\\\\");
          break;
        case '\n':
          sb.append("\\n");
          break;
        case '\r':
          sb.append("\\r");
          break;
        case '\t':
          sb.append("\\t");
          break;
        default:
          sb.append(c);
      }
    }
  }
}
