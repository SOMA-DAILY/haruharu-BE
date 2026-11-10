"""피드백 배치(12·13장): 조건, 판정 분리, 사례 축적, 개선안, 동결 검증, CAS 적용·보류, 롤백."""
import uuid
from datetime import timedelta

import pytest

from reply_ai import errors, versions
from reply_ai.config import Settings
from reply_ai.db import connect, jsonb
from reply_ai.feedback.engine import Engine, run_scheduled, schedule, split_of
from reply_ai.provider import ScriptedProvider
from reply_ai.resources import GATE_BOOLEANS
from reply_ai.util import now_utc, sha256_json
from reply_ai.worker import Worker

from .conftest import T0, PinnedFakeEmbedder, seed_analysis_input

pytestmark = pytest.mark.pg
PATCH = "시청자가 쉽게 답할 수 있는 구체적 질문 후보를 하나 이상 포함한다."


def rooms_by_split():
    found = {"dev": [], "regression": []}
    for room in range(1, 200):
        found[split_of(room)].append(room)
    return found


def make_episode(conn, *, room_id, video_id, viewer_id=3, reply="무슨 과제예요?", provenance="QA", closed_ago_hours=1,
                 attribution="explicit", outcome="REPLIED"):
    source = seed_analysis_input(conn, video_id=video_id, room_id=room_id, caption=f"과제 마감 {video_id}")
    output = {"current_visual_description": "책상 위 노트북", "contextual_description": "책상 위 노트북", "history_requirement": "not_needed",
              "history_used": False, "history_note": "", "visual_facts": [], "context_links": []}
    snapshot = conn.execute(
        """INSERT INTO ai_reply.analysis_snapshot (analysis_input_id, analysis_key, video_id, scope_hash, caption_revision, media_revision,
               history_snapshot_hash, preprocess_version, version_id, cutoff_at, input_sha, output_sha, frames, output_json,
               current_visual_description, contextual_description, history_input_status, history_requirement, history_used, model, status)
           VALUES (%s, %s, %s, 's', 'c', 'm', 'h', 'p', 'v', %s, 'i', 'o', '[]'::jsonb, %s, '책상 위 노트북', '책상 위 노트북',
                   'NO_RECORDS', 'not_needed', false, 'fake', 'READY') RETURNING id""",
        (source["id"], uuid.uuid4().hex, video_id, T0, jsonb(output))).fetchone()
    kept = [{"candidate_id": "G1", "category": "support", "title": "마감을 응원해 볼까요?", "example": "화이팅!", "current_anchor": "과제",
             "reference_ids": [], "personalization": {"kind": "none", "source_quotes": [], "actor_ids": [], "delta": ""}}]
    attempt = conn.execute(
        """INSERT INTO ai_reply.generation_attempt (attempt_key, stage, analysis_id, video_id, version_id, gallery_snapshot_id,
               retrieval, candidates, status) VALUES (%s, 'GENERIC', %s, %s, 'v', 'g', %s, %s, 'SUCCEEDED') RETURNING id""",
        (uuid.uuid4().hex, snapshot["id"], video_id, jsonb({"ranked": [], "selected_replies": []}), jsonb({"kept": kept}))).fetchone()
    set_id = str(uuid.uuid4())
    card = {**kept[0], "card_id": str(uuid.uuid4()), "quality": 3, "redundancy_group": "g1"}
    conn.execute(
        """INSERT INTO ai_reply.recommendation_set (set_id, attempt_id, kind, analysis_id, video_id, version_id, gallery_snapshot_id,
               status, cards, internal_cards, expires_at) VALUES (%s, %s, 'GENERIC', %s, %s, 'v', 'g', 'READY', %s, %s, %s)""",
        (set_id, attempt["id"], snapshot["id"], video_id, jsonb([{k: card[k] for k in ("card_id", "category", "title")}]),
         jsonb([card]), now_utc() + timedelta(hours=24)))
    closed = now_utc() - timedelta(hours=closed_ago_hours)
    episode = conn.execute(
        """INSERT INTO ai_reply.reply_episode (session_id, set_id, request_id, viewer_id, video_id, room_id, first_impression_at,
               last_impression_at, actual_reply_message_id, actual_reply_at, attribution, outcome, closed_at, provenance)
           VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s) RETURNING id""",
        (str(uuid.uuid4()), set_id, str(uuid.uuid4()), viewer_id, video_id, room_id, closed - timedelta(minutes=5),
         closed - timedelta(minutes=5), video_id * 10, closed, attribution, outcome, closed, provenance)).fetchone()
    if outcome == "REPLIED":
        conn.execute("""INSERT INTO ai_reply.episode_reply_snapshot (episode_id, message_id, reply_text, reply_sha, provenance)
                        VALUES (%s, %s, %s, %s, %s)""", (episode["id"], video_id * 10, reply, sha256_json(reply), provenance))
    return episode["id"]


