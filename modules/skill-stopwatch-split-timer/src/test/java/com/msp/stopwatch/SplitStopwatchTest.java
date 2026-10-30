package com.msp.stopwatch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SplitStopwatchTest {

    @Test
    @DisplayName("Record split laps and verify cumulative and relative timings")
    void testSplitLapTimings() throws InterruptedException {
        SplitStopwatch sw = SplitStopwatch.start("OrderCheckoutPipeline");

        Thread.sleep(20);
        SplitLap lap1 = sw.split("ValidateInventory");

        Thread.sleep(30);
        SplitLap lap2 = sw.split("ChargePaymentGateway");

        Thread.sleep(10);
        SplitLap lap3 = sw.split("SendNotificationSms");

        sw.stop();

        assertFalse(sw.isRunning());
        assertEquals("OrderCheckoutPipeline", sw.getTaskName());

        List<SplitLap> laps = sw.getLaps();
        assertEquals(3, laps.size());

        assertTrue(lap1.lapMillis() >= 15.0);
        assertTrue(lap2.lapMillis() >= 25.0);
        assertTrue(lap3.lapMillis() >= 8.0);

        assertTrue(lap2.cumulativeMillis() > lap1.cumulativeMillis());
        assertTrue(lap3.cumulativeMillis() > lap2.cumulativeMillis());

        // Verify pretty print output formatting
        String report = sw.prettyPrint();
        assertNotNull(report);
        assertTrue(report.contains("OrderCheckoutPipeline"));
        assertTrue(report.contains("ValidateInventory"));
        assertTrue(report.contains("ChargePaymentGateway"));
        assertTrue(report.contains("SendNotificationSms"));
    }
}
