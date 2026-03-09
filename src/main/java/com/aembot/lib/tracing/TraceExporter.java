package com.aembot.lib.tracing;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
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
   * Export all frames in the buffer to Chrome Tracing JSON format.
   *
   * @param frames The frame buffer
   * @param currentIndex Current write index in circular buffer
   * @param totalFrameCount Total frames recorded
   * @param path Output file path
   */
  public static void exportToJson(
      TraceFrame[] frames, int currentIndex, int totalFrameCount, String path) {
    int numFrames = Math.min(totalFrameCount, frames.length);
    exportRecentToJson(frames, currentIndex, numFrames, path);
  }

  /**
   * Export the most recent N frames to Chrome Tracing JSON format.
   *
   * @param frames The frame buffer
   * @param currentIndex Current write index in circular buffer
   * @param numFrames Number of frames to export
   * @param path Output file path
   */
  public static void exportRecentToJson(
      TraceFrame[] frames, int currentIndex, int numFrames, String path) {
    StringBuilder json = new StringBuilder(1024 * 1024); // 1MB initial capacity
    json.append("{\"traceEvents\":[\n");

    int bufferSize = frames.length;
    int startIndex = (currentIndex - numFrames + 1 + bufferSize) % bufferSize;

    // First pass: discover all unique thread IDs and their names
    Map<Long, Integer> threadIdToTid = new HashMap<>();
    Map<Long, String> threadIdToName = new HashMap<>();
    int nextTid = 10; // Start robot threads at tid=10

    for (int i = 0; i < numFrames; i++) {
      int frameIdx = (startIndex + i) % bufferSize;
      TraceFrame frame = frames[frameIdx];
      if (frame.spanCount == 0) continue;

      for (int s = 0; s < frame.spanCount; s++) {
        TraceSpan span = frame.spans[s];
        if (!span.complete) continue;

        long tid = span.threadId;
        if (!threadIdToTid.containsKey(tid)) {
          threadIdToTid.put(tid, nextTid++);
          // Use actual thread name, or fall back to Thread-{id}
          String name = span.threadName != null ? span.threadName : "Thread-" + tid;
          threadIdToName.put(tid, name);
        }
      }
    }

    // Add thread name metadata events for special tracks
    json.append(
        "{\"name\":\"thread_name\",\"ph\":\"M\",\"pid\":1,\"tid\":2,\"args\":{\"name\":\"LoopOverruns\"}},\n");
    json.append(
        "{\"name\":\"thread_name\",\"ph\":\"M\",\"pid\":1,\"tid\":3,\"args\":{\"name\":\"FrameMarkers\"}},\n");

    // Add thread name metadata for discovered threads
    for (Map.Entry<Long, Integer> entry : threadIdToTid.entrySet()) {
      long threadId = entry.getKey();
      int tid = entry.getValue();
      String threadName = threadIdToName.getOrDefault(threadId, "Thread-" + threadId);
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

    for (int i = 0; i < numFrames; i++) {
      int frameIdx = (startIndex + i) % bufferSize;
      TraceFrame frame = frames[frameIdx];

      // Skip empty frames
      if (frame.spanCount == 0) continue;

      double frameDurationMs = frame.getDurationMillis();
      double frameStartUs = frame.startTime * 1_000_000;
      double frameEndUs = frame.endTime * 1_000_000;
      boolean isOverrun = frameDurationMs > LOOP_PERIOD_MS;

      // Add frame duration counter (shows as a graph in Perfetto)
      if (!firstEvent) {
        json.append(",\n");
      }
      firstEvent = false;
      json.append("{\"name\":\"Frame Duration (ms)\",\"cat\":\"timing\",\"ph\":\"C\",\"ts\":");
      json.append(String.format("%.3f", frameStartUs));
      json.append(",\"pid\":1,\"tid\":0,\"args\":{\"duration\":");
      json.append(String.format("%.3f", frameDurationMs));
      json.append("}}");

      // Add 20ms budget line marker at frame start
      json.append(",\n{\"name\":\"20ms Budget\",\"cat\":\"timing\",\"ph\":\"C\",\"ts\":");
      json.append(String.format("%.3f", frameStartUs));
      json.append(",\"pid\":1,\"tid\":0,\"args\":{\"budget\":20.0}}");

      // Add LOOP OVERRUN marker if frame exceeded 20ms
      if (isOverrun) {
        json.append(",\n{\"name\":\"LOOP OVERRUN\",\"cat\":\"error\",\"ph\":\"X\",\"ts\":");
        json.append(String.format("%.3f", frameStartUs));
        json.append(",\"dur\":");
        json.append(String.format("%.3f", frameEndUs - frameStartUs));
        json.append(",\"pid\":1,\"tid\":2,\"args\":{\"frame\":");
        json.append(frame.frameNumber);
        json.append(",\"duration_ms\":");
        json.append(String.format("%.3f", frameDurationMs));
        json.append(",\"overrun_ms\":");
        json.append(String.format("%.3f", frameDurationMs - LOOP_PERIOD_MS));
        json.append("}}");
      }

      // Add frame boundary instant event on FrameMarkers track
      json.append(",\n{\"name\":\"Frame ");
      json.append(frame.frameNumber);
      if (isOverrun) {
        json.append(" [OVERRUN]");
      }
      json.append("\",\"cat\":\"frame\",\"ph\":\"i\",\"ts\":");
      json.append(String.format("%.3f", frameStartUs));
      json.append(",\"pid\":1,\"tid\":3,\"s\":\"t\",\"args\":{\"duration_ms\":");
      json.append(String.format("%.3f", frameDurationMs));
      json.append("}}");

      // Output spans as Complete Duration events ("X")
      for (int s = 0; s < frame.spanCount; s++) {
        TraceSpan span = frame.spans[s];
        if (!span.complete) continue;

        double startUs = span.startFPGA * 1_000_000;
        double durUs = span.getDurationMicros();
        int tid = threadIdToTid.getOrDefault(span.threadId, 10);

        String cat = (span.category != null && !span.category.isEmpty())
            ? span.category : "robot";

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
        json.append(",\"args\":{\"frame\":");
        json.append(frame.frameNumber);
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
      System.out.println("[Tracer] Exported " + numFrames + " frames to " + path);
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
