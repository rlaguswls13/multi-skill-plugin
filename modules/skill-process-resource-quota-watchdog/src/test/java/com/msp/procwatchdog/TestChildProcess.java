package com.msp.procwatchdog;

/**
 * Deterministic cross-platform helper subprocess used by {@link ProcessWatchdogTest}.
 */
public final class TestChildProcess {

    public static void main(String[] args) throws Exception {
        String mode = (args.length > 0) ? args[0] : "both";
        switch (mode) {
            case "both" -> {
                System.out.println("stdout-line");
                System.err.println("stderr-line");
                System.out.flush();
                System.err.flush();
            }
            case "heartbeat" -> {
                // Continuously emits output so idle timeout does NOT trigger, allowing wall-clock timeout to fire.
                for (int i = 0; i < 600; i++) {
                    System.out.println("tick-" + i);
                    System.out.flush();
                    Thread.sleep(50);
                }
            }
            case "idle" -> {
                // Emits one line then goes silent to trigger idle timeout.
                System.out.println("started");
                System.out.flush();
                Thread.sleep(30_000);
            }
            case "flood" -> {
                // Writes 1KB chunks repeatedly to trigger output byte quota.
                byte[] chunk = new byte[1024];
                java.util.Arrays.fill(chunk, (byte) 'A');
                for (int i = 0; i < 10_000; i++) {
                    System.out.write(chunk);
                    System.out.flush();
                }
            }
            default -> throw new IllegalArgumentException("Unknown mode: " + mode);
        }
    }
}
