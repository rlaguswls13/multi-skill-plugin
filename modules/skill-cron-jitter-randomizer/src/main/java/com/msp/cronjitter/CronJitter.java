package com.msp.cronjitter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 대규모 분산 배치 작업이 정각에 일제히 집중되는 Thundering Herd(스탬피드) 현상을 방어하기 위해
 * 예약 시각에 난수 또는 키 기반 고정 지터(Jitter)를 가산하는 스케줄러 보조기.
 */
public final class CronJitter {

    private CronJitter() {}

    /**
     * [0, maxJitter] 범위의 완전 무작위 지터를 예약 시각에 가산합니다.
     */
    public static Instant applyRandomJitter(Instant scheduledTime, Duration maxJitter) {
        Objects.requireNonNull(scheduledTime, "scheduledTime must not be null");
        Objects.requireNonNull(maxJitter, "maxJitter must not be null");
        long maxMillis = maxJitter.toMillis();
        if (maxMillis <= 0) return scheduledTime;

        long jitterMillis = ThreadLocalRandom.current().nextLong(maxMillis + 1);
        return scheduledTime.plusMillis(jitterMillis);
    }

    /**
     * [-maxJitter, +maxJitter] 대칭 범위의 지터를 가산합니다.
     */
    public static Instant applySymmetricJitter(Instant scheduledTime, Duration maxJitter) {
        Objects.requireNonNull(scheduledTime, "scheduledTime must not be null");
        Objects.requireNonNull(maxJitter, "maxJitter must not be null");
        long maxMillis = maxJitter.toMillis();
        if (maxMillis <= 0) return scheduledTime;

        long jitterMillis = ThreadLocalRandom.current().nextLong(-maxMillis, maxMillis + 1);
        return scheduledTime.plusMillis(jitterMillis);
    }

    /**
     * 특정 식별자(서버 ID, 테넌트 ID, 고객 ID)에 대해 항상 일관된(결정론적) 오프셋을 부여하는 지터입니다.
     */
    public static Instant applyDeterministicJitter(Instant scheduledTime, String key, Duration maxJitter) {
        Objects.requireNonNull(scheduledTime, "scheduledTime must not be null");
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(maxJitter, "maxJitter must not be null");
        long maxMillis = maxJitter.toMillis();
        if (maxMillis <= 0) return scheduledTime;

        long hash = Math.abs(hashString(key));
        long jitterMillis = hash % (maxMillis + 1);
        return scheduledTime.plusMillis(jitterMillis);
    }

    private static long hashString(String str) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(str.getBytes(StandardCharsets.UTF_8));
            long val = 0;
            for (int i = 0; i < 8; i++) {
                val = (val << 8) | (digest[i] & 0xFF);
            }
            return val;
        } catch (Exception e) {
            return str.hashCode();
        }
    }
}
