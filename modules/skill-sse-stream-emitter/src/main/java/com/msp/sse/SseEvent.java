package com.msp.sse;

import java.util.Objects;

/**
 * W3C Server-Sent Events (SSE) 이벤트 명세 모델 및 포맷터.
 * event, data, id, retry, comment 필드를 표준 와이어 포맷으로 인코딩합니다.
 */
public record SseEvent(
        String id,
        String event,
        String data,
        Long retry,
        String comment
) {

    public static Builder builder() {
        return new Builder();
    }

    public static SseEvent dataOnly(String data) {
        return builder().data(data).build();
    }

    public static SseEvent of(String event, String data) {
        return builder().event(event).data(data).build();
    }

    public static SseEvent comment(String comment) {
        return builder().comment(comment).build();
    }

    /**
     * W3C SSE 표준 전송 와이어 포맷 문자열로 직렬화합니다.
     */
    public String format() {
        StringBuilder sb = new StringBuilder();

        if (comment != null) {
            for (String line : comment.split("\r?\n")) {
                sb.append(": ").append(line).append("\n");
            }
        }
        if (id != null) {
            sb.append("id: ").append(id).append("\n");
        }
        if (event != null) {
            sb.append("event: ").append(event).append("\n");
        }
        if (retry != null) {
            sb.append("retry: ").append(retry).append("\n");
        }
        if (data != null) {
            for (String line : data.split("\r?\n")) {
                sb.append("data: ").append(line).append("\n");
            }
        }

        sb.append("\n");
        return sb.toString();
    }

    public static final class Builder {
        private String id;
        private String event;
        private String data;
        private Long retry;
        private String comment;

        private Builder() {}

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder event(String event) {
            this.event = event;
            return this;
        }

        public Builder data(String data) {
            this.data = data;
            return this;
        }

        public Builder retry(long retry) {
            this.retry = retry;
            return this;
        }

        public Builder comment(String comment) {
            this.comment = comment;
            return this;
        }

        public SseEvent build() {
            return new SseEvent(id, event, data, retry, comment);
        }
    }
}
