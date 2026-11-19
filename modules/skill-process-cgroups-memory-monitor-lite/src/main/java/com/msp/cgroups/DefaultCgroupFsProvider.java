package com.msp.cgroups;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Standard filesystem provider accessing local files using Java NIO.
 */
public class DefaultCgroupFsProvider implements CgroupFsProvider {

    @Override
    public boolean exists(String path) {
        return Files.exists(Paths.get(path));
    }

    @Override
    public String readString(String path) throws IOException {
        return Files.readString(Paths.get(path), StandardCharsets.UTF_8).strip();
    }

    @Override
    public List<String> readLines(String path) throws IOException {
        return Files.readAllLines(Paths.get(path), StandardCharsets.UTF_8);
    }
}
