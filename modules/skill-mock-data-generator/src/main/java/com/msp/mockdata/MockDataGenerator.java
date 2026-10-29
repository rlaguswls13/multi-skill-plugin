package com.msp.mockdata;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * High-performance, zero-dependency mock data generator for realistic Korean and English
 * identities, addresses, phone numbers, emails, dates, prices, and deterministic testing.
 */
public final class MockDataGenerator {

    private static final String[] KOREAN_LAST_NAMES = {
            "김", "이", "박", "최", "정", "강", "조", "윤", "장", "임",
            "한", "오", "서", "신", "권", "황", "안", "송", "류", "홍",
            "고", "문", "양", "손", "배", "백", "허", "유", "남", "심"
    };

    private static final String[] KOREAN_FIRST_NAMES = {
            "민준", "서연", "도윤", "지우", "하준", "서윤", "주원", "하은", "예준", "지아",
            "시우", "서현", "지호", "수아", "준우", "하린", "유준", "민서", "건우", "채원",
            "예은", "지후", "다은", "우진", "윤서", "선우", "서진", "연우", "시은", "정우"
    };

    private static final String[] ENGLISH_FIRST_NAMES = {
            "James", "Mary", "John", "Patricia", "Robert", "Jennifer", "Michael", "Linda",
            "William", "Elizabeth", "David", "Barbara", "Richard", "Susan", "Joseph", "Jessica",
            "Thomas", "Sarah", "Charles", "Karen", "Daniel", "Lisa", "Matthew", "Nancy",
            "Anthony", "Betty", "Mark", "Margaret", "Donald", "Sandra", "Steven", "Ashley"
    };

    private static final String[] ENGLISH_LAST_NAMES = {
            "Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller", "Davis",
            "Rodriguez", "Martinez", "Hernandez", "Lopez", "Gonzalez", "Wilson", "Anderson", "Thomas",
            "Taylor", "Moore", "Jackson", "Martin", "Lee", "Perez", "Thompson", "White", "Harris"
    };

    private static final String[] EMAIL_DOMAINS = {
            "gmail.com", "naver.com", "kakao.com", "daum.net", "outlook.com", "icloud.com"
    };

    private static final String[] KOREAN_CITIES = {
            "서울특별시", "부산광역시", "대구광역시", "인천광역시", "광주광역시",
            "대전광역시", "울산광역시", "세종특별자치시", "경기도", "강원특별자치도"
    };

    private static final String[] KOREAN_DISTRICTS = {
            "강남구", "서초구", "송파구", "종로구", "마포구", "영등포구",
            "성남시 분당구", "수원시 영통구", "용인시 수지구", "고양시 일산동구"
    };

    private static final String[] KOREAN_ROADS = {
            "테헤란로", "강남대로", "서초대로", "판교역로", "세종대로", "대학로",
            "올림픽로", "도산대로", "디지털로", "월드컵북로", "양재천로", "포은대로"
    };

    private static final String[] KOREAN_COMPANY_NAMES = {
            "넥스트이노베이션", "카카오랩스", "네이버클라우드", "현대모빌리티", "삼성솔루션",
            "대한테크놀로지", "한화시스템즈", "토스인사이트", "라인플랫폼", "쿠팡로지스틱스",
            "엔씨소프트웨어", "우아한디지털", "당근소프트", "야놀자트래블", "크래프톤랩"
    };

    private static final String[] ENGLISH_COMPANY_NAMES = {
            "Acme Corp", "Apex Solutions Inc", "Global Tech LLC", "Stellar Innovations",
            "Cyberdyne Systems", "Wayne Enterprises", "Stark Industries", "Horizon Dynamics",
            "Omni Consumer Products", "Initech Global", "Umbrella BioTech", "Massive Dynamic"
    };

    private static final String ALPHANUMERIC_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

    private final Random random;

    /**
     * Creates a new MockDataGenerator with non-deterministic random seed.
     */
    public MockDataGenerator() {
        this(new Random());
    }

    /**
     * Creates a MockDataGenerator with a fixed seed for reproducible deterministic tests.
     */
    public MockDataGenerator(long seed) {
        this(new Random(seed));
    }

    /**
     * Internal constructor with custom Random instance.
     */
    public MockDataGenerator(Random random) {
        this.random = Objects.requireNonNull(random, "random must not be null");
    }

    /**
     * Static factory for deterministic mock generation with a fixed seed.
     */
    public static MockDataGenerator deterministic(long seed) {
        return new MockDataGenerator(seed);
    }

    // --- Names ---

    public String koreanLastName() {
        return pickOne(KOREAN_LAST_NAMES);
    }

    public String koreanFirstName() {
        return pickOne(KOREAN_FIRST_NAMES);
    }

    public String koreanName() {
        return koreanLastName() + koreanFirstName();
    }

    public String englishFirstName() {
        return pickOne(ENGLISH_FIRST_NAMES);
    }

    public String englishLastName() {
        return pickOne(ENGLISH_LAST_NAMES);
    }

    public String englishName() {
        return englishFirstName() + " " + englishLastName();
    }

    // --- Phone Numbers ---

    /**
     * Generates a Korean mobile phone number formatted as {@code 010-XXXX-XXXX}.
     */
    public String koreanMobilePhoneNumber() {
        int mid = 2000 + random.nextInt(8000);
        int last = random.nextInt(10000);
        return String.format(Locale.ROOT, "010-%04d-%04d", mid, last);
    }

