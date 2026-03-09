package com.aembot.lib.tracing;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tests for TraceExporter that don't require WPILib runtime.
 * Creates test frames by directly setting fields rather than calling methods
 * that depend on Timer.getFPGATimestamp().
 */
class TraceExporterTest {

  @TempDir Path tempDir;

  private TraceLoop[] frames;
  private List<String> categories;
  private Map<Long, String> threadNames;

  @BeforeEach
  void setUp() {
    // Create test frames by directly instantiating and setting fields
    frames = new TraceLoop[10];
    for (int i = 0; i < frames.length; i++) {
      frames[i] = new TraceLoop();
    }
    // Set up default category registry
    categories = new ArrayList<>();
    categories.add("robot"); // index 0
    // Set up thread name cache
    threadNames = new HashMap<>();
  }

  /**
   * Sets up a frame with test data.
   */
  private void setupFrame(TraceLoop frame, int loopNumber, double startTime, double endTime) {
    frame.loopNumber = loopNumber;
    frame.startTime = startTime;
    frame.endTime = endTime;
    frame.startNanos = (long) (startTime * 1_000_000_000); // Simulated nanos reference
    frame.spanCount = 0;
  }

  /**
   * Adds a completed span to a frame.
   */
  private void addSpan(TraceLoop frame, String name, long threadId, String threadNameStr) {
    int idx = frame.spanCount++;
    TraceSpan span = frame.spans[idx];
    span.name = name;
    span.startNanos = frame.startNanos + 1_000_000; // 1ms after frame start
    span.endNanos = frame.startNanos + 2_000_000;   // 2ms after frame start
    span.depth = 0;
    span.threadId = threadId;
    span.categoryIndex = 0; // default "robot" category
    span.complete = true;
    // Cache thread name
    threadNames.put(threadId, threadNameStr);
  }

  @Test
  void exportToJson_createsFile() throws IOException {
    Path outputPath = tempDir.resolve("trace.json");

    TraceExporter.exportToJson(frames, 0, 0, categories, threadNames, outputPath.toString());

    assertTrue(Files.exists(outputPath));
  }

  @Test
  void exportToJson_createsValidJson() throws IOException {
    Path outputPath = tempDir.resolve("trace.json");

    TraceExporter.exportToJson(frames, 0, 0, categories, threadNames, outputPath.toString());

    String content = Files.readString(outputPath);
    assertTrue(content.startsWith("{\"traceEvents\":["));
    assertTrue(content.endsWith("]}"));
  }

  @Test
  void exportToJson_includesProcessMetadata() throws IOException {
    Path outputPath = tempDir.resolve("trace.json");

    TraceExporter.exportToJson(frames, 0, 0, categories, threadNames, outputPath.toString());

    String content = Files.readString(outputPath);
    assertTrue(content.contains("\"name\":\"process_name\""));
    assertTrue(content.contains("\"name\":\"FRC Robot\""));
  }

  @Test
  void exportToJson_includesSpecialTracks() throws IOException {
    Path outputPath = tempDir.resolve("trace.json");

    TraceExporter.exportToJson(frames, 0, 0, categories, threadNames, outputPath.toString());

    String content = Files.readString(outputPath);
    assertTrue(content.contains("\"name\":\"LoopOverruns\""));
    assertTrue(content.contains("\"name\":\"LoopMarkers\""));
  }

  @Test
  void exportToJson_exportsSpanData() throws IOException {
    setupFrame(frames[0], 0, 1.0, 1.02);
    addSpan(frames[0], "TestMethod", 1, "main");

    Path outputPath = tempDir.resolve("trace.json");
    TraceExporter.exportToJson(frames, 0, 1, categories, threadNames, outputPath.toString());

    String content = Files.readString(outputPath);
    assertTrue(content.contains("\"name\":\"TestMethod\""));
    assertTrue(content.contains("\"cat\":\"robot\""));
    assertTrue(content.contains("\"ph\":\"X\"")); // Complete duration event
  }

  @Test
  void exportToJson_includesFrameDurationCounter() throws IOException {
    setupFrame(frames[0], 0, 1.0, 1.015);
    addSpan(frames[0], "Test", 1, "main");

    Path outputPath = tempDir.resolve("trace.json");
    TraceExporter.exportToJson(frames, 0, 1, categories, threadNames, outputPath.toString());

    String content = Files.readString(outputPath);
    assertTrue(content.contains("\"name\":\"Loop Duration (ms)\""));
    assertTrue(content.contains("\"ph\":\"C\"")); // Counter event
  }

  @Test
  void exportToJson_detectsLoopOverruns() throws IOException {
    setupFrame(frames[0], 0, 1.0, 1.025); // 25ms duration (>20ms threshold)
    addSpan(frames[0], "SlowMethod", 1, "main");

    Path outputPath = tempDir.resolve("trace.json");
    TraceExporter.exportToJson(frames, 0, 1, categories, threadNames, outputPath.toString());

    String content = Files.readString(outputPath);
    assertTrue(content.contains("\"name\":\"LOOP OVERRUN\""));
    assertTrue(content.contains("\"cat\":\"error\""));
  }

  @Test
  void exportToJson_noOverrunForFastFrames() throws IOException {
    setupFrame(frames[0], 0, 1.0, 1.015); // 15ms duration (<20ms threshold)
    addSpan(frames[0], "FastMethod", 1, "main");

    Path outputPath = tempDir.resolve("trace.json");
    TraceExporter.exportToJson(frames, 0, 1, categories, threadNames, outputPath.toString());

    String content = Files.readString(outputPath);
    assertFalse(content.contains("LOOP OVERRUN"));
  }

