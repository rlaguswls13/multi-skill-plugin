package com.msp.tablecopier.result;

/**
 * 동기 API 리턴 외에 실시간 진행률·행 단위 에러·최종 완료 이벤트를 수신할 수 있는 콜백 인터페이스.
 */
public interface CopyResultListener {

    default void onRowSuccess(int rowNumber, int totalRows) {
    }

    default void onRowError(CopyErrorDetail errorDetail) {
    }

    default void onComplete(CopyStatus status, ResultNotification notification, CopyExecutionResponse response) {
    }
}