    /**
     * Generates a Korean landline phone number formatted as {@code 02-XXXX-XXXX} or {@code 031-XXX-XXXX}.
     */
    public String koreanLandlinePhoneNumber() {
        String[] prefixes = {"02", "031", "032", "051", "053", "042"};
        String prefix = pickOne(prefixes);
        int mid = 200 + random.nextInt(8000);
        int last = random.nextInt(10000);
        return String.format(Locale.ROOT, "%s-%d-%04d", prefix, mid, last);
    }

    public String phoneNumber() {
        return koreanMobilePhoneNumber();
    }

    // --- Email ---

    public String email() {
        return email(pickOne(EMAIL_DOMAINS));
    }

    public String email(String domain) {
        Objects.requireNonNull(domain, "domain must not be null");
        String first = englishFirstName().toLowerCase(Locale.ROOT);
        String last = englishLastName().toLowerCase(Locale.ROOT);
        int num = 10 + random.nextInt(90);
        return first + "." + last + num + "@" + domain;
    }

    // --- Addresses ---

    public String koreanCity() {
        return pickOne(KOREAN_CITIES);
    }

    public String koreanDistrict() {
        return pickOne(KOREAN_DISTRICTS);
    }

    public String koreanRoad() {
        return pickOne(KOREAN_ROADS);
    }

    /**
     * Generates a 5-digit Korean postal code (01000 ~ 63999).
     */
    public String koreanPostalCode() {
        int code = 1000 + random.nextInt(63000);
        return String.format(Locale.ROOT, "%05d", code);
    }

    /**
     * Generates a full Korean street address.
     */
    public String koreanAddress() {
        String city = koreanCity();
        String district = koreanDistrict();
        String road = koreanRoad();
        int buildingNo = 1 + random.nextInt(500);
        int roomNo = 101 + random.nextInt(1500);
        return String.format(Locale.ROOT, "%s %s %s %d, %d호", city, district, road, buildingNo, roomNo);
    }

    // --- Companies ---

    public String koreanCompanyName() {
        String name = pickOne(KOREAN_COMPANY_NAMES);
        return random.nextBoolean() ? "(주)" + name : name + " 주식회사";
    }

    public String englishCompanyName() {
        return pickOne(ENGLISH_COMPANY_NAMES);
    }

    // --- Dates & Time ---

    public LocalDate date(LocalDate from, LocalDate to) {
        Objects.requireNonNull(from, "from date must not be null");
        Objects.requireNonNull(to, "to date must not be null");
        long days = ChronoUnit.DAYS.between(from, to);
        if (days < 0) {
            throw new IllegalArgumentException("from date must be before or equal to to date");
        }
        long offset = days == 0 ? 0 : (Math.abs(random.nextLong()) % (days + 1));
        return from.plusDays(offset);
    }

    public LocalDate recentDate(int daysBack) {
        if (daysBack < 0) {
            throw new IllegalArgumentException("daysBack must be non-negative");
        }
        LocalDate today = LocalDate.now();
        return date(today.minusDays(daysBack), today);
    }

    public LocalDateTime dateTime(LocalDateTime from, LocalDateTime to) {
        Objects.requireNonNull(from, "from dateTime must not be null");
        Objects.requireNonNull(to, "to dateTime must not be null");
        long seconds = ChronoUnit.SECONDS.between(from, to);
        if (seconds < 0) {
            throw new IllegalArgumentException("from dateTime must be before or equal to to dateTime");
        }
        long offset = seconds == 0 ? 0 : (Math.abs(random.nextLong()) % (seconds + 1));
        return from.plusSeconds(offset);
    }

    // --- Numbers, Money, Tokens ---

    public long price(long min, long max, long step) {
        if (min > max) {
            throw new IllegalArgumentException("min price must be <= max price");
        }
        if (step <= 0) {
            throw new IllegalArgumentException("step must be positive");
        }
        long stepsCount = (max - min) / step;
        long chosenStep = stepsCount <= 0 ? 0 : (Math.abs(random.nextLong()) % (stepsCount + 1));
        return min + chosenStep * step;
    }

    public UUID uuid() {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        // Set version 4 and variant bits for RFC 4122 compliance
        bytes[6] &= 0x0f;
        bytes[6] |= 0x40;
        bytes[8] &= 0x3f;
        bytes[8] |= (byte) 0x80;
        long msb = 0;
        long lsb = 0;
        for (int i = 0; i < 8; i++) msb = (msb << 8) | (bytes[i] & 0xff);
        for (int i = 8; i < 16; i++) lsb = (lsb << 8) | (bytes[i] & 0xff);
        return new UUID(msb, lsb);
    }

    public String alphanumeric(int length) {
        if (length < 0) {
            throw new IllegalArgumentException("length must be non-negative");
        }
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHANUMERIC_CHARS.charAt(random.nextInt(ALPHANUMERIC_CHARS.length())));
        }
        return sb.toString();
    }

    public String numeric(int length) {
        if (length < 0) {
            throw new IllegalArgumentException("length must be non-negative");
        }
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }

    public boolean booleanValue() {
        return random.nextBoolean();
    }

    // --- Collections & Picking ---

    @SafeVarargs
    public final <T> T pickOne(T... items) {
        if (items == null || items.length == 0) {
            throw new IllegalArgumentException("items array must not be null or empty");
        }
        return items[random.nextInt(items.length)];
    }

    public <T> T pickOne(List<T> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("items list must not be null or empty");
        }
        return items.get(random.nextInt(items.size()));
    }

    public <T> List<T> pickMultiple(List<T> items, int count) {
        if (items == null) {
            throw new IllegalArgumentException("items list must not be null");
        }
        if (count < 0 || count > items.size()) {
            throw new IllegalArgumentException("count must be between 0 and items.size()");
        }
        List<T> copy = new ArrayList<>(items);
        Collections.shuffle(copy, random);
        return copy.subList(0, count);
    }
}
