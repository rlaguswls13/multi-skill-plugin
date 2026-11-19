package com.msp.sqlupsert;

/**
 * Supported database SQL dialects for UPSERT generation.
 */
public enum DatabaseDialect {
    POSTGRESQL,
    SQLITE,
    MYSQL,
    MARIADB,
    ORACLE,
    H2,
    SQL_SERVER
}