def appropriate(verdict="appropriate", confidence="high"):
    return {"verdict": verdict, "confidence": confidence, "stable_meaning": True, "reason": "판정"}


def direction(raw="absent", final="absent", raw_ids=(), final_ids=()):
    return {"raw": raw, "final": final, "raw_ids": list(raw_ids), "final_ids": list(final_ids), "direction_summary": "과제 질문", "reason": "r"}


def question_candidates():
    return {"candidates": [{"candidate_id": "G1", "category": "question", "title": "무슨 과제인지 물어볼까요?", "example": "무슨 과제예요?",
                            "current_anchor": "과제", "reference_ids": [],
                            "personalization": {"kind": "none", "source_quotes": [], "actor_ids": [], "delta": ""}}],
            "personalization_used": False, "reason": ""}


def support_candidates():
    out = question_candidates()
    out["candidates"][0].update(category="support", title="마감을 응원해 볼까요?", example="화이팅!")
    return out


def gate_ok(request):
    ids = [c["candidate_id"] for c in request.payload["candidates"]]
    return {"assessments": [{"candidate_id": i, **{k: True for k in GATE_BOOLEANS}, "useful_personalization": False, "quality": 3,
                             "redundancy_group": "g-" + i, "issue": "none", "reason": ""} for i in ids]}


def batch_provider(*, propose=True):
    """후보 버전 프롬프트(PATCH 포함)는 질문 후보를 만들어 실제 답장 방향을 맞힌다."""
    calls = {"appropriateness": 0}

    def appropriateness(request):
        calls["appropriateness"] += 1
        return appropriate()

    def direction_match(request):
        titles = [c["title"] for c in request.payload["final_cards"]]
        included = any("무슨 과제" in t for t in titles)
        raw_included = any("무슨 과제" in c["title"] for c in request.payload["candidates"])
        return direction("included" if raw_included else "absent", "included" if included else "absent",
                         [c["candidate_id"] for c in request.payload["candidates"] if "무슨 과제" in c["title"]],
                         [c["card_id"] for c in request.payload["final_cards"] if "무슨 과제" in c["title"]])

    def generic(request):
        return question_candidates() if PATCH in request.instructions else support_candidates()

    def card_quality(request):
        return {"verdict": "appropriate", "confidence": "high", "stable_meaning": True, "unsupported_personalization": False, "reason": "r"}

    proposal = ({"status": "proposed", "module": "generation", "instruction": PATCH, "reason_codes": ["candidate_absent"]}
                if propose else {"status": "deferred", "module": "none", "instruction": "", "reason_codes": ["insufficient_evidence"]})
    return ScriptedProvider({"actual_appropriateness": appropriateness, "direction_match": direction_match, "generic": generic,
                             "gate": gate_ok, "card_quality": card_quality, "propose_patch": lambda r: proposal}), calls


def run_batch(conn, settings, provider, force=True):
    scheduled = schedule(conn, settings, force=force)
    assert scheduled, "batch should be scheduled"
    return Engine(conn, settings, provider).tick(scheduled["batch_run_id"])


def seed_history_cases(conn, settings):
    """앞선 배치에서 dev·regression 사례가 쌓인 상태를 만든다(검증 세트의 재료)."""
    splits = rooms_by_split()
    provider, _ = batch_provider(propose=False)
    # dev·regression 은 방도 사람도 겹치지 않아야 검증 세트가 된다.
    for i, room in enumerate(splits["dev"][:2] + splits["regression"][:2]):
        make_episode(conn, room_id=room, video_id=900 + i, viewer_id=3 if i < 2 else 4)
    first = run_batch(conn, settings, provider)
    assert first["status"] == "KEPT_BASELINE"
    return splits


