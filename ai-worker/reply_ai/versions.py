"""버전 레지스트리와 활성 버전 포인터(13장). 활성 변경은 revision compare-and-swap 으로만 한다."""
from __future__ import annotations

from typing import Any

from . import errors
from .db import jsonb
from .resources import PROMPT_KEYS, default_prompts, package_config
from .util import sha256_json

SCOPE = "online"


class CasConflict(Exception):
    """활성 버전이 기대와 달라 바꾸지 않았다(덮어쓰기 금지)."""


def preprocess_version(config: dict[str, Any], prompts: dict[str, str]) -> str:
    """분석 재사용 판단 키: 분석 프롬프트·전처리 설정·모델이 같으면 같은 값."""
    return sha256_json({"analysis_prompt": prompts["analysis"], "preprocess": config["preprocess"],
                        "model": config["model"]["model_id"], "reasoning_effort": config["model"]["reasoning_effort"]})


def build_version(label: str, config: dict[str, Any], prompts: dict[str, str], gallery_snapshot_id: str,
                  parent: str | None = None, note: str | None = None, status: str = "CANDIDATE") -> dict[str, Any]:
    missing = [k for k in PROMPT_KEYS if not prompts.get(k)]
    if missing:
        raise ValueError(f"prompts missing: {missing}")
    full = sha256_json({"config": config, "prompts": prompts, "gallery_snapshot_id": gallery_snapshot_id})
    return {"version_id": f"{label}-{full[:12]}", "parent_version_id": parent, "config_json": config, "prompts_json": prompts,
            "full_config_sha": full, "prompts_sha": sha256_json(prompts),
            "preprocess_version": preprocess_version(config, prompts), "gallery_snapshot_id": gallery_snapshot_id,
            "status": status, "change_note": note}


def insert_version(conn, row: dict[str, Any]) -> None:
    existing = conn.execute("SELECT full_config_sha FROM ai_reply.version_registry WHERE version_id = %s",
                            (row["version_id"],)).fetchone()
    if existing:
        if existing["full_config_sha"] != row["full_config_sha"]:
            raise ValueError(f"version {row['version_id']} exists with different content")
        return
    conn.execute(
        """INSERT INTO ai_reply.version_registry (version_id, parent_version_id, config_json, prompts_json, full_config_sha,
               prompts_sha, preprocess_version, gallery_snapshot_id, status, change_note)
           VALUES (%(version_id)s, %(parent_version_id)s, %(config)s, %(prompts)s, %(full_config_sha)s, %(prompts_sha)s,
                   %(preprocess_version)s, %(gallery_snapshot_id)s, %(status)s, %(change_note)s)""",
        {**row, "config": jsonb(row["config_json"]), "prompts": jsonb(row["prompts_json"])})


def ensure_empty_gallery(conn) -> str:
    snapshot_id = "gallery-empty-" + sha256_json([])[:12]
    conn.execute(
        """INSERT INTO ai_reply.gallery_snapshot (id, reference_video_ids, content_sha, status, note)
           VALUES (%s, '[]'::jsonb, %s, 'ACTIVE', '참고 사례 없음(운영 시작값)') ON CONFLICT DO NOTHING""",
        (snapshot_id, sha256_json([])))
    return snapshot_id


def active(conn) -> dict[str, Any] | None:
    return conn.execute(
        """SELECT a.version_id, a.revision, v.* FROM ai_reply.active_version a
             JOIN ai_reply.version_registry v ON v.version_id = a.version_id WHERE a.scope = %s""", (SCOPE,)).fetchone()


def load(conn, version_id: str) -> dict[str, Any]:
    row = conn.execute("SELECT * FROM ai_reply.version_registry WHERE version_id = %s", (version_id,)).fetchone()
    if row is None:
        raise errors.JobFailure(errors.INPUT_MISSING, f"version {version_id}")
    return row


def for_preprocess(conn, preprocess: str) -> dict[str, Any]:
    """분석 입력의 preprocess_version 에 맞는 버전(활성 우선, 없으면 가장 최근)."""
    current = active(conn)
    if current and current["preprocess_version"] == preprocess:
        return current
    row = conn.execute("""SELECT * FROM ai_reply.version_registry WHERE preprocess_version = %s
                          ORDER BY created_at DESC LIMIT 1""", (preprocess,)).fetchone()
    if row is None:
        raise errors.JobFailure(errors.INPUT_MISSING, "no version for preprocess_version")
    return row


