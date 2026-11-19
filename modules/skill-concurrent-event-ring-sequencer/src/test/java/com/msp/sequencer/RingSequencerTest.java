package com.msp.sequencer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class RingSequencerTest {

    static final class ValueEvent {
        long value;
        String message;

        public void reset() {
            value = 0L;
            message = null;
        }
    }

    @Test
    @DisplayName("Buffer size must be a positive power of 2")
    void testBufferSizeValidation() {
        assertThrows(IllegalArgumentException.class, () ->
                RingBuffer.createSingleProducer(ValueEvent::new, 0));
        assertThrows(IllegalArgumentException.class, () ->
                RingBuffer.createSingleProducer(ValueEvent::new, 100));
        assertThrows(IllegalArgumentException.class, () ->
                RingBuffer.createSingleProducer(ValueEvent::new, -8));

        assertDoesNotThrow(() ->
                RingBuffer.createSingleProducer(ValueEvent::new, 1024));
    }

    @Test
    @DisplayName("Single-producer claim and publish single events")
    void testSingleProducerPublish() {
        RingBuffer<ValueEvent> ringBuffer = RingBuffer.createSingleProducer(ValueEvent::new, 16);

        long seq1 = ringBuffer.next();
        ValueEvent event1 = ringBuffer.get(seq1);
        event1.value = 100L;
        ringBuffer.publish(seq1);

        assertEquals(0L, ringBuffer.getCursor());
        assertEquals(100L, ringBuffer.get(0L).value);

        long seq2 = ringBuffer.next();
        ValueEvent event2 = ringBuffer.get(seq2);
        event2.value = 200L;
        ringBuffer.publish(seq2);

        assertEquals(1L, ringBuffer.getCursor());
        assertEquals(200L, ringBuffer.get(1L).value);
    }

    @Test
    @DisplayName("Batch claim and batch publish")
    void testBatchClaimAndPublish() {
        RingBuffer<ValueEvent> ringBuffer = RingBuffer.createSingleProducer(ValueEvent::new, 16);

        long hi = ringBuffer.claim(5);
        long lo = hi - 5 + 1;
        assertEquals(0L, lo);
        assertEquals(4L, hi);

        for (long seq = lo; seq <= hi; seq++) {
            ringBuffer.get(seq).value = seq * 10;
        }
        ringBuffer.publish(lo, hi);

        assertEquals(4L, ringBuffer.getCursor());
        for (long seq = lo; seq <= hi; seq++) {
            assertEquals(seq * 10, ringBuffer.get(seq).value);
        }
    }

    @Test
    @DisplayName("Publish via EventTranslator and EventTranslatorOneArg")
    void testTranslators() {
        RingBuffer<ValueEvent> ringBuffer = RingBuffer.createSingleProducer(ValueEvent::new, 16);

        ringBuffer.publishEvent((event, seq) -> {
            event.value = 42L;
            event.message = "hello";
        });
        assertEquals(0L, ringBuffer.getCursor());
        assertEquals(42L, ringBuffer.get(0).value);
        assertEquals("hello", ringBuffer.get(0).message);

        ringBuffer.publishEvent((event, seq, text) -> {
            event.value = 99L;
            event.message = text;
        }, "custom-arg");
        assertEquals(1L, ringBuffer.getCursor());
        assertEquals(99L, ringBuffer.get(1).value);
        assertEquals("custom-arg", ringBuffer.get(1).message);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("End-to-end Producer and BatchEventProcessor with 10,000 events")
    void testProducerConsumerPipeline() throws Exception {
        int count = 10_000;
        RingBuffer<ValueEvent> ringBuffer = RingBuffer.createSingleProducer(ValueEvent::new, 1024, new YieldingWaitStrategy());
        SequenceBarrier barrier = ringBuffer.newBarrier();

        List<Long> received = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch latch = new CountDownLatch(count);

        BatchEventProcessor<ValueEvent> processor = new BatchEventProcessor<>(ringBuffer, barrier, (event, seq, endOfBatch) -> {
            received.add(event.value);
            latch.countDown();
        });

        ringBuffer.addGatingSequence(processor.getSequence());

        Thread consumerThread = Thread.ofPlatform().start(processor);

        for (int i = 0; i < count; i++) {
            long finalI = i;
            ringBuffer.publishEvent((event, seq) -> event.value = finalI);
        }

        assertTrue(latch.await(4, TimeUnit.SECONDS), "Timed out waiting for events to be processed");
        processor.halt();
        consumerThread.join(1000);

        assertEquals(count, received.size());
        for (int i = 0; i < count; i++) {
            assertEquals((long) i, received.get(i));
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("All wait strategies function correctly with BatchEventProcessor")
    void testWaitStrategies() throws Exception {
        WaitStrategy[] strategies = new WaitStrategy[]{
                new BusySpinWaitStrategy(),
                new YieldingWaitStrategy(),
                new BlockingWaitStrategy(),
                new SleepingWaitStrategy()
        };

        for (WaitStrategy strategy : strategies) {
            RingBuffer<ValueEvent> ringBuffer = RingBuffer.createSingleProducer(ValueEvent::new, 64, strategy);
            SequenceBarrier barrier = ringBuffer.newBarrier();

            AtomicInteger count = new AtomicInteger(0);
            CountDownLatch latch = new CountDownLatch(50);

            BatchEventProcessor<ValueEvent> processor = new BatchEventProcessor<>(ringBuffer, barrier, (event, seq, endOfBatch) -> {
                count.incrementAndGet();
                latch.countDown();
            });

            ringBuffer.addGatingSequence(processor.getSequence());
            Thread thread = Thread.ofPlatform().start(processor);

            for (int i = 0; i < 50; i++) {
                ringBuffer.publishEvent((event, seq) -> event.value = seq);
            }

            assertTrue(latch.await(2, TimeUnit.SECONDS), "Strategy failed: " + strategy.getClass().getSimpleName());
            processor.halt();
            thread.join(1000);

            assertEquals(50, count.get(), "Mismatch for strategy: " + strategy.getClass().getSimpleName());
        }
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    @DisplayName("Gating sequence prevents producer from overwriting unconsumed buffer slots")
    void testGatingSequencePreventsOverwrite() throws Exception {
        int bufferSize = 4;
        RingBuffer<ValueEvent> ringBuffer = RingBuffer.createSingleProducer(ValueEvent::new, bufferSize, new BlockingWaitStrategy());
        Sequence gatingSeq = new Sequence(-1L);
        ringBuffer.addGatingSequence(gatingSeq);

        // Fill buffer to capacity (4 slots: 0, 1, 2, 3)
        for (int i = 0; i < 4; i++) {
            ringBuffer.publishEvent((event, seq) -> event.value = seq);
        }

        // Now buffer is full (gating sequence is -1, next claim would wrap past 4 - bufferSize = 0)
        AtomicLong fifthClaimed = new AtomicLong(-1);
        CountDownLatch producerStarted = new CountDownLatch(1);

        Thread producerThread = Thread.ofPlatform().start(() -> {
            producerStarted.countDown();
            long seq = ringBuffer.next(); // Should block until gatingSeq is advanced
            fifthClaimed.set(seq);
            ringBuffer.publish(seq);
        });

        assertTrue(producerStarted.await(1, TimeUnit.SECONDS));
        Thread.sleep(100); // Verify producer is waiting
        assertEquals(-1, fifthClaimed.get(), "Producer should be blocked by gating sequence");

        // Advance gating sequence to consume slot 0
        gatingSeq.set(0L);

        // Producer should now proceed and claim slot 4
        producerThread.join(2000);
        assertEquals(4L, fifthClaimed.get(), "Producer should have claimed sequence 4");
    }

    @Test
    @DisplayName("Sequence removeGatingSequence")
    void testRemoveGatingSequence() {
        RingSequencer sequencer = new RingSequencer(16, new BusySpinWaitStrategy());
        Sequence s1 = new Sequence(0);
        Sequence s2 = new Sequence(5);

        sequencer.addGatingSequence(s1, s2);
        assertEquals(2, sequencer.getGatingSequences().length);
        assertEquals(0, sequencer.getMinimumGatingSequence());

        boolean removed = sequencer.removeGatingSequence(s1);
        assertTrue(removed);
        assertEquals(1, sequencer.getGatingSequences().length);
        assertEquals(5, sequencer.getMinimumGatingSequence());

        assertFalse(sequencer.removeGatingSequence(new Sequence(10)));
    }
}
