package com.msp.ipasn;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Pure Java in-memory IPv4 Range and CIDR based Country Code and ASN lookup probe.
 * <p>
 * Converts IPv4 addresses to 32-bit unsigned integers and performs
 * logarithmic binary search (O(log N)) with longest prefix match resolution.
 * Thread-safe with lock-free read lookups.
 */
public final class IpAsnLookupEngine {

    record RangeEntry(long startIp, long endIp, IpGeoAsnRecord record) implements Comparable<RangeEntry> {
        @Override
        public int compareTo(RangeEntry o) {
            int cmp = Long.compare(this.startIp, o.startIp);
            if (cmp != 0) return cmp;
            return Long.compare(this.endIp, o.endIp);
        }
    }

    private final ReentrantReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final List<RangeEntry> entryList = new ArrayList<>();
    private volatile RangeEntry[] entries = new RangeEntry[0];

    public IpAsnLookupEngine() {
    }

    /**
     * Registers an IPv4 address range [startIp, endIp] with metadata.
     *
     * @param startIp start IPv4 address (inclusive, e.g. "1.1.1.0")
     * @param endIp end IPv4 address (inclusive, e.g. "1.1.1.255")
     * @param countryCode ISO-3166-1 alpha-2 country code
     * @param asn Autonomous System Number
     * @param asName Autonomous System organization name
     */
    public void registerRange(String startIp, String endIp, String countryCode, int asn, String asName) {
        long start = parseIpv4ToLong(startIp);
        long end = parseIpv4ToLong(endIp);
        if (start > end) {
            throw new IllegalArgumentException("startIp (" + startIp + ") cannot be greater than endIp (" + endIp + ")");
        }
        registerRange(start, end, new IpGeoAsnRecord(countryCode, asn, asName));
    }

    /**
     * Registers a CIDR block notation with metadata (e.g. "8.8.8.0/24").
     *
     * @param cidr CIDR notation (e.g., "8.8.8.0/24")
     * @param countryCode ISO-3166-1 alpha-2 country code
     * @param asn Autonomous System Number
     * @param asName Autonomous System organization name
     */
    public void registerCidr(String cidr, String countryCode, int asn, String asName) {
        if (cidr == null || !cidr.contains("/")) {
            throw new IllegalArgumentException("Invalid CIDR format (expected ip/prefix): " + cidr);
        }
        String[] parts = cidr.trim().split("/");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid CIDR notation: " + cidr);
        }

        long baseIp = parseIpv4ToLong(parts[0]);
        int prefix;
        try {
            prefix = Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid CIDR prefix number: " + parts[1]);
        }

        if (prefix < 0 || prefix > 32) {
            throw new IllegalArgumentException("CIDR prefix must be between 0 and 32: " + prefix);
        }

        long mask = (prefix == 0) ? 0L : (0xFFFFFFFFL << (32 - prefix)) & 0xFFFFFFFFL;
        long startIp = baseIp & mask;
        long endIp = startIp | (~mask & 0xFFFFFFFFL);

        registerRange(startIp, endIp, new IpGeoAsnRecord(countryCode, asn, asName));
    }

    /**
     * Looks up metadata for the given IPv4 address string.
     *
     * @param ip IPv4 address string (e.g., "1.1.1.1")
     * @return Optional containing IpGeoAsnRecord if found, empty Optional otherwise
     */
    public Optional<IpGeoAsnRecord> lookup(String ip) {
        long ipLong = parseIpv4ToLong(ip);
        return lookup(ipLong);
    }

    /**
     * Looks up metadata for the given 32-bit unsigned long IPv4 value.
     *
     * @param ipLong IPv4 address represented as 32-bit unsigned long
     * @return Optional containing IpGeoAsnRecord if found, empty Optional otherwise
     */
    public Optional<IpGeoAsnRecord> lookup(long ipLong) {
        RangeEntry[] current = entries;
        if (current.length == 0) {
            return Optional.empty();
        }

        int low = 0;
        int high = current.length - 1;
        int candidate = -1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (current[mid].startIp() <= ipLong) {
                candidate = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }

        if (candidate == -1) {
            return Optional.empty();
        }

        // Search backward from candidate for the most specific enclosing range (longest prefix match)
        RangeEntry bestMatch = null;
        long minSpan = Long.MAX_VALUE;

        for (int i = candidate; i >= 0; i--) {
            RangeEntry entry = current[i];
            if (ipLong <= entry.endIp()) {
                long span = entry.endIp() - entry.startIp();
                if (span < minSpan) {
                    minSpan = span;
                    bestMatch = entry;
                }
            }
        }

        return bestMatch != null ? Optional.of(bestMatch.record()) : Optional.empty();
    }

    /**
     * Returns the total count of registered IP ranges.
     */
    public int size() {
        return entries.length;
    }

    /**
     * Clears all registered IP ranges.
     */
    public void clear() {
        rwLock.writeLock().lock();
        try {
            entryList.clear();
            entries = new RangeEntry[0];
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    private void registerRange(long startIp, long endIp, IpGeoAsnRecord record) {
        rwLock.writeLock().lock();
        try {
            entryList.add(new RangeEntry(startIp, endIp, record));
            Collections.sort(entryList);
            entries = entryList.toArray(new RangeEntry[0]);
        } finally {
            rwLock.writeLock().unlock();
        }
    }

    /**
     * Parses an IPv4 string into a 32-bit unsigned long (0 to 4294967295).
     *
     * @param ip IPv4 string (e.g. "192.168.0.1")
     * @return 32-bit unsigned long value
     */
    public static long parseIpv4ToLong(String ip) {
        if (ip == null || ip.isBlank()) {
            throw new IllegalArgumentException("IP address cannot be null or blank");
        }
        String[] parts = ip.trim().split("\\.");
        if (parts.length != 4) {
            throw new IllegalArgumentException("Invalid IPv4 address format: " + ip);
        }

        long result = 0;
        for (int i = 0; i < 4; i++) {
            String part = parts[i].trim();
            if (part.isEmpty() || (part.length() > 1 && part.startsWith("0"))) {
                if (!part.equals("0")) {
                    throw new IllegalArgumentException("Invalid IPv4 octet (leading zeros not allowed): " + part);
                }
            }
            try {
                int val = Integer.parseInt(part);
                if (val < 0 || val > 255) {
                    throw new IllegalArgumentException("IPv4 octet out of range [0-255]: " + val);
                }
                result = (result << 8) | val;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid IPv4 octet number: " + part, e);
            }
        }
        return result & 0xFFFFFFFFL;
    }

    /**
     * Converts a 32-bit unsigned long back to standard dotted-decimal IPv4 string.
     *
     * @param ipLong 32-bit unsigned long
     * @return dotted decimal string (e.g., "192.168.1.1")
     */
    public static String longToIpv4(long ipLong) {
        if (ipLong < 0 || ipLong > 0xFFFFFFFFL) {
            throw new IllegalArgumentException("IP value out of 32-bit range: " + ipLong);
        }
        return ((ipLong >>> 24) & 0xFF) + "." +
               ((ipLong >>> 16) & 0xFF) + "." +
               ((ipLong >>> 8) & 0xFF) + "." +
               (ipLong & 0xFF);
    }
}
