package com.msp.streampump;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ProcessStreamPumper Tests")
class ProcessStreamPumperTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("pumpAsync transfers bytes faithfully between streams")
    void testStreamToStreamTransfer() throws ExecutionException, InterruptedException, TimeoutException {
        byte[] payload = "Hello Virtual Threads Stream Pumper!".getBytes(StandardCharsets.UTF_8);
        ByteArrayInputStream in = new ByteArrayInputStream(payload);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        CompletableFuture<StreamPumpResult> future = ProcessStreamPumper.pumpAsync(in, out, 16);
        StreamPumpResult result = future.get(5, TimeUnit.SECONDS);

        assertTrue(result.isSuccess());
        assertNull(result.error());
        assertEquals(payload.length, result.bytesPumped());
        assertNotNull(result.duration());
        assertArrayEquals(payload, out.toByteArray());
    }

    @Test
    @DisplayName("pumpToFileAsync transfers stream to disk destination file")
    void testFilePumping() throws ExecutionException, InterruptedException, TimeoutException, IOException {
        String data = "Writing stream content directly to disk without memory overhead.\nSecond Line.";
        ByteArrayInputStream in = new ByteArrayInputStream(data.getBytes(StandardCharsets.UTF_8));
        Path targetFile = tempDir.resolve("output_stream.txt");

        CompletableFuture<StreamPumpResult> future = ProcessStreamPumper.pumpToFileAsync(in, targetFile);
        StreamPumpResult result = future.get(5, TimeUnit.SECONDS);

        assertTrue(result.isSuccess());
        assertTrue(Files.exists(targetFile));
        assertEquals(data, Files.readString(targetFile, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("pumpToLineConsumer delivers lines to callback consumer")
    void testLineConsumerStreaming() throws ExecutionException, InterruptedException, TimeoutException {
        String multiline = "Line 1: init\nLine 2: processing\nLine 3: done";
        ByteArrayInputStream in = new ByteArrayInputStream(multiline.getBytes(StandardCharsets.UTF_8));
        List<String> collected = new ArrayList<>();

        CompletableFuture<StreamPumpResult> future = ProcessStreamPumper.pumpToLineConsumer(in, collected::add);
        StreamPumpResult result = future.get(5, TimeUnit.SECONDS);

        assertTrue(result.isSuccess());
        assertEquals(3, collected.size());
        assertEquals("Line 1: init", collected.get(0));
        assertEquals("Line 2: processing", collected.get(1));
        assertEquals("Line 3: done", collected.get(2));
        assertTrue(result.bytesPumped() > 0);
    }

    @Test
    @DisplayName("Large payload 1MB transfer safely completes")
    void testLargePayloadTransfer() throws ExecutionException, InterruptedException, TimeoutException {
        int size = 1024 * 1024; // 1 MB
        byte[] bigData = new byte[size];
        for (int i = 0; i < size; i++) {
            bigData[i] = (byte) (i & 0xFF);
        }

        ByteArrayInputStream in = new ByteArrayInputStream(bigData);
        ByteArrayOutputStream out = new ByteArrayOutputStream(size);

        CompletableFuture<StreamPumpResult> future = ProcessStreamPumper.pumpAsync(in, out);
        StreamPumpResult result = future.get(10, TimeUnit.SECONDS);

        assertTrue(result.isSuccess());
        assertEquals(size, result.bytesPumped());
        assertArrayEquals(bigData, out.toByteArray());
    }

    @Test
    @DisplayName("Error handling completes future with failure result on stream exception")
    void testErrorHandling() throws ExecutionException, InterruptedException, TimeoutException {
        InputStream faultyStream = new InputStream() {
            private int count = 0;

            @Override
            public int read() throws IOException {
                if (count++ > 5) {
                    throw new IOException("Simulated broken pipe");
                }
                return 42;
            }
        };

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        CompletableFuture<StreamPumpResult> future = ProcessStreamPumper.pumpAsync(faultyStream, out, 2);
        StreamPumpResult result = future.get(5, TimeUnit.SECONDS);

        assertFalse(result.isSuccess());
        assertNotNull(result.error());
        assertTrue(result.error() instanceof IOException);
        assertEquals("Simulated broken pipe", result.error().getMessage());
        assertTrue(result.errorOptional().isPresent());
    }

    @Test
    @DisplayName("Parameter validation checks")
    void testValidation() {
        ByteArrayInputStream in = new ByteArrayInputStream(new byte[10]);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        assertThrows(NullPointerException.class, () -> ProcessStreamPumper.pumpAsync(null, out));
        assertThrows(NullPointerException.class, () -> ProcessStreamPumper.pumpAsync(in, null));
        assertThrows(IllegalArgumentException.class, () -> ProcessStreamPumper.pumpAsync(in, out, 0));
        assertThrows(IllegalArgumentException.class, () -> ProcessStreamPumper.pumpAsync(in, out, -10));

        assertThrows(NullPointerException.class, () -> ProcessStreamPumper.pumpToFileAsync(in, null));
        assertThrows(NullPointerException.class, () -> ProcessStreamPumper.pumpToLineConsumer(in, null));
    }
}
