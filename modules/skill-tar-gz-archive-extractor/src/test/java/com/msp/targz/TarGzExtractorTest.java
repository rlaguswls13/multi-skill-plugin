package com.msp.targz;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TarGzExtractor Tests")
class TarGzExtractorTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Create archive, list entries, and extract back - round trip verification")
    void testRoundTripArchiveCreationAndExtraction() throws IOException {
        Path srcDir = tempDir.resolve("source");
        Files.createDirectories(srcDir.resolve("nested/dir"));

        Path file1 = srcDir.resolve("hello.txt");
        Files.writeString(file1, "Hello World from root file!", StandardCharsets.UTF_8);

        Path file2 = srcDir.resolve("nested/dir/nested.txt");
        Files.writeString(file2, "Deeply nested file content 1234567890", StandardCharsets.UTF_8);

        // 1. Create .tar.gz archive in memory
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        TarGzExtractor.createArchive(srcDir.toFile(), baos);
        byte[] archiveData = baos.toByteArray();
        assertTrue(archiveData.length > 0);

        // 2. List entries
        List<TarEntryInfo> entries = TarGzExtractor.listEntries(new ByteArrayInputStream(archiveData));
        assertFalse(entries.isEmpty());

        List<String> entryNames = entries.stream().map(TarEntryInfo::name).toList();
        assertTrue(entryNames.stream().anyMatch(n -> n.equals("hello.txt")));
        assertTrue(entryNames.stream().anyMatch(n -> n.contains("nested.txt")));
        assertTrue(entryNames.stream().anyMatch(n -> n.endsWith("/")));

        // 3. Extract to another directory
        Path destDir = tempDir.resolve("destination");
        List<File> extracted = TarGzExtractor.extractToDirectory(new ByteArrayInputStream(archiveData), destDir.toFile());
        assertFalse(extracted.isEmpty());

        Path extractedFile1 = destDir.resolve("hello.txt");
        assertTrue(Files.exists(extractedFile1));
        assertEquals("Hello World from root file!", Files.readString(extractedFile1, StandardCharsets.UTF_8));

        Path extractedFile2 = destDir.resolve("nested/dir/nested.txt");
        assertTrue(Files.exists(extractedFile2));
        assertEquals("Deeply nested file content 1234567890", Files.readString(extractedFile2, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("Handle long filenames (> 100 chars) correctly")
    void testLongFilenames() throws IOException {
        Path srcDir = tempDir.resolve("longname_src");
        Files.createDirectories(srcDir);

        String veryLongFileName = "very_long_file_name_that_exceeds_one_hundred_characters_in_total_length_for_ustar_gnu_header_support_test.txt";
        Path longFile = srcDir.resolve(veryLongFileName);
        Files.writeString(longFile, "Long name file content", StandardCharsets.UTF_8);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        TarGzExtractor.createArchive(srcDir.toFile(), baos);
        byte[] archiveBytes = baos.toByteArray();

        List<TarEntryInfo> entries = TarGzExtractor.listEntries(new ByteArrayInputStream(archiveBytes));
        assertEquals(1, entries.size());
        assertEquals(veryLongFileName, entries.getFirst().name());

        Path destDir = tempDir.resolve("longname_dest");
        TarGzExtractor.extractToDirectory(new ByteArrayInputStream(archiveBytes), destDir.toFile());
        assertTrue(Files.exists(destDir.resolve(veryLongFileName)));
        assertEquals("Long name file content", Files.readString(destDir.resolve(veryLongFileName)));
    }

    @Test
    @DisplayName("Prevent Zip Slip vulnerability with malicious path traversal entries")
    void testZipSlipDefense() throws IOException {
        ByteArrayOutputStream rawTar = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(rawTar)) {
            // Construct malicious header with "../traversal.txt"
            byte[] maliciousHeader = new byte[512];
            byte[] nameBytes = "../../traversal.txt".getBytes(StandardCharsets.UTF_8);
            System.arraycopy(nameBytes, 0, maliciousHeader, 0, nameBytes.length);
            // Mode
            byte[] mode = "0000644 ".getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(mode, 0, maliciousHeader, 100, mode.length);
            // Size: 5 bytes
            byte[] size = "00000000005 ".getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(size, 0, maliciousHeader, 124, size.length);
            // Mtime
            byte[] mtime = "00000000000 ".getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(mtime, 0, maliciousHeader, 136, mtime.length);
            maliciousHeader[156] = '0'; // normal file
            // Checksum
            Arrays.fill(maliciousHeader, 148, 156, (byte) ' ');
            long sum = 0;
            for (byte b : maliciousHeader) {
                sum += (b & 0xFF);
            }
            String sumOct = Long.toOctalString(sum);
            byte[] sumBytes = (sumOct + "\0 ").getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(sumBytes, 0, maliciousHeader, 148, sumBytes.length);

            gz.write(maliciousHeader);
            gz.write("pwned".getBytes(StandardCharsets.UTF_8));
            gz.write(new byte[512 - 5]); // padding
            gz.write(new byte[1024]); // end blocks
            gz.finish();
        }

        byte[] maliciousArchive = rawTar.toByteArray();
        Path extractDest = tempDir.resolve("safe_dest");

        assertThrows(SecurityException.class, () ->
                TarGzExtractor.extractToDirectory(new ByteArrayInputStream(maliciousArchive), extractDest.toFile())
        );
    }

    @Test
    @DisplayName("Validate TarEntryInfo immutability and argument checks")
    void testTarEntryInfoValidation() {
        assertThrows(NullPointerException.class, () -> new TarEntryInfo(null, 10, false, Instant.now()));
        assertThrows(NullPointerException.class, () -> new TarEntryInfo("name", 10, false, null));
        assertThrows(IllegalArgumentException.class, () -> new TarEntryInfo("name", -1, false, Instant.now()));

        TarEntryInfo entry = new TarEntryInfo("data.bin", 1024, false, Instant.EPOCH);
        assertEquals("data.bin", entry.name());
        assertEquals(1024, entry.size());
        assertFalse(entry.isDirectory());
        assertEquals(Instant.EPOCH, entry.mtime());
    }
}
