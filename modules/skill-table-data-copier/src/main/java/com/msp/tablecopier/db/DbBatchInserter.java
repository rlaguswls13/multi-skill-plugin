package com.msp.tablecopier.db;

import com.msp.tablecopier.mapping.ColumnMappingRule;
import com.msp.tablecopier.mapping.ColumnSubstitutionEngine;
import com.msp.tablecopier.mapping.ColumnSubstitutionEngine.ResolvedColumnMapping;
import com.msp.tablecopier.parser.ParsedTableData;
import com.msp.tablecopier.result.CopyErrorDetail;
import com.msp.tablecopier.result.CopyExecutionResponse;
import com.msp.tablecopier.result.CopyResultListener;
import com.msp.tablecopier.result.ErrorCategory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 치환된 테이블 데이터를 Access DB 또는 JDBC DB에 실제로 INSERT(Copy)하고,
 * 행 단위 성공/실패 및 에러 상세({@link CopyErrorDetail})를 수집하는 실행기.
 */
public class DbBatchInserter {

    private final ColumnSubstitutionEngine substitutionEngine;

    public DbBatchInserter(ColumnSubstitutionEngine substitutionEngine) {
        this.substitutionEngine = substitutionEngine;
    }

    public CopyExecutionResponse executeInsert(
            DbConnectionConfig dbConfig,
            ParsedTableData parsedData,
            String targetTable,
            List<ColumnMappingRule> mappings,
            CopyResultListener listener
    ) {
        long startTime = System.currentTimeMillis();
        String safeTable = ColumnMappingRule.validateSqlIdentifier(targetTable != null ? targetTable.trim() : "");
        List<ResolvedColumnMapping> resolvedMappings = substitutionEngine.resolveMappings(parsedData.headers(), mappings);
        String preparedSql = substitutionEngine.buildPreparedInsertSql(safeTable, resolvedMappings);

        List<List<String>> rows = parsedData.rows();
        int totalRows = rows.size();
        int insertedRows = 0;
        int failedRows = 0;
        List<CopyErrorDetail> errors = new ArrayList<>();

        try (Connection conn = dbConfig.openConnection()) {
            conn.setAutoCommit(false);

            try (PreparedStatement pstmt = conn.prepareStatement(preparedSql)) {
                for (int i = 0; i < totalRows; i++) {
                    int rowNumber = i + 1;
                    List<String> sourceRow = rows.get(i);
                    Map<String, Object> mappedRow = substitutionEngine.transformRow(sourceRow, resolvedMappings);

                    try {
                        bindParameters(pstmt, mappedRow);
                        pstmt.executeUpdate();
                        insertedRows++;
                        if (listener != null) {
                            listener.onRowSuccess(rowNumber, totalRows);
                        }
                    } catch (SQLException rowEx) {
                        failedRows++;
                        CopyErrorDetail errorDetail = toRowErrorDetail(rowNumber, mappedRow, rowEx);
                        errors.add(errorDetail);
                        if (listener != null) {
                            listener.onRowError(errorDetail);
                        }

                        if (dbConfig.transactionPolicy() == TransactionPolicy.ALL_OR_NOTHING) {
                            conn.rollback();
                            int skipped = totalRows - rowNumber;
                            long elapsed = System.currentTimeMillis() - startTime;
                            return new CopyExecutionResponse(
                                    safeTable,
                                    totalRows,
                                    0, // 전체 롤백되었으므로 최종 반영 행 수는 0
                                    failedRows,
                                    skipped,
                                    elapsed,
                                    preparedSql,
                                    Collections.unmodifiableList(errors)
                            );
                        }
                    }
                }
                conn.commit();
            } catch (SQLException stmtEx) {
                conn.rollback();
                CopyErrorDetail schemaError = new CopyErrorDetail(
                        0,
                        ErrorCategory.SQL_SYNTAX_OR_SCHEMA_ERROR,
                        null,
                        null,
                        stmtEx.getSQLState(),
                        stmtEx.getErrorCode(),
                        "테이블 또는 컬럼 스키마 확인 실패: " + safeTable,
                        stmtEx.getMessage(),
                        Collections.emptyMap()
                );
                errors.add(schemaError);
                if (listener != null) {
                    listener.onRowError(schemaError);
                }
                long elapsed = System.currentTimeMillis() - startTime;
                return new CopyExecutionResponse(
                        safeTable,
                        totalRows,
                        0,
                        totalRows,
                        0,
                        elapsed,
                        preparedSql,
                        Collections.unmodifiableList(errors)
                );
            }
        } catch (SQLException connEx) {
            CopyErrorDetail connError = new CopyErrorDetail(
                    0,
                    ErrorCategory.DB_CONNECTION_ERROR,
                    null,
                    dbConfig.jdbcUrl(),
                    connEx.getSQLState(),
                    connEx.getErrorCode(),
                    "데이터베이스 접속에 실패했습니다 (" + dbConfig.dbType() + ")",
                    connEx.getMessage(),
                    Collections.emptyMap()
            );
            errors.add(connError);
            if (listener != null) {
                listener.onRowError(connError);
            }
            long elapsed = System.currentTimeMillis() - startTime;
            return new CopyExecutionResponse(
                    safeTable,
                    totalRows,
                    0,
                    totalRows,
                    0,
                    elapsed,
                    preparedSql,
                    Collections.unmodifiableList(errors)
            );
        }

        long elapsed = System.currentTimeMillis() - startTime;
        return new CopyExecutionResponse(
                safeTable,
                totalRows,
                insertedRows,
                failedRows,
                0,
                elapsed,
                preparedSql,
                Collections.unmodifiableList(errors)
        );
    }

    private void bindParameters(PreparedStatement pstmt, Map<String, Object> mappedRow) throws SQLException {
        pstmt.clearParameters();
        int paramIdx = 1;
        for (Object value : mappedRow.values()) {
            pstmt.setObject(paramIdx++, value);
        }
    }

    private CopyErrorDetail toRowErrorDetail(int rowNumber, Map<String, Object> mappedRow, SQLException ex) {
        String sqlState = ex.getSQLState();
        ErrorCategory category = (sqlState != null && sqlState.startsWith("42"))
                ? ErrorCategory.SQL_SYNTAX_OR_SCHEMA_ERROR
                : ErrorCategory.DATA_CONSTRAINT_ERROR;

        String suspectedColumn = null;
        Object suspectedValue = null;
        String errMsgUpper = ex.getMessage() != null ? ex.getMessage().toUpperCase() : "";
        for (Map.Entry<String, Object> entry : mappedRow.entrySet()) {
            if (errMsgUpper.contains(entry.getKey().toUpperCase())) {
                suspectedColumn = entry.getKey();
                suspectedValue = entry.getValue();
                break;
            }
        }

        String userMessage = suspectedColumn != null
                ? String.format("%d행 [%s] 컬럼 값('%s') 입력 중 오류 발생", rowNumber, suspectedColumn, suspectedValue)
                : String.format("%d행 데이터 INSERT 중 제약조건/타입 오류 발생", rowNumber);

        return new CopyErrorDetail(
                rowNumber,
                category,
                suspectedColumn,
                suspectedValue,
                sqlState,
                ex.getErrorCode(),
                userMessage,
                ex.getMessage(),
                mappedRow
        );
    }
}
