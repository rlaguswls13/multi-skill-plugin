package com.msp.cookiejar;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RFC 6265 CookieJar Tests")
class CookieJarTest {

    private CookieJar cookieJar;

    @BeforeEach
    void setUp() {
        cookieJar = new CookieJar();
    }

    @Test
    @DisplayName("Parses basic Set-Cookie header and stores session cookie")
    void testBasicCookieParsing() {
        URI uri = URI.create("https://example.com/api/users");
        cookieJar.add(uri, "sessionId=abc12345; Secure; HttpOnly");

        assertEquals(1, cookieJar.size());
        List<HttpCookieRecord> cookies = cookieJar.getCookies(uri);
        assertEquals(1, cookies.size());

        HttpCookieRecord cookie = cookies.get(0);
        assertEquals("sessionId", cookie.name());
        assertEquals("abc12345", cookie.value());
        assertEquals("example.com", cookie.domain());
        assertEquals("/api", cookie.path()); // Default path for /api/users
        assertTrue(cookie.secure());
        assertTrue(cookie.httpOnly());
        assertNull(cookie.expiresAt());
        assertTrue(cookie.hostOnly());
        assertEquals(SameSite.LAX, cookie.sameSite());
    }

    @Test
    @DisplayName("Domain matching: exact and subdomain matching per RFC 6265")
    void testDomainMatching() {
        URI uri = URI.create("https://sub.example.com/app");
        // Non-host-only cookie with explicit Domain=.example.com
        cookieJar.add(uri, "crossDomain=val; Domain=example.com; Path=/");

        // Host-only cookie
        cookieJar.add(uri, "hostOnlyCookie=val; Path=/");

        // Another subdomain request
        URI anotherSubUri = URI.create("https://api.example.com/app");
        List<HttpCookieRecord> anotherSubCookies = cookieJar.getCookies(anotherSubUri);
        assertEquals(1, anotherSubCookies.size());
        assertEquals("crossDomain", anotherSubCookies.get(0).name());

        // Exact match request
        URI subUri = URI.create("https://sub.example.com/app");
        List<HttpCookieRecord> subCookies = cookieJar.getCookies(subUri);
        assertEquals(2, subCookies.size());

        // IP address should not match subdomain domain cookie
        HttpCookieRecord ipCookie = new HttpCookieRecord(
                "ipCookie", "val", "192.168.1.1", "/", null, false, false, SameSite.LAX, Instant.now(), false
        );
        assertTrue(ipCookie.matchesDomain("192.168.1.1"));
        assertFalse(ipCookie.matchesDomain("sub.192.168.1.1"));
    }

    @Test
    @DisplayName("Path matching: exact, child, and non-prefix paths")
    void testPathMatching() {
        HttpCookieRecord cookie = new HttpCookieRecord(
                "test", "val", "example.com", "/docs", null, false, false, SameSite.LAX, Instant.now()
        );

        assertTrue(cookie.matchesPath("/docs"));
        assertTrue(cookie.matchesPath("/docs/"));
        assertTrue(cookie.matchesPath("/docs/subpage"));
        assertFalse(cookie.matchesPath("/docsexample")); // Not separated by '/'
        assertFalse(cookie.matchesPath("/"));
        assertFalse(cookie.matchesPath("/other"));
    }

    @Test
    @DisplayName("Expiration handling: Max-Age and Expires headers")
    void testExpiration() throws InterruptedException {
        URI uri = URI.create("https://example.com/test");

        // 1-second max age
        cookieJar.add(uri, "shortLived=foo; Max-Age=1; Path=/test");
        assertEquals(1, cookieJar.size());
        assertFalse(cookieJar.getCookies(uri).isEmpty());

        // Wait for it to expire
        Thread.sleep(1100);
        cookieJar.purgeExpired();
        assertEquals(0, cookieJar.size());
        assertTrue(cookieJar.getCookies(uri).isEmpty());

        // Max-Age=0 expires immediately and deletes cookie
        cookieJar.add(uri, "toDelete=bar; Max-Age=3600; Path=/test");
        assertEquals(1, cookieJar.size());
        cookieJar.add(uri, "toDelete=bar; Max-Age=0; Path=/test");
        assertEquals(0, cookieJar.size());
    }

    @Test
    @DisplayName("Secure flag prevents transmission over unencrypted HTTP")
    void testSecureFlag() {
        URI httpsUri = URI.create("https://example.com/secure");
        cookieJar.add(httpsUri, "secretToken=xyz; Secure; Path=/");

        URI httpUri = URI.create("http://example.com/secure");
        assertTrue(cookieJar.getCookies(httpUri).isEmpty());
        assertEquals("", cookieJar.getCookieHeader(httpUri));

        assertFalse(cookieJar.getCookies(httpsUri).isEmpty());
        assertEquals("secretToken=xyz", cookieJar.getCookieHeader(httpsUri));
    }

    @Test
    @DisplayName("SameSite parsing: Strict, Lax, None")
    void testSameSiteParsing() {
        URI uri = URI.create("https://example.com/");
        cookieJar.add(uri, "s1=val; SameSite=Strict; Path=/");
        cookieJar.add(uri, "s2=val; SameSite=Lax; Path=/");
        cookieJar.add(uri, "s3=val; SameSite=None; Path=/");

        List<HttpCookieRecord> cookies = cookieJar.getCookies(uri);
        assertEquals(3, cookies.size());

        assertTrue(cookies.stream().anyMatch(c -> c.name().equals("s1") && c.sameSite() == SameSite.STRICT));
        assertTrue(cookies.stream().anyMatch(c -> c.name().equals("s2") && c.sameSite() == SameSite.LAX));
        assertTrue(cookies.stream().anyMatch(c -> c.name().equals("s3") && c.sameSite() == SameSite.NONE));
    }

    @Test
    @DisplayName("Cookie header generation sorts longer paths first per RFC 6265 section 5.4")
    void testPathSortOrderInHeader() {
        URI uri = URI.create("https://example.com/a/b/c");
        cookieJar.add(uri, "root=1; Path=/");
        cookieJar.add(uri, "mid=2; Path=/a/b");
        cookieJar.add(uri, "deep=3; Path=/a/b/c");

        String header = cookieJar.getCookieHeader(uri);
        assertEquals("deep=3; mid=2; root=1", header);
    }

    @Test
    @DisplayName("Clear removes all cookies")
    void testClear() {
        URI uri = URI.create("https://example.com/");
        cookieJar.add(uri, "c1=1; Path=/");
        cookieJar.add(uri, "c2=2; Path=/");
        assertEquals(2, cookieJar.size());

        cookieJar.clear();
        assertEquals(0, cookieJar.size());
        assertEquals("", cookieJar.getCookieHeader(uri));
    }

    @Test
    @DisplayName("Concurrent multithreaded cookie operations")
    void testConcurrentOperations() throws InterruptedException {
        int threadCount = 10;
        int operationsPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int threadId = i;
            executor.submit(() -> {
                try {
                    URI uri = URI.create("https://example" + (threadId % 3) + ".com/api");
                    for (int j = 0; j < operationsPerThread; j++) {
                        cookieJar.add(uri, "cookie_" + threadId + "_" + j + "=val; Path=/; Max-Age=3600");
                        cookieJar.getCookies(uri);
                        cookieJar.getCookieHeader(uri);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS));
        executor.shutdown();
        assertTrue(cookieJar.size() > 0);
    }
}
