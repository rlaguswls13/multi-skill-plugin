package com.msp.sse;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * 다중 SSE 클라이언트 구독자 관리 및 브로드캐스트 디스패처.
 */
public final class SseBroadcaster {

    private final CopyOnWriteArrayList<Consumer<String>> subscribers = new CopyOnWriteArrayList<>();

    public AutoCloseable subscribe(Consumer<String> consumer) {
        subscribers.add(consumer);
        return () -> subscribers.remove(consumer);
    }

    public void broadcast(SseEvent event) {
        String formatted = event.format();
        for (Consumer<String> sub : subscribers) {
            try {
                sub.accept(formatted);
            } catch (Exception ignored) {
                subscribers.remove(sub);
            }
        }
    }

    public void ping() {
        broadcast(SseEvent.comment("keepalive"));
    }

    public int subscriberCount() {
        return subscribers.size();
    }
}
