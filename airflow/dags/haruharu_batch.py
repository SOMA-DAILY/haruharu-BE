"""하루하루 내부 배치 트리거 API(#389) 클라이언트의 순수 함수(#390).

Airflow 런타임에 의존하지 않는 부분만 여기 둔다 — bucket 변환, 요청 구성, 응답 해석. DAG 파일은 이 함수들을
HTTP 호출·XCom·Sensor 로 감쌀 뿐이다. 그래서 컨텍스트 없이 pytest 로 검증할 수 있다.

계약(#389): POST /internal/batch/jobs/{job}/executions {bucket} →
  202 STARTED/RESTARTED · 200 ALREADY_COMPLETED(멱등) · 409 실행 중/레이스(재시도) · 400/401/404(설정 오류, 재시도 무의미)
  GET /internal/batch/executions/{id} → status(COMPLETED/FAILED/STARTED/...)
"""
from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from typing import Any
from zoneinfo import ZoneInfo

from airflow.sdk.exceptions import AirflowException, AirflowFailException

JOB_NAME = "notificationCleanupJob"
TOKEN_HEADER = "X-Internal-Token"
KST = ZoneInfo("Asia/Seoul")

# Spring Batch BatchStatus 기준. 종료 상태 중 COMPLETED 만 성공이다.
RUNNING_STATUSES = {"STARTING", "STARTED", "STOPPING"}
TERMINAL_FAILURE_STATUSES = {"FAILED", "STOPPED", "ABANDONED", "UNKNOWN"}


def bucket_for(run_after: datetime | None, data_interval_end: datetime | None = None,
               logical_date: datetime | None = None) -> str:
    """이 DAG Run 이 담당하는 하루 = KST 날짜 문자열(yyyy-MM-dd). Spring Batch 식별 파라미터 ``bucket`` 이 된다.

    Airflow 3 에서 스케줄 실행은 ``run_after`` = 데이터 구간 끝(= 04:00 KST 그날), 수동 실행은 트리거 시각이라
    둘 다 "실제로 도는 날"이 된다 — 앱의 @Scheduled 런처가 쓰던 ``LocalDate.now(Asia/Seoul)`` 와 같은 좌표계.
    ``logical_date`` 는 수동 실행에서 None 일 수 있어(AIP-83) 마지막 폴백으로만 쓴다.
    """
    for candidate in (run_after, data_interval_end, logical_date):
        if candidate is not None:
            return candidate.astimezone(KST).date().isoformat()
    raise AirflowFailException("bucket 을 정할 날짜가 없다 (run_after/data_interval_end/logical_date 모두 None)")


def build_start_request(base_url: str, token: str, job_name: str, bucket: str) -> tuple[str, dict[str, str], dict[str, Any]]:
    if not token or not token.strip():
        # app 은 빈 토큰을 전부 401 로 거부한다(fail-closed). 재시도해도 같으니 즉시 실패시킨다.
        raise AirflowFailException("INTERNAL_BATCH_TOKEN 이 비어 있다 — Connection haruharu_app 의 password 를 설정하라")
    url = f"{base_url.rstrip('/')}/internal/batch/jobs/{job_name}/executions"
    headers = {TOKEN_HEADER: token, "Content-Type": "application/json", "Accept": "application/json"}
    return url, headers, {"bucket": bucket}


def build_status_url(base_url: str, execution_id: int) -> str:
    return f"{base_url.rstrip('/')}/internal/batch/executions/{execution_id}"


def interpret_start_response(status_code: int, body: dict[str, Any]) -> int:
    """시작 응답 → execution_id. 상태 코드에 따라 '재시도 가능(AirflowException)' 과 '즉시 실패(AirflowFailException)' 를 가른다.

    - 202/200: 새 실행·재시작·이미 완료(멱등) 모두 성공. 이후는 폴링이 판단한다.
    - 409: 실행 중이거나 동시 launch 레이스 — 잠시 후 재시도하면 200/202 로 수렴한다.
    - 5xx: 서버 귀책 — 재시도.
    - 400/401/404: 요청·설정 오류 — 재시도해도 같은 결과라 바로 실패.
    """
    if status_code in (200, 202):
        try:
            return int(body["data"]["execution_id"])
        except (KeyError, TypeError, ValueError) as e:
            # 서버는 잡을 시작했는데 응답이 잘렸을 수 있다 — 멱등 API 라 재호출하면 ALREADY_COMPLETED 로 execution_id 를 복구한다.
            raise AirflowException(f"시작 응답(http={status_code})에 execution_id 가 없다 — 재시도: {str(body)[:200]}") from e
    error = (body or {}).get("error") or {}
    detail = f"{status_code} {error.get('code')} {error.get('message')}"
    if status_code == 409 or status_code >= 500:
        raise AirflowException(f"배치 시작 재시도 필요: {detail}")
    raise AirflowFailException(f"배치 시작 거부(재시도 무의미): {detail}")


# 상태 조회(GET) 의 HTTP 실패 분류. 일시 장애는 '다음 poke' 로 넘기고, 영구 오류만 즉시 실패시킨다.
STATUS_PERMANENT_HTTP = {400, 401, 403, 404}


def classify_status_http_failure(status_code: int) -> str:
    """'poke_again'(일시: 5xx·408·429·기타) 또는 'fail'(영구: 400/401/403/404). 200 은 호출자가 먼저 처리한다."""
    return "fail" if status_code in STATUS_PERMANENT_HTTP else "poke_again"


@dataclass(frozen=True)
class PollDecision:
    is_done: bool
    status: str


def interpret_status_response(body: dict[str, Any]) -> PollDecision:
    """상태 응답 → 센서 판정. COMPLETED 면 완료, 실행 중이면 계속 폴링, 실패 종료면 즉시 실패."""
    data = (body or {}).get("data") or {}
    if "status" not in data:
        # 200 인데 본문이 깨졌거나 잘렸다 — 일시 현상으로 보고 다음 poke 에서 다시 읽는다 (UNKNOWN 은 명시된 종료 상태일 때만 실패).
        return PollDecision(is_done=False, status="MALFORMED")
    status = str(data["status"])
    if status == "COMPLETED":
        return PollDecision(is_done=True, status=status)
    if status in RUNNING_STATUSES:
        return PollDecision(is_done=False, status=status)
    if status in TERMINAL_FAILURE_STATUSES:
        raise AirflowFailException(
            f"배치 실행이 {status} 로 끝났다: execution_id={data.get('execution_id')} exit={data.get('exit_code')} "
            f"message={(data.get('exit_message') or '')[:300]}")
    raise AirflowFailException(f"알 수 없는 배치 상태: {status}")
