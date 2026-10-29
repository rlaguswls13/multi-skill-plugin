package com.msp.tablecopier.api;

import com.msp.tablecopier.db.DbBatchInserter;
import com.msp.tablecopier.db.DbConnectionConfig;
import com.msp.tablecopier.mapping.ColumnMappingRule;
import com.msp.tablecopier.mapping.ColumnSubstitutionEngine;
import com.msp.tablecopier.mapping.SourceInspectionResponse;
import com.msp.tablecopier.mapping.SubstitutionPreviewResponse;
import com.msp.tablecopier.parser.ParsedTableData;
import com.msp.tablecopier.parser.TabularDataParser;
import com.msp.tablecopier.result.CopyErrorDetail;
import com.msp.tablecopier.result.CopyExecutionResponse;
import com.msp.tablecopier.result.CopyResultListener;
import com.msp.tablecopier.result.CopyStatus;
import com.msp.tablecopier.result.ErrorCategory;
import com.msp.tablecopier.result.ResultNotification;

import java.util.Collections;
import java.util.List;

/**
 * {@code skill-table-data-copier} 모듈의 단일 진입점(Facade API).
 * <p>
 * 엑셀(.xlsx, .csv) 또는 텍스트(.txt) 형태의 테이블 데이터를 받아:
 * <ol>
 *   <li>{@link #inspectSource(SourceDataInput, int)}: 원본 열 헤더와 샘플 데이터를 분석하여 API 응답으로 반환</li>
 *   <li>{@link #previewSubstitution(SourceDataInput, String, List, int)}: 각 열을 원하는 컬럼명으로 치환한 화면 미리보기 및 SQL을 API 응답으로 반환</li>
 *   <li>{@link #executeCopy(SourceDataInput, DbConnectionConfig, String, List, CopyResultListener)}: MS Access 또는 JDBC DB에 실제로 Insert(Copy)하고 결과 알림 및 행별 에러 상세를 반환</li>
 * </ol>
 */
public class TableDataCopierApi {

    private final TabularDataParser parser;
    private final ColumnSubstitutionEngine substitutionEngine;
    private final DbBatchInserter batchInserter;

    public TableDataCopierApi() {
        this.parser = new TabularDataParser();
        this.substitutionEngine = new ColumnSubstitutionEngine();
        this.batchInserter = new DbBatchInserter(this.substitutionEngine);
    }

    /**
     * [Step 1 API] 입력된 엑셀/TXT 데이터의 원본 열 목록과 상위 샘플 행을 추출하여 화면 표시용 API 응답으로 반환합니다.
     */
    public ApiResponse<SourceInspectionResponse> inspectSource(SourceDataInput input, int maxSampleRows) {
        try {
            ParsedTableData parsed = parseInput(input);
            SourceInspectionResponse response = substitutionEngine.inspect(parsed, maxSampleRows);
            ResultNotification notification = ResultNotification.success(
                    "SOURCE_INSPECTED",
                    "원본 테이블 분석 완료",
                    String.format("총 %d개 열, %d개 데이터 행이 감지되었습니다.", response.totalColumns(), response.totalRows())
            );
            return new ApiResponse<>(true, CopyStatus.SUCCESS, notification, response);
        } catch (Exception e) {
            ResultNotification notification = ResultNotification.error(
                    "SOURCE_PARSE_FAILED",
                    "원본 데이터 파싱 실패",
                    e.getMessage()
            );
            return new ApiResponse<>(false, CopyStatus.FAILED, notification, null);
        }
    }

    /**
     * [Step 2 API] 원본 열을 원하는 타겟 컬럼명으로 치환했을 때 화면에 표기할 전/후 비교 데이터와 INSERT SQL 미리보기를 반환합니다.
     */
    public ApiResponse<SubstitutionPreviewResponse> previewSubstitution(
            SourceDataInput input,
            String targetTable,
            List<ColumnMappingRule> mappings,
            int maxPreviewRows
    ) {
        try {
            ParsedTableData parsed = parseInput(input);
            SubstitutionPreviewResponse preview = substitutionEngine.previewSubstitution(
                    parsed, targetTable, mappings, maxPreviewRows
            );
            ResultNotification notification = ResultNotification.success(
                    "SUBSTITUTION_PREVIEW_READY",
                    "컬럼 치환 미리보기 생성 완료",
                    String.format("테이블 [%s] 대상 %d개 컬럼 치환 미리보기가 준비되었습니다 (전체 %d행 중 %d행 표시).",
                            preview.targetTable(), preview.columnTransitions().size(),
                            preview.totalSourceRows(), preview.previewRowCount())
            );
            return new ApiResponse<>(true, CopyStatus.SUCCESS, notification, preview);
        } catch (Exception e) {
            ResultNotification notification = ResultNotification.error(
                    "SUBSTITUTION_PREVIEW_FAILED",
                    "컬럼 치환 미리보기 생성 실패",
                    e.getMessage()
            );
            return new ApiResponse<>(false, CopyStatus.FAILED, notification, null);
        }
    }

    /**
     * [Step 3 API] 치환된 컬럼명 기준으로 대상 DB(Access DB 또는 일반 JDBC DB)에 데이터를 실제로 INSERT(Copy)하고,
     * 결과 알림(Notification) 및 에러 상세 목록을 반환합니다.
     */
    public ApiResponse<CopyExecutionResponse> executeCopy(
            SourceDataInput input,
            DbConnectionConfig dbConfig,
            String targetTable,
            List<ColumnMappingRule> mappings
    ) {
        return executeCopy(input, dbConfig, targetTable, mappings, null);
    }

