package com.msp.tar;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TarArchiveTest {

    @Test
    @DisplayName("Write and read single file in TAR archive")
    void testSingleFileArchive() throws IOException {
        TarWriter writer = new TarWriter();
        String content = "Hello, pure Java 21 TAR streamer!";
        long epoch = 1700000000L;
        writer.putEntry("notes/hello.txt", content.getBytes(StandardCharsets.UTF_8), epoch);

        byte[] tarBytes = writer.toByteArray();
        assertTrue(tarBytes.length >= 512 + 512 + 1024); // header + 1 data block (512 padded) + 2 null blocks

        List<TarArchiveEntry> entries = TarReader.readEntries(tarBytes);
        assertEquals(1, entries.size());

        TarArchiveEntry entry = entries.get(0);
        assertEquals("notes/hello.txt", entry.name());
        assertEquals(content.getBytes(StandardCharsets.UTF_8).length, entry.size());
        assertEquals(epoch, entry.lastModifiedEpochSeconds());
        assertFalse(entry.isDirectory());
        assertEquals(content, entry.contentAsString());
    }

    @Test
    @DisplayName("Write and read multiple files and directories")
    void testMultipleEntriesAndDirectories() throws IOException {
        TarWriter writer = new TarWriter();
        writer.putDirectory("app/");
        writer.putDirectory("app/config");
        writer.putEntry("app/config/application.properties", "port=8080\nname=demo");
        // Test file larger than 512 bytes to verify multi-block padding
        byte[] largeData = new byte[1500];
        for (int i = 0; i < largeData.length; i++) {
            largeData[i] = (byte) (i % 256);
        }
        writer.putEntry("app/large.bin", largeData, 1699999999L);
        writer.putEntry("app/empty.txt", new byte[0]);

        byte[] tarBytes = writer.toByteArray();
        List<TarArchiveEntry> results = TarReader.readEntries(tarBytes);

        assertEquals(5, results.size());

        TarArchiveEntry dir1 = results.get(0);
        assertEquals("app/", dir1.name());
        assertTrue(dir1.isDirectory());
        assertEquals(0, dir1.size());

        TarArchiveEntry dir2 = results.get(1);
        assertEquals("app/config/", dir2.name());
        assertTrue(dir2.isDirectory());

        TarArchiveEntry conf = results.get(2);
        assertEquals("app/config/application.properties", conf.name());
        assertFalse(conf.isDirectory());
        assertEquals("port=8080\nname=demo", conf.contentAsString());

        TarArchiveEntry large = results.get(3);
        assertEquals("app/large.bin", large.name());
        assertEquals(1500, large.size());
        assertArrayEquals(largeData, large.content());

        TarArchiveEntry empty = results.get(4);
        assertEquals("app/empty.txt", empty.name());
        assertEquals(0, empty.size());
        assertEquals(0, empty.content().length);
    }

    @Test
    @DisplayName("Exact 512-byte file has no extra padding data block")
    void testExactBlockSizeFile() throws IOException {
        byte[] exact512 = new byte[512];
        for (int i = 0; i < 512; i++) {
            exact512[i] = (byte) 'A';
        }

        TarWriter writer = new TarWriter();
        writer.putEntry("exact.dat", exact512);
        byte[] tar = writer.toByteArray();

        // 512 (header) + 512 (data) + 1024 (null blocks) = 2048 bytes
        assertEquals(2048, tar.length);

        List<TarArchiveEntry> entries = TarReader.readEntries(tar);
        assertEquals(1, entries.size());
        assertArrayEquals(exact512, entries.get(0).content());
    }

    @Test
    @DisplayName("Empty TAR stream returns empty list")
    void testEmptyStream() throws IOException {
        byte[] empty = new byte[0];
        List<TarArchiveEntry> entries = TarReader.readEntries(empty);
        assertTrue(entries.isEmpty());

        byte[] onlyNullBlocks = new byte[1024];
        entries = TarReader.readEntries(onlyNullBlocks);
        assertTrue(entries.isEmpty());
    }

    @Test
    @DisplayName("TarReader throws IOException on corrupted premature stream")
    void testCorruptedStream() {
        byte[] truncatedHeader = new byte[250]; // less than 512
        assertThrows(IOException.class, () -> TarReader.readEntries(new ByteArrayInputStream(truncatedHeader)));
    }
}
