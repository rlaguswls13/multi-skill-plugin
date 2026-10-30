package com.msp.ipgeo;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * CIDR 대역과 메타데이터(예: 국가 코드, 서브넷 명칭 등)를 매핑하여 고속 검색하는 테이블.
 */
public final class CidrTable<T> {

    private record Entry<T>(Cidr cidr, T value) {}

    private final List<Entry<T>> entries = new ArrayList<>();

    public CidrTable<T> add(String cidrStr, T value) {
        entries.add(new Entry<>(Cidr.of(cidrStr), value));
        return this;
    }

    public Optional<T> find(String ip) {
        if (ip == null || ip.isBlank()) return Optional.empty();
        for (Entry<T> entry : entries) {
            if (entry.cidr().matches(ip)) {
                return Optional.of(entry.value());
            }
        }
        return Optional.empty();
    }
}
