package com.msp.tablecopier.result;

import java.time.Instant;

/**
 * [고려사항 (3)] 화면 알림창(Toast / Modal / Alert)에 즉시 표기할 수 있는 표준 결과 알림 객체.
 *
 * @param level     알림 심각도 (SUCCESS, WARNING, ERROR)
 * @param code      표준 결과/에러 코드 (예: COPY_COMPLETED, COPY_PARTIAL_WARNING, DB_CONNECTION_FAILED)
 * @param title     알림 제목 (예: "데이터 복사 완료", "일부 행 복사 실패")
 * @param message   사용자에게 표시할 상세 안내 메시지
 * @param timestamp 알림 생성 시각 (ISO-8601)
 */
public record ResultNotification(
        NotificationLevel level,
        String code,
        String title,
        String message,
        String timestamp
) {
    public enum NotificationLevel {
        SUCCESS,
        WARNING,
        ERROR
    }

    public static ResultNotification success(String code, String title, String message) {
        return new ResultNotification(NotificationLevel.SUCCESS, code, title, message, Instant.now().toString());
    }

    public static ResultNotification warning(String code, String title, String message) {
        return new ResultNotification(NotificationLevel.WARNING, code, title, message, Instant.now().toString());
    }

    public static ResultNotification error(String code, String title, String message) {
        return new ResultNotification(NotificationLevel.ERROR, code, title, message, Instant.now().toString());
    }
}