def test_schedule_waits_for_twenty_or_twenty_four_hours(conn):
    versions.init_version(conn)
    settings = Settings()
    for i in range(19):
        make_episode(conn, room_id=1, video_id=200 + i)
    assert schedule(conn, settings) is None
    make_episode(conn, room_id=1, video_id=300)
    created = schedule(conn, settings)
    assert created["status"] == "created" and created["closures"] == 20
    assert conn.execute("SELECT trigger_reason FROM ai_reply.batch_run").fetchone()["trigger_reason"] == "COUNT"
    assert schedule(conn, settings)["status"] == "resumed"  # PENDING 배치는 같은 cutoff 로 재개


def test_oldest_waiting_24h_triggers_batch(conn):
    versions.init_version(conn)
    make_episode(conn, room_id=1, video_id=400, closed_ago_hours=25)
    assert schedule(conn, Settings())["status"] == "created"


def test_real_user_episodes_are_not_collected_unless_allowed(conn):
    versions.init_version(conn)
    make_episode(conn, room_id=1, video_id=410, provenance="REAL_USER", closed_ago_hours=30)
    assert schedule(conn, Settings()) is None
    assert schedule(conn, Settings(real_user_feedback_enabled=True))["status"] == "created"


def test_fb02_inappropriate_or_uncertain_reply_is_not_accumulated(conn):
    """FB02: 실제 답장이 부적절하거나 의미가 불명확하면 사례·개선 근거에서 제외."""
    versions.init_version(conn)
    make_episode(conn, room_id=1, video_id=500, reply="ㅋ")
    make_episode(conn, room_id=1, video_id=501, reply="음")
    verdicts = iter([appropriate("inappropriate"), appropriate("uncertain", "low")])
    provider = ScriptedProvider({"actual_appropriateness": lambda r: next(verdicts), "direction_match": lambda r: direction()})
    result = run_batch(conn, Settings(), provider)
    assert result["observations"] == {"unsuitable_or_uncertain": 2}
    assert conn.execute("SELECT count(*) AS n FROM ai_reply.feedback_case").fetchone()["n"] == 0


def test_fb01_direction_absent_is_recorded_separately_from_card_quality(conn):
    """FB01: 방향 미포함이어도 카드 품질 판정과 섞지 않고 생성 누락 관찰로 따로 남긴다."""
    versions.init_version(conn)
    make_episode(conn, room_id=1, video_id=510)
    provider = ScriptedProvider({"actual_appropriateness": lambda r: appropriate(), "direction_match": lambda r: direction()})
    run_batch(conn, Settings(), provider)
    case = conn.execute("SELECT reason_code, reference_eligible FROM ai_reply.feedback_case").fetchone()
    assert case == {"reason_code": "candidate_absent", "reference_eligible": False}  # QA 는 참고 사례가 되지 않는다
    stages = [r["stage"] for r in conn.execute("SELECT stage FROM ai_reply.feedback_judgment ORDER BY id").fetchall()]
    assert stages == ["ACTUAL_APPROPRIATENESS", "DIRECTION_MATCH"]


def test_fb06_uncertain_judgment_is_never_rejudged(conn):
    """FB06: 첫 유효 판정이 uncertain 이어도 좋은 결과가 나올 때까지 다시 부르지 않는다."""
    versions.init_version(conn)
    make_episode(conn, room_id=1, video_id=520)
    calls = []
    provider = ScriptedProvider({"actual_appropriateness": lambda r: calls.append(1) or appropriate("uncertain", "low"),
                                 "direction_match": lambda r: direction()})
    settings = Settings()
    scheduled = schedule(conn, settings, force=True)
    engine = Engine(conn, settings, provider)
    engine.tick(scheduled["batch_run_id"])
    conn.execute("UPDATE ai_reply.batch_run SET status = 'PENDING'")  # 같은 배치를 다시 돌려도
    Engine(conn, settings, provider).tick(scheduled["batch_run_id"])
    assert len(calls) == 1


def test_validated_candidate_is_held_when_automatic_activation_is_off(conn):
    versions.init_version(conn)
    settings = Settings()
    splits = seed_history_cases(conn, settings)
    for i in range(3):
        make_episode(conn, room_id=splits["dev"][5 + i], video_id=950 + i)
    provider, _ = batch_provider()
    result = run_batch(conn, settings, provider)
    assert result["status"] == "VALIDATED" and result["activation"] == "disabled"
    assert result["validation"]["passed"] and result["validation"]["improved"]
    assert versions.active(conn)["version_id"].startswith("v1-")
    run = conn.execute("SELECT stage, frozen_sha, result FROM ai_reply.validation_run").fetchone()
    assert run["stage"] == "DONE" and run["frozen_sha"] and run["result"] == "PASS"
    consumed = conn.execute("SELECT count(*) AS n FROM ai_reply.feedback_case WHERE consumed_by_batch_id IS NOT NULL").fetchone()["n"]
    assert consumed >= 3


