package com.msp.tablecopier.db;

/**
 * 지원하는 대상 데이터베이스 유형
 */
public enum DbType {
    /** Microsoft Access (.accdb, .mdb) - UCanAccess JDBC 드라이버 기본 연동 */
    ACCESS("net.ucanaccess.jdbc.UcanaccessDriver"),
    MYSQL("com.mysql.cj.jdbc.Driver"),
    MARIADB("org.mariadb.jdbc.Driver"),
    POSTGRESQL("org.postgresql.Driver"),
    ORACLE("oracle.jdbc.OracleDriver"),
    MSSQL("com.microsoft.sqlserver.jdbc.SQLServerDriver"),
    SQLITE("org.sqlite.JDBC"),
    H2("org.h2.Driver"),
    CUSTOM(null);

    private final String defaultDriverClassName;

    DbType(String defaultDriverClassName) {
        this.defaultDriverClassName = defaultDriverClassName;
    }

    public String defaultDriverClassName() {
        return defaultDriverClassName;
    }
}
