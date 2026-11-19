package com.msp.streammux;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Functional sink for consuming multiplexed stream lines.
 */
@FunctionalInterface
public interface StreamSink extends Consumer<StreamLine> {

    static StreamSink toConsumer(Consumer<StreamLine> consumer) {
        Objects.requireNonNull(consumer, "consumer cannot be null");
        return consumer::accept;
    }

    static StreamSink toList(List<StreamLine> targetList) {
        Objects.requireNonNull(targetList, "targetList cannot be null");
        return line -> {
            synchronized (targetList) {
                targetList.add(line);
            }
        };
    }

    static StreamSink toOutputStream(OutputStream os, Function<StreamLine, String> formatter) {
        Objects.requireNonNull(os, "os cannot be null");
        Objects.requireNonNull(formatter, "formatter cannot be null");
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8));
        return line -> {
            synchronized (writer) {
                try {
                    writer.write(formatter.apply(line));
                    writer.newLine();
                    writer.flush();
                } catch (IOException e) {
                    throw new RuntimeException("Error writing to multiplexed output stream", e);
                }
            }
        };
    }

    static StreamSink toPrintStream(PrintStream ps, Function<StreamLine, String> formatter) {
        Objects.requireNonNull(ps, "ps cannot be null");
        Objects.requireNonNull(formatter, "formatter cannot be null");
        return line -> {
            synchronized (ps) {
                ps.println(formatter.apply(line));
                ps.flush();
            }
        };
    }
}
