"""배치 트리거 DAG 들이 공유하는 태스크 본문(#390 → #469 에서 분리).

notification_cleanup·room_reconcile 두 DAG 의 start_job/wait_job 은 "어떤 잡을 어떤 bucket 으로" 만 다르고 HTTP 호출·응답 해석은
같다. 그 공통부를 여기 둔다. Airflow 런타임(Connection·PokeReturnValue)에 의존하므로 순수 함수 모듈(haruharu_batch)과 분리했다.
DAG 파일이 아니므로 .airflowignore 로 dag-processor 파싱 대상에서 뺀다.
"""
from __future__ import annotations

import logging

import requests
from airflow.sdk import Connection, PokeReturnValue
from airflow.sdk.exceptions import AirflowException, AirflowFailException

from haruharu_batch import (
    TOKEN_HEADER,
    build_start_request,
    build_status_url,
    classify_status_http_failure,
    interpret_start_response,
    interpret_status_response,
)

log = logging.getLogger(__name__)  # Task SDK 의 RuntimeTaskInstance 에는 .log 가 없다 — 모듈 로거가 태스크 로그로 흘러간다

APP_CONN_ID = "haruharu_app"          # AIRFLOW_CONN_HARUHARU_APP 환경변수로 정의(docker-compose.yaml)
# 슬롯 1 짜리 Pool — 백필·수동·스케줄 실행이 겹쳐도 start_job 은 한 번에 하나만 돈다. 모든 배치 트리거 DAG 가 공유한다.
# max_active_runs=1 은 스케줄 실행에만 적용되고 Airflow 3 백필 동시성(기본 10)은 제어하지 못한다. Pool 은 그와 무관하게 강제된다.
# 여러 잡이 같은 Pool 을 쓰면 서로의 launch 도 줄 세워져 JobRepository(SERIALIZABLE) 직렬화 충돌이 줄어든다.
# Pool 은 airflow-init(compose) / 운영 부트스트랩에서 `airflow pools set` 으로 만든다 — 없으면 태스크가 영원히 scheduled 에 머문다.
TRIGGER_POOL = "haruharu_batch_trigger"
HTTP_TIMEOUT_SECONDS = 30   # 기본값. 주기가 짧은 DAG 는 run 예산에 맞춰 더 짧게 넘긴다(room_reconcile)


def app_connection() -> tuple[str, str]:
    conn = Connection.get(APP_CONN_ID)
    host = conn.host or ""
    base_url = host if host.startswith("http") else f"http://{host}"
    if conn.port and f":{conn.port}" not in base_url:
        base_url = f"{base_url}:{conn.port}"
    return base_url, conn.password or ""


def start_batch_job(job_name: str, bucket: str, timeout_seconds: float = HTTP_TIMEOUT_SECONDS) -> int:
    """잡 시작. execution_id 를 돌려준다(XCom 으로 wait_job 에 넘어간다). 멱등 재호출(200)도 성공이다."""
    base_url, token = app_connection()
    url, headers, body = build_start_request(base_url, token, job_name, bucket)
    try:
        response = requests.post(url, headers=headers, json=body, timeout=timeout_seconds)
    except requests.RequestException as e:
        # 앱이 내려가 있거나 네트워크 오류 — 재시도 대상(지수 백오프). 앱이 올라오면 같은 bucket 으로 멱등 재호출된다.
        raise AirflowException(f"배치 트리거 API 연결 실패: {e}") from e
    payload = _json_or_empty(response)
    execution_id = interpret_start_response(response.status_code, payload)
    log.info("배치 시작 응답: job=%s http=%s bucket=%s execution_id=%s outcome=%s",
             job_name, response.status_code, bucket, execution_id, (payload.get("data") or {}).get("outcome"))
    return execution_id


def poke_batch_execution(execution_id: int, timeout_seconds: float = HTTP_TIMEOUT_SECONDS) -> PokeReturnValue:
    """COMPLETED 까지 폴링하는 센서 본문. FAILED 등 실패 종료면 즉시 실패(재시도 없음 — 잡은 이미 끝났다).

    조회 자체의 일시 장애(연결 오류·5xx·429)는 실패가 아니라 '아직 모름' 이다 — 다음 poke 로 넘기고 센서 timeout 이 상한이다.
    """
    base_url, token = app_connection()
    try:
        response = requests.get(
            build_status_url(base_url, execution_id),
            headers={TOKEN_HEADER: token, "Accept": "application/json"},
            timeout=timeout_seconds,
        )
    except requests.RequestException as e:
        # 일시적 네트워크 장애로 오케스트레이션을 실패시키지 않는다 — 잡은 앱에서 계속 돌고 있을 수 있다. 다음 poke 에서 다시 본다.
        log.warning("상태 조회 연결 실패(다음 poke 에서 재시도): %s", e)
        return PokeReturnValue(is_done=False)
    if response.status_code != 200:
        if classify_status_http_failure(response.status_code) == "fail":
            raise AirflowFailException(f"상태 조회 거부(재시도 무의미): http={response.status_code}")
        log.warning("상태 조회 일시 실패(다음 poke 에서 재시도): http=%s", response.status_code)
        return PokeReturnValue(is_done=False)
    try:
        body = response.json()
    except ValueError:
        log.warning("상태 조회 본문이 JSON 이 아니다(다음 poke 에서 재시도)")
        return PokeReturnValue(is_done=False)
    decision = interpret_status_response(body)
    return PokeReturnValue(is_done=decision.is_done, xcom_value=decision.status)


def _json_or_empty(response: requests.Response) -> dict:
    try:
        return response.json() or {}
    except ValueError:
        return {}
