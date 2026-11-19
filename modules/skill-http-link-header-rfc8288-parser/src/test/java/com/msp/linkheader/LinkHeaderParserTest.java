package com.msp.linkheader;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LinkHeaderParserTest {

    @Test
    void parseMultipleEntriesWithStandardParameters() {
        String header = """
                <https://api.example.com/items?page=2&sort=asc>; rel="next"; title="Next Page"; type="application/json", \
                <https://api.example.com/items?page=10>; rel=last; hreflang=en; hreflang=ko; media="screen"
                """.trim();

        List<LinkEntry> entries = LinkHeaderParser.parse(header);
        assertEquals(2, entries.size());

        LinkEntry first = entries.get(0);
        assertEquals("https://api.example.com/items?page=2&sort=asc", first.targetUri());
        assertEquals(List.of("next"), first.rels());
        assertTrue(first.hasRel("NEXT"));
        assertEquals("Next Page", first.title().orElseThrow());
        assertEquals("application/json", first.type().orElseThrow());

        LinkEntry second = entries.get(1);
        assertEquals("https://api.example.com/items?page=10", second.targetUri());
        assertEquals(List.of("last"), second.rels());
        assertEquals(List.of("en", "ko"), second.hreflang());
        assertEquals("screen", second.media().orElseThrow());
    }

    @Test
    void parseQuotedStringsWithCommasSemicolonsAndEscapes() {
        String header = "<https://example.com/a,b;c>; rel=\"next preload\"; title=\"Page 2, \\\"Quoted\\\"; done\"; anchor=\"#sec-1\"; rev=\"prev\"";
        List<LinkEntry> entries = LinkHeaderParser.parse(header);
        assertEquals(1, entries.size());

        LinkEntry entry = entries.get(0);
        assertEquals("https://example.com/a,b;c", entry.targetUri());
        assertEquals(List.of("next", "preload"), entry.rels());
        assertTrue(entry.hasRel("next"));
        assertTrue(entry.hasRel("preload"));
        assertEquals("Page 2, \"Quoted\"; done", entry.title().orElseThrow());
        assertEquals("#sec-1", entry.anchor().orElseThrow());
        assertEquals("prev", entry.rev().orElseThrow());
    }

    @Test
    void parseRfc8187TitleStarTakesPrecedenceOverTitle() {
        String header = "</chapter2>; rel=\"next\"; title=\"Fallback\"; title*=UTF-8'ko'%EB%8B%A4%EC%9D%8C%20%ED%8E%98%EC%9D%B4%EC%A7%80";
        List<LinkEntry> entries = LinkHeaderParser.parse(header);
        assertEquals(1, entries.size());

        LinkEntry entry = entries.get(0);
        assertEquals("Fallback", entry.rawTitle().orElseThrow());
        ExtValue ext = entry.titleStar().orElseThrow();
        assertEquals("UTF-8", ext.charset());
        assertEquals("ko", ext.language());
        assertEquals("다음 페이지", ext.value());
        assertEquals("다음 페이지", entry.title().orElseThrow());
    }

    @Test
    void parseCustomExtensionParametersAndValuelessFlag() {
        String header = "</assets/app.css>; rel=preload; as=style; crossorigin; x-priority=high";
        List<LinkEntry> entries = LinkHeaderParser.parse(header);
        assertEquals(1, entries.size());

        LinkEntry entry = entries.get(0);
        assertEquals("style", entry.parameter("as").orElseThrow());
        assertEquals("", entry.parameter("crossorigin").orElseThrow());
        assertEquals("high", entry.parameter("X-Priority").orElseThrow());
    }

    @Test
    void builderAndRoundTripFormat() {
        LinkEntry entry1 = LinkEntry.builder("https://api.example.com/items?page=2")
                .rel("next")
                .title("Next \"Page\"")
                .titleStar("ko", "다음 페이지")
                .type("application/json")
                .hreflang("ko")
                .param("x-trace", "abc")
                .build();

        LinkEntry entry2 = LinkEntry.builder("https://api.example.com/items?page=9")
                .rel("last", "https://example.com/rel/Final")
                .build();

        String formatted = LinkHeaderParser.format(List.of(entry1, entry2));
        List<LinkEntry> reparsed = LinkHeaderParser.parse(formatted);

        assertEquals(2, reparsed.size());
        assertEquals(entry1.targetUri(), reparsed.get(0).targetUri());
        assertEquals("Next \"Page\"", reparsed.get(0).rawTitle().orElseThrow());
        assertEquals("다음 페이지", reparsed.get(0).title().orElseThrow());
        assertEquals("application/json", reparsed.get(0).type().orElseThrow());
        assertEquals(List.of("last", "https://example.com/rel/Final"), reparsed.get(1).rels());
    }

    @Test
    void paginationLinksHelperExtractsAllRelationsAndPreviousAlias() {
        String header = """
                <https://api.example.com/v1/users?page=1>; rel="first",
                <https://api.example.com/v1/users?page=2>; rel="previous",
                <https://api.example.com/v1/users?page=4>; rel="next",
                <https://api.example.com/v1/users?page=20>; rel="last"
                """;

        PaginationLinks pages = PaginationLinks.parse(header);
        assertEquals("https://api.example.com/v1/users?page=1", pages.firstUri().orElseThrow());
        assertEquals("https://api.example.com/v1/users?page=2", pages.prevUri().orElseThrow());
        assertEquals("https://api.example.com/v1/users?page=4", pages.nextUri().orElseThrow());
        assertEquals("https://api.example.com/v1/users?page=20", pages.lastUri().orElseThrow());
        assertTrue(pages.hasNext());
        assertTrue(pages.hasPrev());

        PaginationLinks rebuilt = PaginationLinks.ofUris("/p=1", null, "/p=2", "/p=5");
        assertFalse(rebuilt.hasPrev());
        assertTrue(rebuilt.hasNext());
        assertEquals("</p=1>; rel=\"first\", </p=2>; rel=\"next\", </p=5>; rel=\"last\"", rebuilt.toHeaderValue());
    }

    @Test
    void parseMultipleHeadersAndEmptyInputs() {
        assertTrue(LinkHeaderParser.parse(null).isEmpty());
        assertTrue(LinkHeaderParser.parse("   ").isEmpty());
        assertTrue(LinkHeaderParser.parse(" , , ").isEmpty());

        List<LinkEntry> combined = LinkHeaderParser.parseAll(List.of(
                "</p=1>; rel=\"first\"",
                "</p=2>; rel=\"next\""
        ));
        assertEquals(2, combined.size());
        assertEquals("/p=1", combined.get(0).targetUri());
        assertEquals("/p=2", combined.get(1).targetUri());
    }

    @Test
    void malformedHeadersThrowLinkHeaderException() {
        assertThrows(LinkHeaderException.class, () -> LinkHeaderParser.parse("https://no-brackets.example.com"));
        assertThrows(LinkHeaderException.class, () -> LinkHeaderParser.parse("<https://unterminated.example.com"));
        assertThrows(LinkHeaderException.class, () -> LinkHeaderParser.parse("</a.html>; title=\"unterminated"));
        assertThrows(LinkHeaderException.class, () -> LinkHeaderParser.parse("</a.html> rel=next"));
        assertThrows(LinkHeaderException.class, () -> ExtValue.parse("UTF-8-no-quotes"));
        assertThrows(LinkHeaderException.class, () -> ExtValue.parse("UTF-8'en'%ZZ"));
    }
}
