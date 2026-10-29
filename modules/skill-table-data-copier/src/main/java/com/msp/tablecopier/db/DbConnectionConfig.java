package com.msp.tablecopier.db;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * [고려사항 (1)] MS Access DB(.accdb, .mdb) 및 표준 JDBC 데이터베이스 접속 설정 객체.
 *
 * @param dbType            DB 종류 (ACCESS, MYSQL, POSTGRESQL, H2 등)
 * @param jdbcUrl           JDBC 접속 URL (Access 파일 경로만 지정 시 자동 생성)
 * @param accessFilePath    MS Access DB 파일(.accdb, .mdb) 경로 (선택)
 * @param username          DB 접속 계정 (Access 기본값은 빈 문자열)
 * @param password          DB 접속 암호 (Access 암호 설정 시 입력)
 * @param driverClassName   명시적 JDBC 드라이버 클래스명 (null일 경우 dbType 기본값 사용)
 * @param connectTimeoutMs  접속 타임아웃 (밀리초, 기본 5000ms)
 * @param batchSize         배치 Insert 크기 (기본 500)
 * @param transactionPolicy 에러 발생 시 트랜잭션 정책 (ALL_OR_NOTHING / CONTINUE_ON_ERROR)
 * @param customProperties  추가 JDBC 드라이버 프로퍼티
 */
public record DbConnectionConfig(
        DbType dbType,
        String jdbcUrl,
        String accessFilePath,
        String username,
        String password,
        String driverClassName,
        int connectTimeoutMs,
        int batchSize,
        TransactionPolicy transactionPolicy,
        Map<String, String> customProperties
) {
    public DbConnectionConfig {
        if (dbType == null) {
            dbType = DbType.CUSTOM;
        }
        if ((jdbcUrl == null || jdbcUrl.isBlank()) && dbType == DbType.ACCESS && accessFilePath != null && !accessFilePath.isBlank()) {
            String normalizedPath = accessFilePath.trim().replace('\\', '/');
            jdbcUrl = "jdbc:ucanaccess://" + normalizedPath + ";memory=false";
        }
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new IllegalArgumentException("DB 접속을 위한 jdbcUrl 또는 accessFilePath가 필요합니다.");
        }
        if (driverClassName == null || driverClassName.isBlank()) {
            driverClassName = dbType.defaultDriverClassName();
        }
        if (connectTimeoutMs <= 0) {
            connectTimeoutMs = 5000;
        }
        if (batchSize <= 0) {
            batchSize = 500;
        }
        if (transactionPolicy == null) {
            transactionPolicy = TransactionPolicy.ALL_OR_NOTHING;
        }
        customProperties = customProperties == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(customProperties));
    }

    /**
     * MS Access (.accdb / .mdb) 파일 경로로 접속 설정을 생성합니다.
     */
    public static DbConnectionConfig forAccessFile(String accessFilePath) {
        return forAccessFile(accessFilePath, "", "", TransactionPolicy.ALL_OR_NOTHING);
    }

    /**
     * MS Access (.accdb / .mdb) 파일 경로 및 계정/암호, 트랜잭션 정책으로 접속 설정을 생성합니다.
     */
    public static DbConnectionConfig forAccessFile(
            String accessFilePath,
            String username,
            String password,
            TransactionPolicy transactionPolicy
    ) {
        if (accessFilePath == null || accessFilePath.isBlank()) {
            throw new IllegalArgumentException("MS Access DB 파일 경로(accessFilePath)가 비어 있습니다.");
        }
        String normalizedPath = Path.of(accessFilePath.trim()).toAbsolutePath().toString().replace('\\', '/');
        String url = "jdbc:ucanaccess://" + normalizedPath + ";memory=false";
        return new DbConnectionConfig(
                DbType.ACCESS,
                url,
                normalizedPath,
                username != null ? username : "",
                password != null ? password : "",
                DbType.ACCESS.defaultDriverClassName(),
                5000,
                500,
                transactionPolicy,
                Collections.emptyMap()
        );
    }

    /**
     * 일반 JDBC URL을 사용하는 DB 접속 설정을 생성합니다.
     */
    public static DbConnectionConfig forJdbc(
            DbType dbType,
            String jdbcUrl,
            String username,
            String password,
            TransactionPolicy transactionPolicy
    ) {
        return new DbConnectionConfig(
                dbType,
                jdbcUrl,
                null,
                username,
                password,
                dbType != null ? dbType.defaultDriverClassName() : null,
                5000,
                500,
                transactionPolicy,
                Collections.emptyMap()
        );
    }

    /**
     * 설정된 정보를 기반으로 실제 JDBC {@link Connection}을 획득합니다.
     */
    public Connection openConnection() throws SQLException {
        if (driverClassName != null && !driverClassName.isBlank()) {
            try {
                Class.forName(driverClassName);
            } catch (ClassNotFoundException e) {
                throw new SQLException(
                        "JDBC 드라이버 클래스를 찾을 수 없습니다 (" + driverClassName + "). "
                                + (dbType == DbType.ACCESS
                                ? "MS Access 연동 시 런타임 클래스패스에 UCanAccess 드라이버를 추가해주세요."
                                : "런타임 클래스패스에 해당 DB 드라이버 JAR를 추가해주세요."),
                        "08001",
                        e
                );
            }
        }
        DriverManager.setLoginTimeout(Math.max(1, connectTimeoutMs / 1000));
        Properties props = new Properties();
        if (username != null && !username.isEmpty()) {
            props.setProperty("user", username);
        }
        if (password != null && !password.isEmpty()) {
            props.setProperty("password", password);
        }
        customProperties.forEach(props::setProperty);

        if (props.isEmpty()) {
            return DriverManager.getConnection(jdbcUrl);
        }
        return DriverManager.getConnection(jdbcUrl, props);
    }
}
