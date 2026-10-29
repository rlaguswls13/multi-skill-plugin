package com.msp.tablecopier.parser;

/**
 * 입력 테이블 데이터의 원본 포맷 유형
 */
public enum TabularSourceFormat {
    /**
     * Excel OpenXML (.xlsx) 포맷
     */
    EXCEL_XLSX,
    /**
     * 콤마(,) 구분 텍스트/CSV
     */
    CSV,
    /**
     * 탭(\t) 구분 텍스트/TSV (엑셀 복사-붙여넣기 텍스트 포함)
     */
    TXT_TSV,
    /**
     * 파이프(|) 또는 사용자 지정 구분자 텍스트
     */
    TXT_CUSTOM,
    /**
     * 파일 확장자 또는 본문 분석을 통한 자동 감지
     */
    AUTO
}