    /**
     * [Step 3 API - 콜백 포함] 치환된 데이터를 대상 DB에 INSERT(Copy)하며 실시간 리스너 및 최종 API 응답을 반환합니다.
     */
    public ApiResponse<CopyExecutionResponse> executeCopy(
            SourceDataInput input,
            DbConnectionConfig dbConfig,
            String targetTable,
            List<ColumnMappingRule> mappings,
            CopyResultListener listener
    ) {
        long start = System.currentTimeMillis();
        ParsedTableData parsed;
        try {
            parsed = parseInput(input);
        } catch (Exception parseEx) {
            CopyErrorDetail errorDetail = new CopyErrorDetail(
                    0,
                    ErrorCategory.FILE_PARSE_ERROR,
                    null,
                    null,
                    null,
                    0,
                    "원본 엑셀/TXT 데이터 파싱 실패: " + parseEx.getMessage(),
                    parseEx.toString(),
                    Collections.emptyMap()
            );
            CopyExecutionResponse failRes = new CopyExecutionResponse(
                    targetTable, 0, 0, 0, 0, System.currentTimeMillis() - start, "", List.of(errorDetail)
            );
            ResultNotification notification = ResultNotification.error(
                    "FILE_PARSE_ERROR",
                    "원본 데이터 읽기 실패",
                    parseEx.getMessage()
            );
            if (listener != null) {
                listener.onRowError(errorDetail);
                listener.onComplete(CopyStatus.FAILED, notification, failRes);
            }
            return new ApiResponse<>(false, CopyStatus.FAILED, notification, failRes);
        }

        CopyExecutionResponse execResponse;
        try {
            execResponse = batchInserter.executeInsert(dbConfig, parsed, targetTable, mappings, listener);
        } catch (Exception mappingEx) {
            CopyErrorDetail errorDetail = new CopyErrorDetail(
                    0,
                    ErrorCategory.MAPPING_TRANSFORM_ERROR,
                    null,
                    null,
                    null,
                    0,
                    "컬럼 치환 매핑 설정 오류: " + mappingEx.getMessage(),
                    mappingEx.toString(),
                    Collections.emptyMap()
            );
            CopyExecutionResponse failRes = new CopyExecutionResponse(
                    targetTable, parsed.rowCount(), 0, parsed.rowCount(), 0,
                    System.currentTimeMillis() - start, "", List.of(errorDetail)
            );
            ResultNotification notification = ResultNotification.error(
                    "MAPPING_TRANSFORM_ERROR",
                    "컬럼 치환 매핑 오류",
                    mappingEx.getMessage()
            );
            if (listener != null) {
                listener.onRowError(errorDetail);
                listener.onComplete(CopyStatus.FAILED, notification, failRes);
            }
            return new ApiResponse<>(false, CopyStatus.FAILED, notification, failRes);
        }

        CopyStatus status;
        ResultNotification notification;
        boolean success;

        if (execResponse.errors().isEmpty()) {
            status = CopyStatus.SUCCESS;
            success = true;
            notification = ResultNotification.success(
                    "COPY_COMPLETED",
                    "데이터 복사(INSERT) 완료",
                    String.format("테이블 [%s]에 총 %d건 중 %d건이 모두 정상 INSERT 되었습니다.",
                            execResponse.targetTable(), execResponse.totalRows(), execResponse.insertedRows())
            );
        } else if (execResponse.insertedRows() > 0) {
            status = CopyStatus.PARTIAL_SUCCESS;
            success = true;
            notification = ResultNotification.warning(
                    "COPY_PARTIAL_WARNING",
                    String.format("일부 행 복사 실패 (%d건 실패)", execResponse.failedRows()),
                    String.format("테이블 [%s]에 총 %d건 중 %d건 성공, %d건 에러가 발생했습니다. errors 목록을 확인하세요.",
                            execResponse.targetTable(), execResponse.totalRows(),
                            execResponse.insertedRows(), execResponse.failedRows())
            );
        } else {
            status = CopyStatus.FAILED;
            success = false;
            CopyErrorDetail firstErr = execResponse.errors().get(0);
            notification = ResultNotification.error(
                    firstErr.errorCategory().name(),
                    "데이터 복사(INSERT) 실패",
                    firstErr.userMessage() + " (" + firstErr.technicalMessage() + ")"
            );
        }

        if (listener != null) {
            listener.onComplete(status, notification, execResponse);
        }
        return new ApiResponse<>(success, status, notification, execResponse);
    }

    private ParsedTableData parseInput(SourceDataInput input) throws Exception {
        if (input == null) {
            throw new IllegalArgumentException("SourceDataInput이 null입니다.");
        }
        if (input.filePath() != null) {
            return parser.parseFile(
                    input.filePath(),
                    input.format(),
                    input.customDelimiter(),
                    input.firstRowIsHeader(),
                    input.charset()
            );
        }
        if (input.fileBytes() != null && input.fileBytes().length > 0) {
            return parser.parseBytes(
                    input.fileBytes(),
                    input.fileNameHint(),
                    input.format(),
                    input.customDelimiter(),
                    input.firstRowIsHeader(),
                    input.charset()
            );
        }
        if (input.rawText() != null && !input.rawText().isBlank()) {
            return parser.parseText(
                    input.rawText(),
                    input.format(),
                    input.customDelimiter(),
                    input.firstRowIsHeader()
            );
        }
        throw new IllegalArgumentException("입력 데이터(filePath, fileBytes, rawText 중 하나)를 제공해야 합니다.");
    }
}
