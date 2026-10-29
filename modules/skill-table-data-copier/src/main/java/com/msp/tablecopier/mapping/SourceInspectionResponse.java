package com.msp.tablecopier.mapping;

import com.msp.tablecopier.parser.TabularSourceFormat;

import java.util.List;
import java.util.Map;

/**
 * [API 표시 규격 - Step 1] 원본 엑셀/TXT 테이블 구조 분석 응답 DTO.
 * 화면에서 원본 열 목록과 샘플 데이터를 먼저 표기하고 사용자가 원하는 타겟 컬럼명을 입력할 수 있도록 지원합니다.
 *
 * @param detectedFormat 감지된 파일/텍스트 포맷
 * @param totalColumns   전체 열 개수
 * @param totalRows      전체 데이터 행 개수
 * @param columns        열별 인덱스, 원본 헤더명, 샘플 값 요약
 * @param sampleRows     상위 샘플 행 데이터 (열 헤더 -> 값 매핑)
 */
public record SourceInspectionResponse(
        TabularSourceFormat detectedFormat,
        int totalColumns,
        int totalRows,
        List<ColumnInfo> columns,
        List<Map<String, String>> sampleRows
) {
    public record ColumnInfo(
            int columnIndex,
            String sourceHeader,
            List<String> sampleValues
    ) {
    }
}
