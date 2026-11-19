package com.msp.streammux;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Thread-safe multi-process standard stream multiplexer combining stdout/stderr of multiple
 * child processes into a single unified stream using Virtual Threads.
 */
public final class ProcessStreamMultiplexer implements AutoCloseable {

    public record StreamSource(String tag, StreamType type, InputStream stream, Charset charset) {
        public StreamSource {
            Objects.requireNonNull(tag, "tag cannot be null");
            Objects.requireNonNull(type, "type cannot be null");
            Objects.requireNonNull(stream, "stream cannot be null");
            if (charset == null) {
                charset = StandardCharsets.UTF_8;
            }
        }
    }

    private final List<StreamSource> sources;
    private final Consumer<StreamLine> sink;
    private final Object sinkLock = new Object();
    private final AtomicLong sequence = new AtomicLong(0);
    private final Instant startTime = Instant.now();
    private final List<Thread> workerThreads = new CopyOnWriteArrayList<>();
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private ProcessStreamMultiplexer(List<StreamSource> sources, Consumer<StreamLine> sink) {
        this.sources = List.copyOf(sources);
        this.sink = Objects.requireNonNull(sink, "sink cannot be null");
    }

    public static Builder builder() {
        return new Builder();
    }

    public Instant getStartTime() {
        return startTime;
    }

    public long getTotalLinesProcessed() {
        return sequence.get();
    }

    /**
     * Starts multiplexing reader threads using Java 21 Virtual Threads.
     */
    public synchronized ProcessStreamMultiplexer start() {
        if (!started.compareAndSet(false, true)) {
            throw new IllegalStateException("ProcessStreamMultiplexer is already started");
        }

        for (StreamSource source : sources) {
            Thread thread = Thread.ofVirtual()
                    .name("streammux-" + source.tag() + "-" + source.type().name().toLowerCase())
                    .start(() -> readStream(source));
            workerThreads.add(thread);
        }

        return this;
    }

    private void readStream(StreamSource source) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(source.stream(), source.charset()))) {
            String line;
            while (!closed.get() && (line = reader.readLine()) != null) {
                long seq = sequence.incrementAndGet();
                StreamLine streamLine = new StreamLine(seq, source.tag(), source.type(), Instant.now(), line);
                synchronized (sinkLock) {
                    sink.accept(streamLine);
                }
            }
        } catch (IOException ignored) {
            // Stream closed or process terminated
        }
    }

    /**
     * Waits indefinitely for all input streams to finish (reach EOF).
     */
    public boolean awaitCompletion() throws InterruptedException {
        for (Thread thread : workerThreads) {
            thread.join();
        }
        return true;
    }

    /**
     * Waits up to timeout for all input streams to finish (reach EOF).
     */
    public boolean awaitCompletion(Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        for (Thread thread : workerThreads) {
            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0) {
                return false;
            }
            thread.join(Duration.ofNanos(remainingNanos));
            if (thread.isAlive()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public synchronized void close() {
        if (closed.compareAndSet(false, true)) {
            for (StreamSource source : sources) {
                try {
                    source.stream().close();
                } catch (IOException ignored) {}
            }
            for (Thread thread : workerThreads) {
                thread.interrupt();
            }
        }
    }

    public static final class Builder {
        private final List<StreamSource> sources = new ArrayList<>();
        private Consumer<StreamLine> sink;

        public Builder sink(Consumer<StreamLine> sink) {
            this.sink = sink;
            return this;
        }

        public Builder attach(String tag, StreamType type, InputStream inputStream) {
            return attach(tag, type, inputStream, StandardCharsets.UTF_8);
        }

        public Builder attach(String tag, StreamType type, InputStream inputStream, Charset charset) {
            sources.add(new StreamSource(tag, type, inputStream, charset));
            return this;
        }

        public Builder attach(String tag, Process process) {
            Objects.requireNonNull(process, "process cannot be null");
            attach(tag, StreamType.STDOUT, process.getInputStream());
            attach(tag, StreamType.STDERR, process.getErrorStream());
            return this;
        }

        public ProcessStreamMultiplexer build() {
            if (sink == null) {
                throw new IllegalStateException("sink must be configured before building");
            }
            return new ProcessStreamMultiplexer(sources, sink);
        }
    }
}
