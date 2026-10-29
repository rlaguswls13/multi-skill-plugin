package com.msp.eventbus;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Thread-safe in-memory publish-subscribe event bus supporting typed subscriptions,
 * synchronous & asynchronous dispatch, polymorphic event routing, and subscriber exception isolation.
 */
public class EventBus {

    private final ConcurrentHashMap<Class<?>, CopyOnWriteArrayList<SubscriptionImpl<?>>> subscriptions = new ConcurrentHashMap<>();
    private final Executor executor;
    private final SubscriberExceptionHandler exceptionHandler;
    private final DeadLetterHandler deadLetterHandler;
    private final boolean polymorphicDispatch;

    private EventBus(Builder builder) {
        this.executor = builder.executor != null
                ? builder.executor
                : Executors.newVirtualThreadPerTaskExecutor();
        this.exceptionHandler = builder.exceptionHandler != null
                ? builder.exceptionHandler
                : (ex, ctx) -> System.err.printf("[EventBus Error] Exception in subscriber for event %s: %s%n",
                ctx.event().getClass().getSimpleName(), ex.getMessage());
        this.deadLetterHandler = builder.deadLetterHandler;
        this.polymorphicDispatch = builder.polymorphicDispatch;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static EventBus create() {
        return builder().build();
    }

    /**
     * Subscribes a typed consumer to receive events of the specified type (or subtypes if polymorphic).
     */
    @SuppressWarnings("unchecked")
    public <E> Subscription subscribe(Class<E> eventType, Consumer<E> listener) {
        Objects.requireNonNull(eventType, "eventType cannot be null");
        Objects.requireNonNull(listener, "listener cannot be null");

        SubscriptionImpl<E> subscription = new SubscriptionImpl<>(this, eventType, listener);
        subscriptions.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(subscription);
        return subscription;
    }

    /**
     * Publishes an event synchronously to all matching subscribers.
     * Exceptions in individual subscribers are isolated and will not halt dispatch to other subscribers.
     */
    public void publish(Object event) {
        Objects.requireNonNull(event, "event cannot be null");
        List<SubscriptionImpl<?>> matched = findSubscribers(event.getClass());

        if (matched.isEmpty()) {
            if (!(event instanceof DeadLetterEvent)) {
                DeadLetterEvent deadLetter = DeadLetterEvent.of(event);
                if (deadLetterHandler != null) {
                    try {
                        deadLetterHandler.handleDeadLetter(deadLetter);
                    } catch (Throwable t) {
                        System.err.printf("[EventBus Error] DeadLetterHandler failed: %s%n", t.getMessage());
                    }
                }
                // Also dispatch to DeadLetterEvent subscribers if any
                List<SubscriptionImpl<?>> dlqSubscribers = findSubscribers(DeadLetterEvent.class);
                for (SubscriptionImpl<?> sub : dlqSubscribers) {
                    invokeSubscriber(sub, deadLetter);
                }
            }
            return;
        }

        for (SubscriptionImpl<?> sub : matched) {
            invokeSubscriber(sub, event);
        }
    }

    /**
     * Publishes an event asynchronously using the configured Executor and returns a CompletableFuture.
     */
    public CompletableFuture<Void> publishAsync(Object event) {
        Objects.requireNonNull(event, "event cannot be null");
        return CompletableFuture.runAsync(() -> publish(event), executor);
    }

    @SuppressWarnings("unchecked")
    private void invokeSubscriber(SubscriptionImpl<?> sub, Object event) {
        if (!sub.isSubscribed()) {
            return;
        }
        try {
            ((Consumer<Object>) sub.listener()).accept(event);
        } catch (Throwable t) {
            try {
                exceptionHandler.handleException(t, new SubscriberExceptionContext(this, event, sub.eventType(), sub.listener()));
            } catch (Throwable handlerEx) {
                System.err.printf("[EventBus Error] SubscriberExceptionHandler threw exception: %s%n", handlerEx.getMessage());
            }
        }
    }

    private List<SubscriptionImpl<?>> findSubscribers(Class<?> eventClass) {
        if (!polymorphicDispatch) {
            CopyOnWriteArrayList<SubscriptionImpl<?>> list = subscriptions.get(eventClass);
            return list != null ? new ArrayList<>(list) : Collections.emptyList();
        }

        List<SubscriptionImpl<?>> matched = new ArrayList<>();
        subscriptions.forEach((subscribedType, list) -> {
            if (subscribedType.isAssignableFrom(eventClass)) {
                matched.addAll(list);
            }
        });
        return matched;
    }

    public boolean hasSubscribers(Class<?> eventType) {
        return subscriberCount(eventType) > 0;
    }

    public int subscriberCount(Class<?> eventType) {
        CopyOnWriteArrayList<SubscriptionImpl<?>> list = subscriptions.get(eventType);
        return list != null ? list.size() : 0;
    }

    void removeSubscription(SubscriptionImpl<?> subscription) {
        CopyOnWriteArrayList<SubscriptionImpl<?>> list = subscriptions.get(subscription.eventType());
        if (list != null) {
            list.remove(subscription);
            if (list.isEmpty()) {
                subscriptions.remove(subscription.eventType(), list);
            }
        }
    }

    public void clear() {
        subscriptions.clear();
    }

    private static class SubscriptionImpl<E> implements Subscription {
        private final EventBus eventBus;
        private final Class<E> eventType;
        private final Consumer<E> listener;
        private final AtomicBoolean subscribed = new AtomicBoolean(true);

        public SubscriptionImpl(EventBus eventBus, Class<E> eventType, Consumer<E> listener) {
            this.eventBus = eventBus;
            this.eventType = eventType;
            this.listener = listener;
        }

        @Override
        public Class<?> eventType() {
            return eventType;
        }

        @Override
        public boolean isSubscribed() {
            return subscribed.get();
        }

        @Override
        public void unsubscribe() {
            if (subscribed.compareAndSet(true, false)) {
                eventBus.removeSubscription(this);
            }
        }

        public Consumer<E> listener() {
            return listener;
        }
    }

    public static class Builder {
        private Executor executor;
        private SubscriberExceptionHandler exceptionHandler;
        private DeadLetterHandler deadLetterHandler;
        private boolean polymorphicDispatch = true;

        public Builder executor(Executor executor) {
            this.executor = executor;
            return this;
        }

        public Builder exceptionHandler(SubscriberExceptionHandler exceptionHandler) {
            this.exceptionHandler = exceptionHandler;
            return this;
        }

        public Builder deadLetterHandler(DeadLetterHandler deadLetterHandler) {
            this.deadLetterHandler = deadLetterHandler;
            return this;
        }

        public Builder polymorphicDispatch(boolean polymorphicDispatch) {
            this.polymorphicDispatch = polymorphicDispatch;
            return this;
        }

        public EventBus build() {
            return new EventBus(this);
        }
    }
}
