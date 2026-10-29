package com.msp.tablecopier;

import com.msp.tablecopier.api.ApiResponse;
import com.msp.tablecopier.api.SourceDataInput;
import com.msp.tablecopier.api.TableDataCopierApi;
import com.msp.tablecopier.db.DbConnectionConfig;
import com.msp.tablecopier.db.DbType;
import com.msp.tablecopier.db.TransactionPolicy;
import com.msp.tablecopier.mapping.ColumnMappingRule;
import com.msp.tablecopier.mapping.SourceInspectionResponse;
import com.msp.tablecopier.mapping.SubstitutionPreviewResponse;
import com.msp.tablecopier.mapping.ValueTransformRule;
import com.msp.tablecopier.parser.TabularSourceFormat;
import com.msp.tablecopier.result.CopyErrorDetail;
import com.msp.tablecopier.result.CopyExecutionResponse;
import com.msp.tablecopier.result.CopyResultListener;
import com.msp.tablecopier.result.CopyStatus;
import com.msp.tablecopier.result.ErrorCategory;
import com.msp.tablecopier.result.ResultNotification.NotificationLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class TableDataCopierApiTest {

    private TableDataCopierApi api;
    private String h2JdbcUrl;

    @BeforeEach
    void setUp() throws Exception {
        api = new TableDataCopierApi();
        h2JdbcUrl = "jdbc:h2:mem:testdb_" + System.nanoTime() + ";DB_CLOSE_DELAY=-1";
        try (Connection conn = DriverManager.getConnection(h2JdbcUrl, "sa", "");
             Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    CREATE TABLE TB_EMPLOYEE (
                        EMP_NO VARCHAR(20) PRIMARY KEY,
                        EMP_NAME VARCHAR(50) NOT NULL,
                        DEPT_CD VARCHAR(20) NOT NULL,
                        SALARY INT
                    )
                    """);
        }
    }

    @Test
    @DisplayName("1. TXT(탭/파이프 구분) 데이터 열 분석, 원하는 컬럼명 치환 프리뷰 API 및 DB Insert 성공 검증")
    void testTxtInspectPreviewAndCopySuccess() throws Exception {
        String txtData = """
                사원번호\t이름\t부서\t급여
                E1001\t 홍길동 \tdev\t5500
                E1002\t 이순신 \tsales\t6200
                E1003\t 강감찬 \t\t4800
                """;
        SourceDataInput input = SourceDataInput.fromText(txtData);

        // Step 1: 원본 분석 API
        ApiResponse<SourceInspectionResponse> inspectRes = api.inspectSource(input, 5);
        assertTrue(inspectRes.success());
        assertEquals(CopyStatus.SUCCESS, inspectRes.status());
        assertEquals(TabularSourceFormat.TXT_TSV, inspectRes.data().detectedFormat());
        assertEquals(4, inspectRes.data().totalColumns());
        assertEquals(3, inspectRes.data().totalRows());

        // Step 2: 컬럼 치환 미리보기 API (화면 표기용)
        List<ColumnMappingRule> mappings = List.of(
                ColumnMappingRule.byHeader("사원번호", "EMP_NO"),
                ColumnMappingRule.of(1, "이름", "EMP_NAME", ValueTransformRule.TRIM, null),
                ColumnMappingRule.of(2, "부서", "DEPT_CD", ValueTransformRule.DEFAULT_IF_EMPTY, "GENERAL"),
                ColumnMappingRule.of(3, "급여", "SALARY", ValueTransformRule.DIGITS_ONLY, "0")
        );

        ApiResponse<SubstitutionPreviewResponse> previewRes = api.previewSubstitution(
                input, "TB_EMPLOYEE", mappings, 10
        );
        assertTrue(previewRes.success());
        assertEquals("INSERT INTO TB_EMPLOYEE (EMP_NO, EMP_NAME, DEPT_CD, SALARY) VALUES (?, ?, ?, ?)",
                previewRes.data().preparedSqlTemplate());
        assertEquals("홍길동", previewRes.data().substitutedRows().get(0).mappedColumns().get("EMP_NAME"));
        assertEquals("GENERAL", previewRes.data().substitutedRows().get(2).mappedColumns().get("DEPT_CD"));
        String previewJson = previewRes.toJson();
        assertTrue(previewJson.contains("\"targetColumnName\":\"EMP_NO\""));
        assertTrue(previewJson.contains("\"status\":\"SUCCESS\""));

        // Step 3: DB Insert(Copy) 실행 및 결과 알림 확인
        DbConnectionConfig dbConfig = DbConnectionConfig.forJdbc(
                DbType.H2, h2JdbcUrl, "sa", "", TransactionPolicy.ALL_OR_NOTHING
        );
        AtomicInteger callbackSuccessCount = new AtomicInteger();
        ApiResponse<CopyExecutionResponse> copyRes = api.executeCopy(
                input, dbConfig, "TB_EMPLOYEE", mappings, new CopyResultListener() {
                    @Override
                    public void onRowSuccess(int rowNumber, int totalRows) {
                        callbackSuccessCount.incrementAndGet();
                    }
                }
        );

        assertTrue(copyRes.success());
        assertEquals(CopyStatus.SUCCESS, copyRes.status());
        assertEquals(NotificationLevel.SUCCESS, copyRes.notification().level());
        assertEquals(3, copyRes.data().insertedRows());
        assertEquals(0, copyRes.data().failedRows());
        assertEquals(3, callbackSuccessCount.get());

        // 실제 DB 적재 검증
        try (Connection conn = DriverManager.getConnection(h2JdbcUrl, "sa", "");
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT EMP_NO, EMP_NAME, DEPT_CD, SALARY FROM TB_EMPLOYEE ORDER BY EMP_NO")) {
            assertTrue(rs.next());
            assertEquals("E1001", rs.getString("EMP_NO"));
            assertEquals("홍길동", rs.getString("EMP_NAME"));
            assertEquals(5500, rs.getInt("SALARY"));
        }
    }

    @Test
    @DisplayName("2. Excel(.xlsx) 바이너리 스트림 파싱, 컬럼 치환 및 DB Insert 검증")
    void testExcelXlsxParsingAndCopy() throws Exception {
        byte[] xlsxBytes = createSampleXlsxBytes();
        SourceDataInput input = SourceDataInput.fromBytes(xlsxBytes, "sample.xlsx", true);

        List<ColumnMappingRule> mappings = List.of(
                ColumnMappingRule.byHeader("코드", "EMP_NO"),
                ColumnMappingRule.byHeader("성명", "EMP_NAME"),
                ColumnMappingRule.of(2, "소속", "DEPT_CD", ValueTransformRule.UPPER, "HQ"),
                ColumnMappingRule.byHeader("연봉", "SALARY")
        );

        DbConnectionConfig dbConfig = DbConnectionConfig.forJdbc(
                DbType.H2, h2JdbcUrl, "sa", "", TransactionPolicy.ALL_OR_NOTHING
        );
        ApiResponse<CopyExecutionResponse> copyRes = api.executeCopy(input, dbConfig, "TB_EMPLOYEE", mappings);

        assertTrue(copyRes.success());
        assertEquals(CopyStatus.SUCCESS, copyRes.status());
        assertEquals(2, copyRes.data().insertedRows());
    }

    @Test
    @DisplayName("3. 에러 알림 및 상세 리턴 검증 — CONTINUE_ON_ERROR 시 일부 행 실패(PARTIAL_SUCCESS) 및 행별 오류 스냅샷 반환")
    void testPartialFailureWithContinueOnError() {
        // E1001 중복 PK로 2행에서 제약조건 에러 유발
        String csvData = """
                사번,성명,부서,급여
                E1001,김유신,DEV,5000
                E1001,중복사번,OPS,4500
                E1002,세종대왕,RND,7000
                """;
        SourceDataInput input = SourceDataInput.fromText(csvData);
        List<ColumnMappingRule> mappings = List.of(
                ColumnMappingRule.byIndex(0, "EMP_NO"),
                ColumnMappingRule.byIndex(1, "EMP_NAME"),
                ColumnMappingRule.byIndex(2, "DEPT_CD"),
                ColumnMappingRule.byIndex(3, "SALARY")
        );

        DbConnectionConfig dbConfig = DbConnectionConfig.forJdbc(
                DbType.H2, h2JdbcUrl, "sa", "", TransactionPolicy.CONTINUE_ON_ERROR
        );

        List<CopyErrorDetail> listenerErrors = new ArrayList<>();
        ApiResponse<CopyExecutionResponse> res = api.executeCopy(
                input, dbConfig, "TB_EMPLOYEE", mappings, new CopyResultListener() {
                    @Override
                    public void onRowError(CopyErrorDetail errorDetail) {
                        listenerErrors.add(errorDetail);
                    }
                }
        );

        assertTrue(res.success());
        assertEquals(CopyStatus.PARTIAL_SUCCESS, res.status());
        assertEquals(NotificationLevel.WARNING, res.notification().level());
        assertEquals(3, res.data().totalRows());
        assertEquals(2, res.data().insertedRows());
        assertEquals(1, res.data().failedRows());
        assertEquals(1, res.data().errors().size());
        assertEquals(1, listenerErrors.size());

        CopyErrorDetail err = res.data().errors().get(0);
        assertEquals(2, err.rowNumber());
        assertEquals(ErrorCategory.DATA_CONSTRAINT_ERROR, err.errorCategory());
        assertEquals("중복사번", err.rowSnapshot().get("EMP_NAME"));
    }

    @Test
    @DisplayName("4. Access DB 접속 설정 생성 및 드라이버/접속 실패 시 DB_CONNECTION_ERROR 결과 알림 리턴 검증")
    void testAccessDbConfigAndConnectionErrorReturn() {
        DbConnectionConfig accessConfig = DbConnectionConfig.forAccessFile(
                "C:/non-existent-folder/sample.accdb", "admin", "1234", TransactionPolicy.ALL_OR_NOTHING
        );
        assertEquals(DbType.ACCESS, accessConfig.dbType());
        assertTrue(accessConfig.jdbcUrl().startsWith("jdbc:ucanaccess://"));

        SourceDataInput input = SourceDataInput.fromText("사번,이름,부서\nE01,홍길동,IT");
        List<ColumnMappingRule> mappings = List.of(
                ColumnMappingRule.byIndex(0, "EMP_NO"),
                ColumnMappingRule.byIndex(1, "EMP_NAME"),
                ColumnMappingRule.byIndex(2, "DEPT_CD")
        );

        ApiResponse<CopyExecutionResponse> res = api.executeCopy(input, accessConfig, "TB_EMPLOYEE", mappings);
        assertFalse(res.success());
        assertEquals(CopyStatus.FAILED, res.status());
        assertEquals(NotificationLevel.ERROR, res.notification().level());
        assertEquals(ErrorCategory.DB_CONNECTION_ERROR, res.data().errors().get(0).errorCategory());
    }

    private byte[] createSampleXlsxBytes() throws Exception {
        String sharedStringsXml = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <sst xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" count="7" uniqueCount="7">
                    <si><t>코드</t></si>
                    <si><t>성명</t></si>
                    <si><t>소속</t></si>
                    <si><t>연봉</t></si>
                    <si><t>X2001</t></si>
                    <si><t>장영실</t></si>
                    <si><t>ai_lab</t></si>
                    <si><t>X2002</t></si>
                    <si><t>정약용</t></si>
                </sst>
                """;
        String sheet1Xml = """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                    <sheetData>
                        <row r="1">
                            <c r="A1" t="s"><v>0</v></c>
                            <c r="B1" t="s"><v>1</v></c>
                            <c r="C1" t="s"><v>2</v></c>
                            <c r="D1" t="s"><v>3</v></c>
                        </row>
                        <row r="2">
                            <c r="A2" t="s"><v>4</v></c>
                            <c r="B2" t="s"><v>5</v></c>
                            <c r="C2" t="s"><v>6</v></c>
                            <c r="D2"><v>8500</v></c>
                        </row>
                        <row r="3">
                            <c r="A3" t="s"><v>7</v></c>
                            <c r="B3" t="s"><v>8</v></c>
                            <c r="C3" t="s"><v>6</v></c>
                            <c r="D3"><v>9200</v></c>
                        </row>
                    </sheetData>
                </worksheet>
                """;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            zos.putNextEntry(new ZipEntry("xl/sharedStrings.xml"));
            zos.write(sharedStringsXml.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
            zos.write(sheet1Xml.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return baos.toByteArray();
    }
}