def test_validated_candidate_activates_by_cas_and_can_roll_back(conn):
    versions.init_version(conn)
    settings = Settings(automatic_activation_enabled=True)
    splits = seed_history_cases(conn, settings)
    for i in range(3):
        make_episode(conn, room_id=splits["dev"][5 + i], video_id=960 + i)
    provider, _ = batch_provider()
    result = run_batch(conn, settings, provider)
    assert result["status"] == "ACTIVE"
    active = versions.active(conn)
    assert active["version_id"] == result["candidate_version_id"] and PATCH in active["prompts_json"]["generic"]
    back = versions.rollback(conn, active["parent_version_id"], "검토 후 복원")
    assert versions.active(conn)["version_id"] == back["to"] and back["revision"] == active["revision"] + 1


def test_fb04_regression_in_one_case_keeps_baseline(conn):
    """FB04: 한 사례라도 퇴보하면 기존 버전을 유지한다."""
    versions.init_version(conn)
    settings = Settings(automatic_activation_enabled=True)
    splits = seed_history_cases(conn, settings)
    for i in range(3):
        make_episode(conn, room_id=splits["dev"][5 + i], video_id=970 + i)
    # 기존 버전이 이미 맞히던 사례(902)를 후보 버전이 놓친다. 나머지 사례는 후보 버전이 더 맞힌다.
    provider, _ = batch_provider()

    def generic(request):
        patched = PATCH in request.instructions
        if request.payload["current"]["caption"].endswith("902"):
            return support_candidates() if patched else question_candidates()
        return question_candidates() if patched else support_candidates()

    provider.responses["generic"] = generic
    result = run_batch(conn, settings, provider)
    assert result["status"] == "KEPT_BASELINE" and result["decision"] == "kept_baseline"
    assert result["validation"]["strict_nonregression"] is False
    assert versions.active(conn)["version_id"].startswith("v1-")


def test_fb05_active_version_changed_during_batch_is_not_overwritten(conn):
    """FB05: 배치 중 활성 버전이 바뀌면 CAS 실패로 보류하고 덮어쓰지 않는다."""
    versions.init_version(conn)
    settings = Settings(automatic_activation_enabled=True)
    splits = seed_history_cases(conn, settings)
    for i in range(3):
        make_episode(conn, room_id=splits["dev"][5 + i], video_id=980 + i)
    provider, _ = batch_provider()
    scheduled = schedule(conn, settings, force=True)
    current = versions.active(conn)
    other = versions.derive(conn, current, "manual", gallery_snapshot_id=versions.ensure_empty_gallery(conn), note="manual change")
    engine = Engine(conn, settings, provider)
    original_activate = versions.activate

    def race(*args, **kwargs):  # 검증이 끝난 순간 다른 사람이 활성 버전을 바꿨다
        versions.activate = original_activate
        original_activate(conn, other["version_id"], current["version_id"], current["revision"], "concurrent change")
        return original_activate(*args, **kwargs)

    versions.activate = race
    try:
        result = engine.tick(scheduled["batch_run_id"])
    finally:
        versions.activate = original_activate
    assert result["status"] == "VALIDATED" and result["activation"] == "cas_conflict"
    assert versions.active(conn)["version_id"] == other["version_id"]


def test_suite_without_both_splits_keeps_baseline_and_keeps_observations(conn):
    versions.init_version(conn)
    settings = Settings()
    for i in range(3):
        make_episode(conn, room_id=rooms_by_split()["dev"][i], video_id=990 + i)
    provider, _ = batch_provider()
    result = run_batch(conn, settings, provider)
    assert result["decision"] == "validation_suite_insufficient"
    assert conn.execute("SELECT count(*) AS n FROM ai_reply.feedback_case WHERE consumed_by_batch_id IS NULL").fetchone()["n"] == 3


def test_same_batch_is_not_run_twice_at_once(conn):
    """작업기 작업과 수동 tick 이 같은 배치를 동시에 돌리지 않는다(배치 lease)."""
    versions.init_version(conn)
    make_episode(conn, room_id=1, video_id=600)
    scheduled = schedule(conn, Settings(), force=True)
    conn.execute("UPDATE ai_reply.batch_run SET lease_owner = 'other', lease_until = now() + interval '5 minutes'")
    provider = ScriptedProvider({})
    assert Engine(conn, Settings(), provider, owner="me").tick(scheduled["batch_run_id"])["status"] == "busy"
    assert provider.calls == []


