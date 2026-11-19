package com.msp.cgroups;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;

/**
 * In-memory mock filesystem provider for simulated cgroup hierarchies during tests.
 */
public class MockCgroupFsProvider implements CgroupFsProvider {

    private final Map<String, String> files = new HashMap<>();

    public MockCgroupFsProvider withFile(String path, String content) {
        files.put(path, content);
        return this;
    }

    @Override
    public boolean exists(String path) {
        return files.containsKey(path);
    }

    @Override
    public String readString(String path) throws IOException {
        String content = files.get(path);
        if (content == null) {
            throw new FileNotFoundException("Mock file not found: " + path);
        }
        return content.strip();
    }

    @Override
    public List<String> readLines(String path) throws IOException {
        String content = files.get(path);
        if (content == null) {
            throw new FileNotFoundException("Mock file not found: " + path);
        }
        return Arrays.asList(content.split("\\r?\\n"));
    }
}
