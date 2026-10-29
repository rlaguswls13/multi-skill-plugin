package com.msp.tablecopier.mapping;

import java.util.List;
import java.util.Map;

/**
 * [API 표시 규격 - Step 2] 컬럼명 치환 결과를 화면(UI)에 표기하기 위한 통일된 프리뷰 응답 DTO.
 *
 * @param targetTable          INSERT 대상 테이블명
 * @param totalSourceRows      원본 전체 행 수
 * @param previewRowCount      미리보기에 포함된 행 수
 * @param preparedSqlTemplate  PreparedStatement 용 SQL 템플릿 (예: INSERT INTO TB_EMP (EMP_NO, EMP_NM) VALUES (?, ?))
 * @param columnTransitions    화면 상단 컬럼 매핑 변화 표시용 리스트 (원본 헤더 -> 치환된 컬럼명 + 변환 전/후 샘플)
 * @param substitutedRows      치환된 컬럼명을 Key로 갖는 행 데이터 및 개별 INSERT SQL 미리보기
 */
public record SubstitutionPreviewResponse(
        String targetTable,
        int totalSourceRows,
        int previewRowCount,
        String preparedSqlTemplate,
        List<ColumnTransitionView> columnTransitions,
        List<SubstitutedRowView> substitutedRows
) {
    /**
     * 개별 열의 치환 전/후 매핑 정보 (화면 헤더 매핑 뱃지/화살표 표기용)
     */
    public record ColumnTransitionView(
            int sourceColumnIndex,
            String sourceHeaderName,
            String targetColumnName,
            ValueTransformRule transformRule,
            String sampleBefore,
            Object sampleAfter
    ) {
    }

    /**
     * 치환 완료된 단일 행의 화면 테이블 바인딩 및 SQL 미리보기 구조
     */
    public record SubstitutedRowView(
            int rowNumber,
            Map<String, Object> mappedColumns,
            String renderedInsertSql
    ) {
    }
}
