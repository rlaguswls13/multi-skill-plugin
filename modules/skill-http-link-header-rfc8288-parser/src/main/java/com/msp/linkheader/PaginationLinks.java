package com.msp.linkheader;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Convenience helper extracting standard REST pagination links ({@code first}, {@code prev}, {@code next}, {@code last})
 * from RFC 8288 {@code Link} header entries.
 */
public record PaginationLinks(
        Optional<LinkEntry> first,
        Optional<LinkEntry> prev,
        Optional<LinkEntry> next,
        Optional<LinkEntry> last
) {

    public PaginationLinks {
        Objects.requireNonNull(first, "first must not be null");
        Objects.requireNonNull(prev, "prev must not be null");
        Objects.requireNonNull(next, "next must not be null");
        Objects.requireNonNull(last, "last must not be null");
    }

    /**
     * Parses a raw {@code Link} header value and extracts pagination relations.
     */
    public static PaginationLinks parse(String headerValue) {
        return from(LinkHeaderParser.parse(headerValue));
    }

    /**
     * Extracts pagination relations from parsed {@link LinkEntry} instances.
     * Supports both {@code prev} and legacy {@code previous} relation names.
     */
    public static PaginationLinks from(List<LinkEntry> entries) {
        Objects.requireNonNull(entries, "entries must not be null");
        LinkEntry first = null;
        LinkEntry prev = null;
        LinkEntry previousFallback = null;
        LinkEntry next = null;
        LinkEntry last = null;

        for (LinkEntry entry : entries) {
            if (first == null && entry.hasRel("first")) {
                first = entry;
            }
            if (prev == null && entry.hasRel("prev")) {
                prev = entry;
            } else if (previousFallback == null && entry.hasRel("previous")) {
                previousFallback = entry;
            }
            if (next == null && entry.hasRel("next")) {
                next = entry;
            }
            if (last == null && entry.hasRel("last")) {
                last = entry;
            }
        }

        return new PaginationLinks(
                Optional.ofNullable(first),
                Optional.ofNullable(prev != null ? prev : previousFallback),
                Optional.ofNullable(next),
                Optional.ofNullable(last)
        );
    }

    /**
     * Creates a {@link PaginationLinks} from raw URIs (any {@code null} URI is omitted).
     */
    public static PaginationLinks ofUris(String firstUri, String prevUri, String nextUri, String lastUri) {
        return new PaginationLinks(
                Optional.ofNullable(firstUri).map(u -> LinkEntry.builder(u).rel("first").build()),
                Optional.ofNullable(prevUri).map(u -> LinkEntry.builder(u).rel("prev").build()),
                Optional.ofNullable(nextUri).map(u -> LinkEntry.builder(u).rel("next").build()),
                Optional.ofNullable(lastUri).map(u -> LinkEntry.builder(u).rel("last").build())
        );
    }

    public Optional<String> firstUri() {
        return first.map(LinkEntry::targetUri);
    }

    public Optional<String> prevUri() {
        return prev.map(LinkEntry::targetUri);
    }

    public Optional<String> nextUri() {
        return next.map(LinkEntry::targetUri);
    }

    public Optional<String> lastUri() {
        return last.map(LinkEntry::targetUri);
    }

    public boolean hasNext() {
        return next.isPresent();
    }

    public boolean hasPrev() {
        return prev.isPresent();
    }

    /**
     * Formats all present pagination links into an RFC 8288 {@code Link} header value.
     */
    public String toHeaderValue() {
        List<LinkEntry> ordered = new ArrayList<>(4);
        first.ifPresent(ordered::add);
        prev.ifPresent(ordered::add);
        next.ifPresent(ordered::add);
        last.ifPresent(ordered::add);
        return LinkHeaderParser.format(ordered);
    }
}
