package com.msp.cgroups;

import java.io.IOException;
import java.util.List;

/**
 * Filesystem abstraction for reading cgroup control files, allowing unit testing across OS environments.
 */
public interface CgroupFsProvider {

    boolean exists(String path);

    String readString(String path) throws IOException;

    List<String> readLines(String path) throws IOException;
}
