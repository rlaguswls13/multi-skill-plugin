package com.msp.tablecopier.parser;

import java.util.List;

/**
 * 엑셀(.xlsx) 또는 텍스트(.txt/.csv)에서 파싱된 행·열 테이블 원본 구조
 *
 * @param detectedFormat 감지된 소스 포맷
 * @param headers        원본 열 헤더 목록 (헤더가 없는 경우 COL_1, COL_2 ... 자동 생성)
 * @param rows           데이터 행 목록 (각 행은 열 순서대로 정렬된 문자열 리스트)
 */
public record ParsedTableData(
        TabularSourceFormat detectedFormat,
        List<String> headers,
        List<List<String>> rows
) {
    public int columnCount() {
        return headers != null ? headers.size() : 0;
    }

    public int rowCount() {
        return rows != null ? rows.size() : 0;
    }
}
