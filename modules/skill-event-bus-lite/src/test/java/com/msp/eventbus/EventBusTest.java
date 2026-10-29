package com.msp.eventbus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class EventBusTest {

    private EventBus eventBus;

    @BeforeEach
    void setUp() {
        eventBus = EventBus.create();
    }

    record OrderPlaced(String orderId, int amount) {}
    record UserRegistered(String username) {}

    sealed interface DomainEvent permits ItemCreated, ItemDeleted {}
    record ItemCreated(String itemId) implements DomainEvent {}
    record ItemDeleted(String itemId) implements DomainEvent {}

    @Test
    void testSynchronousDispatch() {
        List<String> received = new ArrayList<>();
        eventBus.subscribe(OrderPlaced.class, e -> received.add(e.orderId()));

        eventBus.publish(new OrderPlaced("ORD-1", 100));
        eventBus.publish(new OrderPlaced("ORD-2", 200));

        assertEquals(List.of("ORD-1", "ORD-2"), received);
    }

    @Test
    void testAsynchronousDispatch() throws Exception {
        List<String> received = new CopyOnWriteArrayList<>();
        eventBus.subscribe(UserRegistered.class, e -> received.add(e.username()));

        CompletableFuture<Void> future = eventBus.publishAsync(new UserRegistered("alice"));
        future.get();

        assertEquals(List.of("alice"), received);
    }

    @Test
    void testMultipleSubscribersForSameEvent() {
        AtomicInteger count1 = new AtomicInteger();
        AtomicInteger count2 = new AtomicInteger();

        eventBus.subscribe(OrderPlaced.class, e -> count1.incrementAndGet());
        eventBus.subscribe(OrderPlaced.class, e -> count2.incrementAndGet());

        eventBus.publish(new OrderPlaced("ORD-1", 50));

        assertEquals(1, count1.get());
        assertEquals(1, count2.get());
    }

    @Test
    void testExceptionIsolation() {
        AtomicInteger errorHandledCount = new AtomicInteger();
        AtomicBoolean healthySubscriberExecuted = new AtomicBoolean(false);

        EventBus bus = EventBus.builder()
                .exceptionHandler((ex, ctx) -> {
                    assertEquals("Crashing subscriber", ex.getMessage());
                    errorHandledCount.incrementAndGet();
                })
                .build();

        // Faulty subscriber
        bus.subscribe(OrderPlaced.class, e -> {
            throw new RuntimeException("Crashing subscriber");
        });

        // Healthy subscriber
        bus.subscribe(OrderPlaced.class, e -> healthySubscriberExecuted.set(true));

        // Publish should not throw exception
        assertDoesNotThrow(() -> bus.publish(new OrderPlaced("ORD-99", 10)));

        assertEquals(1, errorHandledCount.get());
        assertTrue(healthySubscriberExecuted.get(), "Healthy subscriber must execute despite another failing");
    }

    @Test
    void testUnsubscribe() {
        List<String> received = new ArrayList<>();
        Subscription sub = eventBus.subscribe(OrderPlaced.class, e -> received.add(e.orderId()));

        assertTrue(sub.isSubscribed());
        assertEquals(1, eventBus.subscriberCount(OrderPlaced.class));

        eventBus.publish(new OrderPlaced("ORD-1", 100));
        assertEquals(1, received.size());

        sub.unsubscribe();
        assertFalse(sub.isSubscribed());
        assertEquals(0, eventBus.subscriberCount(OrderPlaced.class));

        eventBus.publish(new OrderPlaced("ORD-2", 200));
        assertEquals(1, received.size(), "Unsubscribed listener should not receive subsequent events");
    }

    @Test
    void testDeadLetterHandling() {
        List<Object> deadLetters = new ArrayList<>();
        EventBus bus = EventBus.builder()
                .deadLetterHandler(dl -> deadLetters.add(dl.event()))
                .build();

        bus.publish(new UserRegistered("ghost"));

        assertEquals(1, deadLetters.size());
        assertInstanceOf(UserRegistered.class, deadLetters.getFirst());
    }

    @Test
    void testPolymorphicDispatch() {
        List<String> polymorphicEvents = new ArrayList<>();
        List<String> specificEvents = new ArrayList<>();

        eventBus.subscribe(DomainEvent.class, e -> {
            if (e instanceof ItemCreated c) polymorphicEvents.add("created:" + c.itemId());
            if (e instanceof ItemDeleted d) polymorphicEvents.add("deleted:" + d.itemId());
        });

        eventBus.subscribe(ItemCreated.class, e -> specificEvents.add(e.itemId()));

        eventBus.publish(new ItemCreated("item-1"));
        eventBus.publish(new ItemDeleted("item-2"));

        assertEquals(List.of("created:item-1", "deleted:item-2"), polymorphicEvents);
        assertEquals(List.of("item-1"), specificEvents);
    }
}
