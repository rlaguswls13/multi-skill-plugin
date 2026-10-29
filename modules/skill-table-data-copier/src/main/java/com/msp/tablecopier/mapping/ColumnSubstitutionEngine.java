package com.msp.tablecopier.mapping;

import com.msp.tablecopier.mapping.SourceInspectionResponse.ColumnInfo;
import com.msp.tablecopier.mapping.SubstitutionPreviewResponse.ColumnTransitionView;
import com.msp.tablecopier.mapping.SubstitutionPreviewResponse.SubstitutedRowView;
import com.msp.tablecopier.parser.ParsedTableData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 원본 행/열 데이터를 사용자가 지정한 컬럼 매핑 규칙({@link ColumnMappingRule})에 따라 치환하고,
 * 화면 표시용 API DTO({@link SourceInspectionResponse}, {@link SubstitutionPreviewResponse}) 및
 * DB INSERT 바인딩 데이터를 생성하는 핵심 엔진.
 */
public class ColumnSubstitutionEngine {

    /**
     * 파싱된 원본 테이블 데이터를 분석하여 화면 표기용 {@link SourceInspectionResponse}를 생성합니다.
     */
    public SourceInspectionResponse inspect(ParsedTableData parsedData, int maxSampleRows) {
        int limit = Math.max(1, maxSampleRows);
        List<String> headers = parsedData.headers();
        List<List<String>> rows = parsedData.rows();

        List<ColumnInfo> columns = new ArrayList<>(headers.size());
        for (int colIdx = 0; colIdx < headers.size(); colIdx++) {
            List<String> samples = new ArrayList<>();
            for (int r = 0; r < Math.min(rows.size(), limit); r++) {
                List<String> row = rows.get(r);
                if (colIdx < row.size()) {
                    samples.add(row.get(colIdx));
                }
            }
            columns.add(new ColumnInfo(colIdx, headers.get(colIdx), Collections.unmodifiableList(samples)));
        }

        List<Map<String, String>> sampleRowMaps = new ArrayList<>();
        for (int r = 0; r < Math.min(rows.size(), limit); r++) {
            List<String> row = rows.get(r);
            Map<String, String> rowMap = new LinkedHashMap<>();
            for (int c = 0; c < headers.size(); c++) {
                rowMap.put(headers.get(c), c < row.size() ? row.get(c) : "");
            }
            sampleRowMaps.add(Collections.unmodifiableMap(rowMap));
        }

        return new SourceInspectionResponse(
                parsedData.detectedFormat(),
                headers.size(),
                rows.size(),
                Collections.unmodifiableList(columns),
                Collections.unmodifiableList(sampleRowMaps)
        );
    }

    /**
     * 원본 데이터의 각 열을 원하는 타겟 컬럼명으로 치환하여 화면 미리보기 응답({@link SubstitutionPreviewResponse})을 생성합니다.
     */
    public SubstitutionPreviewResponse previewSubstitution(
            ParsedTableData parsedData,
            String targetTable,
            List<ColumnMappingRule> mappings,
            int maxPreviewRows
    ) {
        String safeTable = ColumnMappingRule.validateSqlIdentifier(targetTable != null ? targetTable.trim() : "");
        List<ResolvedColumnMapping> resolvedMappings = resolveMappings(parsedData.headers(), mappings);
        String preparedSql = buildPreparedInsertSql(safeTable, resolvedMappings);

        List<List<String>> rows = parsedData.rows();
        List< String > firstRow = rows.isEmpty() ? Collections.emptyList() : rows.get(0);

        List<ColumnTransitionView> transitions = new ArrayList<>(resolvedMappings.size());
        for (ResolvedColumnMapping rm : resolvedMappings) {
            String sampleBefore = rm.sourceColumnIndex() < firstRow.size() ? firstRow.get(rm.sourceColumnIndex()) : "";
            Object sampleAfter = rm.rule().transformRule().apply(sampleBefore, rm.rule().defaultValue());
            transitions.add(new ColumnTransitionView(
                    rm.sourceColumnIndex(),
                    rm.sourceHeaderName(),
                    rm.rule().targetColumnName(),
                    rm.rule().transformRule(),
                    sampleBefore,
                    sampleAfter
            ));
        }

        int previewLimit = maxPreviewRows <= 0 ? rows.size() : Math.min(rows.size(), maxPreviewRows);
        List<SubstitutedRowView> previewRows = new ArrayList<>(previewLimit);
        for (int r = 0; r < previewLimit; r++) {
            List<String> sourceRow = rows.get(r);
            Map<String, Object> mappedRow = transformRow(sourceRow, resolvedMappings);
            String renderedSql = buildRenderedInsertSql(safeTable, mappedRow);
            previewRows.add(new SubstitutedRowView(r + 1, mappedRow, renderedSql));
        }

        return new SubstitutionPreviewResponse(
                safeTable,
                rows.size(),
                previewRows.size(),
                preparedSql,
                Collections.unmodifiableList(transitions),
                Collections.unmodifiableList(previewRows)
        );
    }