  @Test
  void exportToJson_handlesSpecialCharactersInSpanName() throws IOException {
    setupFrame(frames[0], 0, 1.0, 1.01);

    // Add span with special characters
    int idx = frames[0].spanCount++;
    TraceSpan span = frames[0].spans[idx];
    span.name = "Test\"Method\\With\nSpecial\tChars";
    span.complete = true;
    span.threadId = 1;
    span.startNanos = frames[0].startNanos + 1_000_000;
    span.endNanos = frames[0].startNanos + 2_000_000;
    span.categoryIndex = 0;
    threadNames.put(1L, "main");

    Path outputPath = tempDir.resolve("trace.json");
    TraceExporter.exportToJson(frames, 0, 1, categories, threadNames, outputPath.toString());

    String content = Files.readString(outputPath);
    // Escaped characters should be present
    assertTrue(content.contains("\\\""));
    assertTrue(content.contains("\\\\"));
    assertTrue(content.contains("\\n"));
    assertTrue(content.contains("\\t"));
  }

  @Test
  void exportToJson_skipsIncompleteSpans() throws IOException {
    setupFrame(frames[0], 0, 1.0, 1.01);

    // Add complete span
    addSpan(frames[0], "CompleteSpan", 1, "main");

    // Add incomplete span
    int idx = frames[0].spanCount++;
    TraceSpan incompleteSpan = frames[0].spans[idx];
    incompleteSpan.name = "IncompleteSpan";
    incompleteSpan.complete = false;
    incompleteSpan.threadId = 1;

    Path outputPath = tempDir.resolve("trace.json");
    TraceExporter.exportToJson(frames, 0, 1, categories, threadNames, outputPath.toString());

    String content = Files.readString(outputPath);
    assertTrue(content.contains("CompleteSpan"));
    assertFalse(content.contains("IncompleteSpan"));
  }

  @Test
  void exportToJson_skipsEmptyFrames() throws IOException {
    // Frame 0 is empty (spanCount = 0)
    frames[0].spanCount = 0;

    // Frame 1 has data
    setupFrame(frames[1], 1, 1.0, 1.01);
    addSpan(frames[1], "OnlySpan", 1, "main");

    Path outputPath = tempDir.resolve("trace.json");
    TraceExporter.exportRecentToJson(frames, 1, 2, categories, threadNames, outputPath.toString());

    String content = Files.readString(outputPath);
    assertTrue(content.contains("OnlySpan"));
  }

  @Test
  void exportRecentToJson_exportsSpecifiedNumberOfFrames() throws IOException {
    // Set up 5 frames
    for (int i = 0; i < 5; i++) {
      setupFrame(frames[i], i, (double) i, i + 0.01);
      addSpan(frames[i], "Frame" + i, 1, "main");
    }

    Path outputPath = tempDir.resolve("trace.json");
    TraceExporter.exportRecentToJson(frames, 4, 2, categories, threadNames, outputPath.toString());

    String content = Files.readString(outputPath);
    // Should have frames 3 and 4 (most recent 2)
    assertTrue(content.contains("Frame3"));
    assertTrue(content.contains("Frame4"));
    assertFalse(content.contains("Frame0"));
    assertFalse(content.contains("Frame1"));
    assertFalse(content.contains("Frame2"));
  }

  @Test
  void exportToJson_handlesMultipleThreads() throws IOException {
    setupFrame(frames[0], 0, 1.0, 1.01);
    addSpan(frames[0], "MainThreadSpan", 1, "main");
    addSpan(frames[0], "WorkerThreadSpan", 2, "Worker-1");

    Path outputPath = tempDir.resolve("trace.json");
    TraceExporter.exportToJson(frames, 0, 1, categories, threadNames, outputPath.toString());

    String content = Files.readString(outputPath);
    assertTrue(content.contains("\"name\":\"main\""));
    assertTrue(content.contains("\"name\":\"Worker-1\""));
  }

  @Test
  void exportToJson_includes20msBudgetLine() throws IOException {
    setupFrame(frames[0], 0, 1.0, 1.01);
    addSpan(frames[0], "Test", 1, "main");

    Path outputPath = tempDir.resolve("trace.json");
    TraceExporter.exportToJson(frames, 0, 1, categories, threadNames, outputPath.toString());

    String content = Files.readString(outputPath);
    assertTrue(content.contains("\"name\":\"20ms Budget\""));
    assertTrue(content.contains("\"budget\":20.0"));
  }

  @Test
  void exportToJson_circularBufferWraparound() throws IOException {
    // Set up all frames
    for (int i = 0; i < frames.length; i++) {
      setupFrame(frames[i], i, (double) i, i + 0.01);
      addSpan(frames[i], "Span" + i, 1, "main");
    }

    // Simulate circular buffer wraparound
    // Buffer size is 10, current index is 2, we want 5 frames
    // This means frames 8, 9, 0, 1, 2
    Path outputPath = tempDir.resolve("trace.json");
    TraceExporter.exportRecentToJson(frames, 2, 5, categories, threadNames, outputPath.toString());

    String content = Files.readString(outputPath);
    assertTrue(content.contains("Span8"));
    assertTrue(content.contains("Span9"));
    assertTrue(content.contains("Span0"));
    assertTrue(content.contains("Span1"));
    assertTrue(content.contains("Span2"));
  }
}
