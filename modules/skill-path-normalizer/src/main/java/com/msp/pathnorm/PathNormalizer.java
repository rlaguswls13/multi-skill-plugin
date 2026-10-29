package com.msp.pathnorm;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Cross-platform path normalizer and Path Traversal attack prevention guard.
 * Zero external dependencies, pure Java 21.
 */
public final class PathNormalizer {

    private static final Pattern ILLEGAL_CHARS_PATTERN = Pattern.compile("[\\x00-\\x1F\\x7F<>:\"/\\\\|?*]");
    private static final Set<String> WINDOWS_RESERVED_NAMES = Set.of(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
    );

    private PathNormalizer() {
    }

    /**
     * Lexically normalizes a path string:
     * - Converts backslashes to forward slashes.
     * - Collapses multiple consecutive slashes.
     * - Resolves '.' and '..' path segments without disk I/O.
     * - Strips redundant trailing slashes.
     *
     * @param path raw path string
     * @return normalized path string
     */
    public static String normalize(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }

        String decoded = decodeUrlEncodings(path);
        String s = decoded.replace('\\', '/');

        // Check if Windows drive letter is present (e.g. C:/)
        String prefix = "";
        if (s.length() >= 2 && Character.isLetter(s.charAt(0)) && s.charAt(1) == ':') {
            prefix = s.substring(0, 2);
            s = s.substring(2);
        }

        boolean isAbsolute = s.startsWith("/");
        String[] tokens = s.split("/+");

        Deque<String> stack = new ArrayDeque<>();
        for (String token : tokens) {
            if (token.isEmpty() || ".".equals(token)) {
                continue;
            }
            if ("..".equals(token)) {
                if (!stack.isEmpty() && !"..".equals(stack.peekLast())) {
                    stack.removeLast();
                } else if (!isAbsolute) {
                    stack.addLast("..");
                }
            } else {
                stack.addLast(token);
            }
        }

        StringBuilder sb = new StringBuilder(prefix);
        if (isAbsolute) {
            sb.append("/");
        }

        if (stack.isEmpty()) {
            if (!isAbsolute && prefix.isEmpty()) {
                return ".";
            }
            return sb.toString();
        }

        sb.append(String.join("/", stack));
        return sb.toString();
    }

    /**
     * Resolves userPath safely within baseDir.
     * Throws {@link SecurityException} if the resolved path escapes baseDir (Path Traversal attempt),
     * contains null-byte poisoning, or represents an illegal absolute path.
     *
     * @param baseDir  the root base directory to restrict access to
     * @param userPath user-supplied relative path string
     * @return normalized absolute Path strictly within baseDir
     * @throws SecurityException        if path traversal or poisoning is detected
     * @throws IllegalArgumentException if baseDir or userPath is null
     */
    public static Path safeResolve(Path baseDir, String userPath) {
        Objects.requireNonNull(baseDir, "baseDir must not be null");
        Objects.requireNonNull(userPath, "userPath must not be null");

        if (userPath.isBlank()) {
            throw new IllegalArgumentException("userPath must not be blank");
        }

        // Detect null byte poisoning before and after URL decode
        if (userPath.contains("\0")) {
            throw new SecurityException("Null byte poisoning detected in path: " + userPath);
        }

        String decoded = decodeUrlEncodings(userPath);
        if (decoded.contains("\0")) {
            throw new SecurityException("Null byte poisoning detected after decode: " + userPath);
        }

        // Prevent absolute paths or Windows drive escaping
        Path normalizedBase = baseDir.toAbsolutePath().normalize();
        Path candidatePath = Path.of(decoded);

        if (candidatePath.isAbsolute() || decoded.startsWith("/") || decoded.startsWith("\\")) {
            // Check if user is attempting absolute path breakout
            throw new SecurityException("Absolute path breakout detected: " + userPath);
        }

        Path resolved = normalizedBase.resolve(decoded).normalize();

        // Enforce boundary check: resolved must start with baseDir
        if (!resolved.startsWith(normalizedBase)) {
            throw new SecurityException("Path traversal attempt detected: " + userPath);
        }

        return resolved;
    }

    /**
     * Resolves userPath safely within baseDir.
     */
    public static Path safeResolve(Path baseDir, Path userPath) {
        Objects.requireNonNull(userPath, "userPath must not be null");
        return safeResolve(baseDir, userPath.toString());
    }

    /**
     * Returns true if userPath can be safely resolved within baseDir without escaping.
     */
    public static boolean isSafePath(Path baseDir, String userPath) {
        try {
            safeResolve(baseDir, userPath);
            return true;
        } catch (SecurityException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Sanitizes a file name by removing illegal OS characters (: * ? " < > | \0 / \),
     * trimming leading/trailing dots and spaces, protecting against Windows reserved device names,
     * and limiting length to 255 characters.
     *
     * @param input raw file name
     * @return safe file name
     */
    public static String sanitizeFileName(String input) {
        if (input == null || input.isBlank()) {
            return "unnamed";
        }

        String decoded = decodeUrlEncodings(input);

        // Strip illegal OS characters
        String cleaned = ILLEGAL_CHARS_PATTERN.matcher(decoded).replaceAll("");

        // Trim leading and trailing dots and whitespace
        cleaned = cleaned.replaceAll("^[. \\t]+|[. \\t]+$", "");

        if (cleaned.isEmpty()) {
            return "unnamed";
        }

        // Check Windows reserved names (CON, PRN, AUX, NUL, COM1-9, LPT1-9)
        String baseName = cleaned;
        int dotIndex = cleaned.indexOf('.');
        if (dotIndex != -1) {
            baseName = cleaned.substring(0, dotIndex);
        }

        if (WINDOWS_RESERVED_NAMES.contains(baseName.toUpperCase(Locale.ROOT))) {
            cleaned = "_" + cleaned;
        }

        // Limit file name length to 255 characters
        if (cleaned.length() > 255) {
            cleaned = cleaned.substring(0, 255);
        }

        return cleaned;
    }

    private static String decodeUrlEncodings(String input) {
        if (!input.contains("%")) {
            return input;
        }
        String current = input;
        try {
            // Multi-pass decode to prevent nested encoding attacks like %252e%252e
            for (int i = 0; i < 3; i++) {
                if (!current.contains("%")) {
                    break;
                }
                String next = URLDecoder.decode(current, StandardCharsets.UTF_8);
                if (next.equals(current)) {
                    break;
                }
                current = next;
            }
        } catch (Exception ignored) {
            // Keep current if decode error
        }
        return current;
    }
}
