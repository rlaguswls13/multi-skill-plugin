# `skill-http-link-header-rfc8288-parser`

Zero-dependency Java 21 **RFC 8288 Web Linking (`Link`)** HTTP header parser, builder, **RFC 8187 (`title*`)** extended parameter codec, and REST `PaginationLinks` helper.

## Highlights
- **RFC 8288 §3 Compliant Scanner**: Handles multiple comma-separated link entries, quoted strings with `\"`/`\\` escapes, commas/semicolons inside quotes, unquoted tokens, valueless flag parameters, and multi-header lists.
- **Standard & Extension Parameters**: Built-in accessors for `rel`, `anchor`, `rev`, `hreflang`, `media`, `title`, `title*`, `type`, plus arbitrary extension parameters.
- **RFC 8187 `ExtValue`**: Encodes and decodes `charset'language'value-chars` (`title*`), preferring decoded `title*` over ASCII `title` per RFC 8288 §3.4.1.
- **`PaginationLinks` Helper**: Extracts `first`, `prev` (including legacy `previous`), `next`, and `last` pagination links in one call.

## Quick Start

```java
import com.msp.linkheader.LinkEntry;
import com.msp.linkheader.LinkHeaderParser;
import com.msp.linkheader.PaginationLinks;

import java.util.List;

public class Example {
    public static void main(String[] args) {
        String header = "<https://api.example.com/items?page=2>; rel=\"next\"; title*=UTF-8'en'Next%20Page, "
                      + "<https://api.example.com/items?page=10>; rel=\"last\"";

        // 1. Parse all Link entries
        List<LinkEntry> links = LinkHeaderParser.parse(header);
        System.out.println("First target: " + links.get(0).targetUri());
        System.out.println("Title: " + links.get(0).title().orElse(""));

        // 2. Extract REST pagination links
        PaginationLinks pages = PaginationLinks.from(links);
        System.out.println("Next URI: " + pages.nextUri().orElse("none"));
        System.out.println("Last URI: " + pages.lastUri().orElse("none"));

        // 3. Build & format Link header
        String out = LinkHeaderParser.format(List.of(
                LinkEntry.builder("https://api.example.com/items?page=3")
                        .rel("next")
                        .title("Page 3")
                        .type("application/json")
                        .build()
        ));
        System.out.println("Header: " + out);
    }
}
```