def test_suite_drops_regression_cases_of_people_in_dev(conn):
    """개인 기록을 쓰는 평가는 사람도 겹치지 않게: dev 에 있는 시청자의 regression 사례는 검증 세트에서 뺀다."""
    versions.init_version(conn)
    settings = Settings()
    splits = rooms_by_split()
    provider, _ = batch_provider(propose=False)
    for i, room in enumerate(splits["dev"][:2] + splits["regression"][:2]):
        make_episode(conn, room_id=room, video_id=700 + i, viewer_id=3)  # 같은 사람
    run_batch(conn, settings, provider)
    for i in range(3):
        make_episode(conn, room_id=splits["dev"][5 + i], video_id=750 + i)
    provider, _ = batch_provider()
    assert run_batch(conn, settings, provider)["decision"] == "validation_suite_insufficient"


# ───────────── 정기 개선 루프(운영: 한 달에 한 번 EventBridge Scheduler → ECS 작업) ─────────────

def scheduled_worker(database_url, settings, provider):
    return Worker(settings, provider=provider, embedder_factory=PinnedFakeEmbedder,
                  connect_fn=lambda: connect(database_url))


def no_sleep(seconds):
    raise AssertionError(f"should not wait ({seconds}s)")


def test_scheduled_run_does_nothing_without_new_observations(conn, database_url):
    versions.init_version(conn)
    settings = Settings(database_url=database_url)
    provider, _ = batch_provider()
    assert run_scheduled(conn, settings, scheduled_worker(database_url, settings, provider), sleep=no_sleep) == {"status": "idle"}
    assert conn.execute("SELECT count(*) AS n FROM ai_reply.batch_run").fetchone()["n"] == 0


def test_scheduled_run_finishes_the_batch_in_process_and_auto_applies_passing_version(conn, database_url):
    """한 달에 한 번: 20개·24시간 조건과 상관없이 새로 닫힌 관찰로 배치를 만들어 이 프로세스에서 끝내고, 통과하면 적용한다."""
    versions.init_version(conn)
    settings = Settings(database_url=database_url, automatic_activation_enabled=True)
    splits = seed_history_cases(conn, settings)
    for i in range(3):
        make_episode(conn, room_id=splits["dev"][5 + i], video_id=970 + i)
    provider, _ = batch_provider()
    result = run_scheduled(conn, settings, scheduled_worker(database_url, settings, provider), sleep=no_sleep)
    assert (result["status"], result["trigger_reason"], result["job_status"]) == ("ACTIVE", "SCHEDULED", "SUCCEEDED")
    assert versions.active(conn)["version_id"] == result["candidate_version_id"]
    # 같은 달에 다시 돌아도 새 관찰이 없으면 아무것도 하지 않는다
    assert run_scheduled(conn, settings, scheduled_worker(database_url, settings, provider), sleep=no_sleep) == {"status": "idle"}


def test_scheduled_run_leaves_transport_failure_for_next_run_and_resumes(conn, database_url):
    versions.init_version(conn)
    settings = Settings(database_url=database_url)
    make_episode(conn, room_id=1, video_id=980)

    def unreachable(request):
        raise errors.JobFailure(errors.TRANSPORT_EXHAUSTED, "network", retryable=True)

    down = ScriptedProvider({"actual_appropriateness": unreachable})
    waits = []
    first = run_scheduled(conn, settings, scheduled_worker(database_url, settings, down), deadline_seconds=60,
                          sleep=waits.append)
    assert first["status"] == "PENDING" and first["job_status"] == "RETRY_WAIT" and waits == []  # 600초 재시도는 마감 밖
    # 다음 정기 실행(재시도 시각 지남): 같은 배치를 이어 받아 끝낸다
    conn.execute("UPDATE ai_reply.ai_job SET next_attempt_at = now() - interval '1 second' WHERE job_type = 'FEEDBACK_BATCH'")
    provider, _ = batch_provider(propose=False)
    second = run_scheduled(conn, settings, scheduled_worker(database_url, settings, provider), sleep=no_sleep)
    assert second["batch_run_id"] == first["batch_run_id"] and second["scheduled"] == "resumed"
    assert second["status"] == "KEPT_BASELINE" and second["job_status"] == "SUCCEEDED"