def init_version(conn, gallery_snapshot_id: str | None = None, note: str | None = None) -> dict[str, Any]:
    """첫 버전(패키지 프롬프트·설정)을 등록하고 활성 포인터가 없을 때만 만든다(멱등)."""
    current = active(conn)
    if current:
        return {"created": False, "version_id": current["version_id"], "revision": current["revision"],
                "gallery_snapshot_id": current["gallery_snapshot_id"]}
    with conn.transaction():
        gallery = gallery_snapshot_id or ensure_empty_gallery(conn)
        if conn.execute("SELECT 1 FROM ai_reply.gallery_snapshot WHERE id = %s", (gallery,)).fetchone() is None:
            raise ValueError(f"gallery snapshot {gallery} not found")
        row = build_version("v1", package_config(), default_prompts(), gallery, note=note or "명세 v1 개발 패키지", status="ACTIVE")
        insert_version(conn, row)
        conn.execute(
            """INSERT INTO ai_reply.active_version (scope, version_id, revision, change_reason)
               VALUES (%s, %s, 1, 'init-version') ON CONFLICT DO NOTHING""", (SCOPE, row["version_id"]))
        log_activation(conn, None, row["version_id"], 1, "init-version", None)
    return {"created": True, "version_id": row["version_id"], "revision": 1, "gallery_snapshot_id": gallery}


def log_activation(conn, from_version: str | None, to_version: str, revision: int, reason: str, batch_run_id: int | None) -> None:
    conn.execute(
        """INSERT INTO ai_reply.version_activation_log (scope, from_version_id, to_version_id, revision, reason, batch_run_id)
           VALUES (%s, %s, %s, %s, %s, %s)""", (SCOPE, from_version, to_version, revision, reason[:200], batch_run_id))


def activate(conn, version_id: str, expected_version: str, expected_revision: int, reason: str,
             batch_run_id: int | None = None) -> int:
    """기대한 활성 버전·revision 일 때만 바꾼다. 새 revision 을 돌려준다."""
    with conn.transaction():
        row = conn.execute(
            """UPDATE ai_reply.active_version
                  SET version_id = %s, revision = revision + 1, previous_version_id = version_id, change_reason = %s,
                      updated_at = now()
                WHERE scope = %s AND version_id = %s AND revision = %s RETURNING revision""",
            (version_id, reason[:200], SCOPE, expected_version, expected_revision)).fetchone()
        if row is None:
            raise CasConflict(f"active version is not {expected_version}@{expected_revision}")
        conn.execute("UPDATE ai_reply.version_registry SET status = 'RETIRED' WHERE version_id = %s", (expected_version,))
        conn.execute("UPDATE ai_reply.version_registry SET status = 'ACTIVE' WHERE version_id = %s", (version_id,))
        log_activation(conn, expected_version, version_id, row["revision"], reason, batch_run_id)
    return row["revision"]


def rollback(conn, target_version: str, reason: str) -> dict[str, Any]:
    """예전에 실제로 활성화됐던 버전으로만 되돌린다. 감사 기록을 남긴다."""
    current = active(conn)
    if current is None:
        raise ValueError("no active version")
    if current["version_id"] == target_version:
        raise ValueError("target is already active")
    seen = conn.execute("""SELECT 1 FROM ai_reply.version_activation_log WHERE scope = %s AND to_version_id = %s LIMIT 1""",
                        (SCOPE, target_version)).fetchone()
    if seen is None:
        raise ValueError("target was never an active version")
    revision = activate(conn, target_version, current["version_id"], current["revision"], "rollback: " + reason)
    return {"from": current["version_id"], "to": target_version, "revision": revision}


def derive(conn, parent: dict[str, Any], label: str, *, prompts: dict[str, str] | None = None,
           gallery_snapshot_id: str | None = None, note: str | None = None) -> dict[str, Any]:
    """부모 버전에서 프롬프트 또는 참고 사례 묶음 하나만 바꾼 새 후보 버전(둘을 동시에 바꾸지 않는다)."""
    if prompts is not None and gallery_snapshot_id is not None:
        raise ValueError("do not change prompts and gallery at the same time")
    row = build_version(label, parent["config_json"], prompts or parent["prompts_json"],
                        gallery_snapshot_id or parent["gallery_snapshot_id"], parent=parent["version_id"], note=note)
    insert_version(conn, row)
    return row


def use_package(conn, reason: str) -> dict[str, Any]:
    """패키지에 들어 있는 지금 지시문·설정(resources/prompts·config.json)으로 새 버전을 만들어 CAS 로 활성화한다.

    참고 사례 묶음(gallery)은 활성 버전 것을 그대로 쓴다. 활성 버전 내용이 이미 같으면 바꾸지 않는다(멱등).
    피드백 배치가 고친 지시문이 활성 버전에 있었다면 패키지 지시문으로 바뀐다(이전 버전은 rollback 으로 되돌릴 수 있다).
    """
    current = active(conn)
    if current is None:
        raise ValueError("no active version: run init-version first")
    row = build_version("pkg", package_config(), default_prompts(), current["gallery_snapshot_id"],
                        parent=current["version_id"], note=reason)
    if row["full_config_sha"] == current["full_config_sha"]:
        return {"changed": False, "version_id": current["version_id"], "revision": current["revision"]}
    insert_version(conn, row)
    revision = activate(conn, row["version_id"], current["version_id"], current["revision"], "use-package: " + reason)
    return {"changed": True, "from": current["version_id"], "version_id": row["version_id"], "revision": revision}