    /**
     * 원본 열 헤더와 매핑 규칙을 대조하여 실제 열 인덱스가 확정된 매핑 목록을 반환합니다.
     */
    public List<ResolvedColumnMapping> resolveMappings(List<String> sourceHeaders, List<ColumnMappingRule> rules) {
        if (rules == null || rules.isEmpty()) {
            throw new IllegalArgumentException("컬럼 치환 매핑 규칙(ColumnMappingRule)이 최소 1개 이상 필요합니다.");
        }
        List<ResolvedColumnMapping> resolved = new ArrayList<>(rules.size());
        for (ColumnMappingRule rule : rules) {
            int colIdx = rule.sourceColumnIndex();
            if (colIdx < 0) {
                colIdx = findHeaderIndex(sourceHeaders, rule.sourceHeaderName());
                if (colIdx < 0) {
                    throw new IllegalArgumentException(
                            "원본 데이터에서 지정한 열 헤더를 찾을 수 없습니다: '" + rule.sourceHeaderName()
                                    + "' (사용 가능한 헤더: " + sourceHeaders + ")"
                    );
                }
            } else if (colIdx >= sourceHeaders.size()) {
                throw new IllegalArgumentException(
                        "원본 열 인덱스 범위 초과: index=" + colIdx + ", 총 열 개수=" + sourceHeaders.size()
                );
            }
            String headerName = sourceHeaders.get(colIdx);
            resolved.add(new ResolvedColumnMapping(colIdx, headerName, rule));
        }
        return Collections.unmodifiableList(resolved);
    }

    /**
     * 단일 행을 타겟 컬럼명 -> 변환 값 맵으로 치환합니다.
     */
    public Map<String, Object> transformRow(List<String> sourceRow, List<ResolvedColumnMapping> resolvedMappings) {
        Map<String, Object> mapped = new LinkedHashMap<>();
        for (ResolvedColumnMapping rm : resolvedMappings) {
            String rawCell = rm.sourceColumnIndex() < sourceRow.size() ? sourceRow.get(rm.sourceColumnIndex()) : "";
            Object transformed = rm.rule().transformRule().apply(rawCell, rm.rule().defaultValue());
            mapped.put(rm.rule().targetColumnName(), transformed);
        }
        return Collections.unmodifiableMap(mapped);
    }

    /**
     * PreparedStatement 실행을 위한 파라미터 바인딩용 INSERT SQL 생성
     */
    public String buildPreparedInsertSql(String targetTable, List<ResolvedColumnMapping> resolvedMappings) {
        String columnsClause = resolvedMappings.stream()
                .map(rm -> rm.rule().targetColumnName())
                .collect(Collectors.joining(", "));
        String placeholders = resolvedMappings.stream()
                .map(rm -> "?")
                .collect(Collectors.joining(", "));
        return "INSERT INTO " + targetTable + " (" + columnsClause + ") VALUES (" + placeholders + ")";
    }

    /**
     * 화면 미리보기용 리터럴 INSERT SQL 문자열 생성
     */
    public String buildRenderedInsertSql(String targetTable, Map<String, Object> mappedRow) {
        String columnsClause = String.join(", ", mappedRow.keySet());
        String valuesClause = mappedRow.values().stream()
                .map(this::formatSqlLiteral)
                .collect(Collectors.joining(", "));
        return "INSERT INTO " + targetTable + " (" + columnsClause + ") VALUES (" + valuesClause + ");";
    }

    private String formatSqlLiteral(Object val) {
        if (val == null) {
            return "NULL";
        }
        String str = String.valueOf(val);
        return "'" + str.replace("'", "''") + "'";
    }

    private int findHeaderIndex(List<String> headers, String targetHeader) {
        if (targetHeader == null) {
            return -1;
        }
        String trimmed = targetHeader.trim();
        for (int i = 0; i < headers.size(); i++) {
            if (headers.get(i).trim().equalsIgnoreCase(trimmed)) {
                return i;
            }
        }
        return -1;
    }

    public record ResolvedColumnMapping(
            int sourceColumnIndex,
            String sourceHeaderName,
            ColumnMappingRule rule
    ) {
    }
}
