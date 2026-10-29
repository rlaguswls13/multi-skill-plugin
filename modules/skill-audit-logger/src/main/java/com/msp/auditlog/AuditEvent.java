package com.msp.auditlog;

import java.time.Instant;
import java.util.*;

/**
 * Immutable audit event representing an action performed by an actor on a resource.
 *
 * @param id        unique event identifier (e.g., UUID)
 * @param timestamp timestamp when the event occurred
 * @param actor     identifier of the user, system, or principal initiating the action
 * @param action    name of the action performed (e.g. USER_LOGIN, ORDER_REFUND)
 * @param resource  target resource identifier (e.g. User/42, Order/1001)
 * @param clientIp  remote IP address of the client
 * @param status    outcome status (e.g. SUCCESS, FAILURE, DENIED)
 * @param details   key-value attributes providing additional metadata
 * @param diffs     tracked field changes before and after the action
 */
public record AuditEvent(
        String id,
        Instant timestamp,
        String actor,
        String action,
        String resource,
        String clientIp,
        String status,
        Map<String, Object> details,
        List<FieldDiff> diffs
) {
    public AuditEvent {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        Objects.requireNonNull(action, "action must not be null");

        actor = actor != null ? actor : "anonymous";
        status = status != null ? status : "SUCCESS";
        details = details != null ? Collections.unmodifiableMap(new LinkedHashMap<>(details)) : Collections.emptyMap();
        diffs = diffs != null ? Collections.unmodifiableList(new ArrayList<>(diffs)) : Collections.emptyList();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String id;
        private Instant timestamp;
        private String actor;
        private String action;
        private String resource;
        private String clientIp;
        private String status = "SUCCESS";
        private final Map<String, Object> details = new LinkedHashMap<>();
        private final List<FieldDiff> diffs = new ArrayList<>();

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder actor(String actor) {
            this.actor = actor;
            return this;
        }

        public Builder action(String action) {
            this.action = action;
            return this;
        }

        public Builder resource(String resource) {
            this.resource = resource;
            return this;
        }

        public Builder clientIp(String clientIp) {
            this.clientIp = clientIp;
            return this;
        }

        public Builder status(String status) {
            this.status = status;
            return this;
        }

        public Builder detail(String key, Object value) {
            if (key != null) {
                this.details.put(key, value);
            }
            return this;
        }

        public Builder details(Map<String, ?> details) {
            if (details != null) {
                this.details.putAll(details);
            }
            return this;
        }

        public Builder diff(String field, Object oldValue, Object newValue) {
            this.diffs.add(new FieldDiff(field, oldValue, newValue));
            return this;
        }

        public Builder diff(FieldDiff diff) {
            if (diff != null) {
                this.diffs.add(diff);
            }
            return this;
        }

        public Builder diffs(Collection<FieldDiff> diffs) {
            if (diffs != null) {
                this.diffs.addAll(diffs);
            }
            return this;
        }

        public AuditEvent build() {
            String eventId = this.id != null ? this.id : UUID.randomUUID().toString();
            Instant eventTs = this.timestamp != null ? this.timestamp : Instant.now();
            return new AuditEvent(
                    eventId,
                    eventTs,
                    this.actor,
                    this.action != null ? this.action : "UNKNOWN_ACTION",
                    this.resource,
                    this.clientIp,
                    this.status,
                    this.details,
                    this.diffs
            );
        }
    }
}
