package com.msp.streammux;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ProcessStreamMultiplexerTest {

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("Multiplex multiple simulated streams with proper tagging")
    void testMultiplexStreams() throws Exception {
        String stdout1 = "worker-1 line 1\nworker-1 line 2\n";
        String stderr1 = "worker-1 error 1\n";
        String stdout2 = "worker-2 line A\nworker-2 line B\nworker-2 line C\n";

        List<StreamLine> output = Collections.synchronizedList(new ArrayList<>());

        try (ProcessStreamMultiplexer mux = ProcessStreamMultiplexer.builder()
                .sink(output::add)
                .attach("worker-1", StreamType.STDOUT, new ByteArrayInputStream(stdout1.getBytes(StandardCharsets.UTF_8)))
                .attach("worker-1", StreamType.STDERR, new ByteArrayInputStream(stderr1.getBytes(StandardCharsets.UTF_8)))
                .attach("worker-2", StreamType.STDOUT, new ByteArrayInputStream(stdout2.getBytes(StandardCharsets.UTF_8)))
                .build()) {

            mux.start();
            assertTrue(mux.awaitCompletion(Duration.ofSeconds(3)));

            assertEquals(6, output.size());
            assertEquals(6, mux.getTotalLinesProcessed());

            // Check sequences are monotonic 1..6
            for (int i = 0; i < output.size(); i++) {
                assertEquals(i + 1, output.get(i).sequence());
            }

            // Verify content exists
            List<String> texts = output.stream().map(StreamLine::text).toList();
            assertTrue(texts.contains("worker-1 line 1"));
            assertTrue(texts.contains("worker-1 line 2"));
            assertTrue(texts.contains("worker-1 error 1"));
            assertTrue(texts.contains("worker-2 line A"));
            assertTrue(texts.contains("worker-2 line B"));
            assertTrue(texts.contains("worker-2 line C"));

            // Verify tag matching
            StreamLine errLine = output.stream()
                    .filter(l -> l.text().equals("worker-1 error 1"))
                    .findFirst()
                    .orElseThrow();
            assertEquals("worker-1", errLine.processTag());
            assertEquals(StreamType.STDERR, errLine.streamType());
        }
    }

    @Test
    @DisplayName("Formatted line representations")
    void testFormattedOutput() {
        Instant now = Instant.parse("2026-11-01T12:00:00Z");
        StreamLine line = new StreamLine(1, "service-auth", StreamType.STDOUT, now, "Server started on port 8080");

        assertEquals("2026-11-01T12:00:00Z [service-auth] [STDOUT] Server started on port 8080", line.formatted());
        assertEquals("2026-11-01T12:00:00Z [service-auth] [STDOUT] Server started on port 8080", line.toString());

        Instant base = Instant.parse("2026-11-01T11:59:59.500Z");
        assertEquals("+500ms [service-auth] [STDOUT] Server started on port 8080", line.formattedElapsed(base));
    }

    @Test
    @DisplayName("StreamSink to OutputStream")
    void testOutputStreamSink() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        StreamSink sink = StreamSink.toOutputStream(baos, StreamLine::formatted);

        Instant now = Instant.parse("2026-11-01T12:00:00Z");
        sink.accept(new StreamLine(1, "proc-A", StreamType.STDOUT, now, "Hello"));
        sink.accept(new StreamLine(2, "proc-B", StreamType.STDERR, now, "World"));

        String result = baos.toString(StandardCharsets.UTF_8);
        assertTrue(result.contains("[proc-A] [STDOUT] Hello"));
        assertTrue(result.contains("[proc-B] [STDERR] World"));
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("Multiplex live child process stdout and stderr")
    void testLiveProcessMultiplexing() throws Exception {
        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        ProcessBuilder pb = isWindows
                ? new ProcessBuilder("cmd.exe", "/c", "echo hello from cmd && echo error from cmd 1>&2")
                : new ProcessBuilder("sh", "-c", "echo 'hello from cmd' && echo 'error from cmd' 1>&2");
        Process process = pb.start();

        List<StreamLine> lines = Collections.synchronizedList(new ArrayList<>());

        try (ProcessStreamMultiplexer mux = ProcessStreamMultiplexer.builder()
                .sink(lines::add)
                .attach("child-cmd", process)
                .build()) {

            mux.start();
            process.waitFor(2, TimeUnit.SECONDS);
            assertTrue(mux.awaitCompletion(Duration.ofSeconds(3)));

            List<String> texts = lines.stream().map(l -> l.text().trim()).toList();
            assertTrue(texts.contains("hello from cmd"));
            assertTrue(texts.contains("error from cmd"));

            boolean hasStdout = lines.stream().anyMatch(l -> l.streamType() == StreamType.STDOUT && l.text().trim().equals("hello from cmd"));
            boolean hasStderr = lines.stream().anyMatch(l -> l.streamType() == StreamType.STDERR && l.text().trim().equals("error from cmd"));
            assertTrue(hasStdout, "Expected stdout line");
            assertTrue(hasStderr, "Expected stderr line");
        }
    }
}
