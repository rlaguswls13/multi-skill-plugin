package com.msp.auditlog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Thread-safe audit logger that masks sensitive information and formats events into
 * JSON or Key-Value outputs sent to a designated sink.
 */
public final class AuditLogger {

    private final Consumer<String> sink;
    private final AuditFormat format;
    private final DataMasker masker;

    private AuditLogger(Consumer<String> sink, AuditFormat format, DataMasker masker) {
        this.sink = Objects.requireNonNull(sink, "sink must not be null");
        this.format = Objects.requireNonNull(format, "format must not be null");
        this.masker = masker != null ? masker : DataMasker.defaultMasker();
    }

    /**
     * Creates an AuditLogger with specified configuration.
     */
    public static AuditLogger create(Consumer<String> sink, AuditFormat format, DataMasker masker) {
        return new AuditLogger(sink, format, masker);
    }

    /**
     * Creates an AuditLogger producing JSON formatted log records.
     */
    public static AuditLogger json(Consumer<String> sink) {
        return new AuditLogger(sink, AuditFormat.JSON, DataMasker.defaultMasker());
    }

    /**
     * Creates an AuditLogger producing Key-Value formatted log records.
     */
    public static AuditLogger keyValue(Consumer<String> sink) {
        return new AuditLogger(sink, AuditFormat.KEY_VALUE, DataMasker.defaultMasker());
    }

    /**
     * Creates an in-memory AuditLogger useful for unit tests.
     */
    public static InMemoryAuditLogger inMemory(AuditFormat format) {
        return new InMemoryAuditLogger(format, DataMasker.defaultMasker());
    }

    /**
     * Formats an audit event to a string using this logger's format and data masker.
     */
    public String format(AuditEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        return format.format(event, masker);
    }

    /**
     * Logs an audit event, applying sensitive data masking and writing formatted output to the sink.
     */
    public void log(AuditEvent event) {
        String output = format(event);
        sink.accept(output);
    }

    /**
     * Testing helper logger capturing all outputs in an in-memory list.
     */
    public static final class InMemoryAuditLogger {
        private final List<String> records = Collections.synchronizedList(new ArrayList<>());
        private final AuditLogger logger;

        InMemoryAuditLogger(AuditFormat format, DataMasker masker) {
            this.logger = new AuditLogger(records::add, format, masker);
        }

        public void log(AuditEvent event) {
            logger.log(event);
        }

        public String format(AuditEvent event) {
            return logger.format(event);
        }

        public List<String> getRecords() {
            return new ArrayList<>(records);
        }

        public void clear() {
            records.clear();
        }
    }
}
