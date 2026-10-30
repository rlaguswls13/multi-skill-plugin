package com.msp.toc;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TocExtractorTest {

    @Test
    @DisplayName("Extract headings and generate slug anchors properly")
    void testExtractHeadings() {
        String md = """
                # Introduction
                Welcome to the documentation.
                
                ## Getting Started
                How to get started.
                
                ### Installation
                Steps to install.
                
                ```java
                // # This is a comment, not a heading
                public class Main {}
                ```
                
                ## Getting Started
                Another section with same name.
                
                # Conclusion
                """;

        List<TocItem> items = TocExtractor.extract(md);
        assertEquals(5, items.size());

        assertEquals(new TocItem(1, "Introduction", "introduction"), items.get(0));
        assertEquals(new TocItem(2, "Getting Started", "getting-started"), items.get(1));
        assertEquals(new TocItem(3, "Installation", "installation"), items.get(2));
        assertEquals(new TocItem(2, "Getting Started", "getting-started-1"), items.get(3)); // Duplicate deduplicated
        assertEquals(new TocItem(1, "Conclusion", "conclusion"), items.get(4));
    }

    @Test
    @DisplayName("Generate TOC markdown with correct indentation")
    void testGenerateTocMarkdown() {
        String md = """
                # Title
                ## Subtitle 1
                ### Deep Subtitle
                ## Subtitle 2
                """;

        String toc = TocExtractor.generateTocMarkdown(md);
        String expected = """
                - [Title](#title)
                  - [Subtitle 1](#subtitle-1)
                    - [Deep Subtitle](#deep-subtitle)
                  - [Subtitle 2](#subtitle-2)""".stripIndent();

        assertEquals(expected.replace("\r\n", "\n"), toc.replace("\r\n", "\n"));
    }
}
