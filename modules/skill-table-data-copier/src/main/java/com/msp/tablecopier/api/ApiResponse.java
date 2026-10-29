package com.msp.tablecopier.api;

import com.msp.tablecopier.result.CopyStatus;
import com.msp.tablecopier.result.ResultNotification;

/**
 * [고려사항 (2) & (3)] 화면 표기 및 결과 알림/에러 리턴을 API 방식으로 통일한 표준 응답 엔벨로프.
 *
 * @param success      성공 여부 (SUCCESS 또는 PARTIAL_SUCCESS 시 true, FAILED 시 false)
 * @param status       최종 처리 상태 (SUCCESS, PARTIAL_SUCCESS, FAILED)
 * @param notification 화면 토스트/알림창에 바로 표기할 수 있는 표준 알림 객체
 * @param data         단계별 응답 페이로드 (SourceInspectionResponse / SubstitutionPreviewResponse / CopyExecutionResponse)
 */
public record ApiResponse<T>(
        boolean success,
        CopyStatus status,
        ResultNotification notification,
        T data
) {
    /**
     * 외부 의존성 없이 현재 API 응답 객체를 JSON 문자열로 직렬화하여 반환합니다.
     */
    public String toJson() {
        return JsonResponseFormatter.toJson(this);
    }
}
