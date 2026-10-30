package com.msp.multipart;

import java.io.IOException;
import java.nio.file.Files;
import java.util.*;

/**
 * 파싱된 전체 멀티파트 컬렉션.
 * AutoCloseable을 구현하여 스왑된 임시 디스크 파일들을 안전하게 삭제합니다.
 */
public final class MultipartResult implements AutoCloseable {

    private final List<MultipartPart> parts;

    public MultipartResult(List<MultipartPart> parts) {
        this.parts = Collections.unmodifiableList(new ArrayList<>(parts));
    }

    public List<MultipartPart> parts() {
        return parts;
    }

    public Optional<MultipartPart> getPart(String name) {
        return parts.stream().filter(p -> Objects.equals(p.name(), name)).findFirst();
    }

    public Optional<String> getFieldValue(String name) {
        return getPart(name).map(p -> {
            try {
                return p.asString();
            } catch (IOException e) {
                return null;
            }
        });
    }

    @Override
    public void close() {
        for (MultipartPart part : parts) {
            if (part.tempFilePath() != null) {
                try {
                    Files.deleteIfExists(part.tempFilePath());
                } catch (IOException ignored) {}
            }
        }
    }
}
