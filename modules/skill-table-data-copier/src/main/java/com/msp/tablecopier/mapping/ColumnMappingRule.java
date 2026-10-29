package com.msp.tablecopier.mapping;

/**
 * 원본 테이블의 특정 열(인덱스 또는 원본 헤더명)을 타겟 DB 컬럼명으로 치환하기 위한 매핑 규칙.
 *
 * @param sourceColumnIndex 원본 열 인덱스 (0-based, 음수일 경우 sourceHeaderName으로 매칭)
 * @param sourceHeaderName  원본 열 헤더명 (예: "사원번호", "이름")
 * @param targetColumnName  변경(치환)할 타겟 DB 컬럼명 (예: "EMP_NO", "EMP_NAME")
 * @param transformRule     값 정제 규칙 (기본값 TRIM)
 * @param defaultValue      DEFAULT_IF_EMPTY 규칙 또는 빈 값일 때 채울 기본값
 */
public record ColumnMappingRule(
        int sourceColumnIndex,
        String sourceHeaderName,
        String targetColumnName,
        ValueTransformRule transformRule,
        String defaultValue
) {
    public ColumnMappingRule {
        if (targetColumnName == null || targetColumnName.isBlank()) {
            throw new IllegalArgumentException("타겟 컬럼명(targetColumnName)은 비어 있을 수 없습니다.");
        }
        targetColumnName = validateSqlIdentifier(targetColumnName.trim());
        if (transformRule == null) {
            transformRule = ValueTransformRule.TRIM;
        }
    }

    public static ColumnMappingRule byIndex(int sourceColumnIndex, String targetColumnName) {
        return new ColumnMappingRule(sourceColumnIndex, null, targetColumnName, ValueTransformRule.TRIM, null);
    }

    public static ColumnMappingRule byHeader(String sourceHeaderName, String targetColumnName) {
        return new ColumnMappingRule(-1, sourceHeaderName, targetColumnName, ValueTransformRule.TRIM, null);
    }

    public static ColumnMappingRule of(
            int sourceColumnIndex,
            String sourceHeaderName,
            String targetColumnName,
            ValueTransformRule transformRule,
            String defaultValue
    ) {
        return new ColumnMappingRule(sourceColumnIndex, sourceHeaderName, targetColumnName, transformRule, defaultValue);
    }

    public static String validateSqlIdentifier(String identifier) {
        if (identifier == null || !identifier.matches("^[A-Za-z_가-힣][A-Za-z0-9_가-힣]*$")) {
            throw new IllegalArgumentException("유효하지 않은 SQL 식별자(컬럼/테이블명)입니다: " + identifier);
        }
        return identifier;
    }
}
