"""추천 효과 보고(이슈 #560): 그룹별 게시자 응답률, 차이, 구간, 판정, 대조 확인, 연결 구분 참고."""
from __future__ import annotations

import uuid
from datetime import datetime, timedelta, timezone
from pathlib import Path

import pytest

from reply_ai import effect_report as effect
from reply_ai.db import jsonb
from reply_ai.util import sha256_text

ROOT = Path(__file__).resolve().parents[2]
SQL_559 = ROOT / "scripts" / "sql" / "559-reply-depth-up.sql"
SQL_561 = ROOT / "scripts" / "sql" / "561-reply-effect-up.sql"
T0 = datetime(2026, 11, 10, 3, 0, tzinfo=timezone.utc)


def row(arm, responded, room=1, cohort=9, minutes=None, members=3, spoke=1, kind=None):
    return {"message_id": None, "room_id": room, "cohort_id": cohort, "arm": arm, "owner_responded": responded,
            "owner_response_minutes": minutes, "response_window_minutes": 30, "bystander_members": members,
            "bystanders_responded": spoke, "link_kind": kind}


def test_report_counts_rates_and_difference_exactly():
    rows = ([row("TREATMENT", True, room=r, minutes=5.0) for r in (1, 2, 3)] + [row("TREATMENT", False, room=4)]
            + [row("HOLDOUT", True, room=r) for r in (1, 2)] + [row("HOLDOUT", False, room=r) for r in (3, 4)]
            + [row("NONE", True, room=5), row(None, False, room=6)])
    out = effect.report(rows, min_per_arm=1, iterations=200)
    assert out["arms"]["TREATMENT"] == {"replies": 4, "responded": 3, "rate": 0.75, "median_minutes": 5.0, "rooms": 4}
    assert out["arms"]["HOLDOUT"]["replies"] == 4 and out["arms"]["HOLDOUT"]["rate"] == 0.5
    assert out["difference"] == 0.25
    assert out["excluded_arm"] == {"NONE": 1, "MISSING": 1}
    lo, hi = out["ci95"]
    assert lo <= 0.25 <= hi
    assert out["bootstrap"] == {"unit": "chat_room", "iterations": 200, "seed": 0}
    # 대조: 게시자 응답률(전체 10행 중 6) vs 다른 멤버 채팅률(1/3)
    assert out["placebo"] == {"owner_rate": 0.6, "bystander_rate": 0.3333, "bystander_members": 30}


def test_report_is_inconclusive_below_min_per_arm_and_reproducible():
    rows = [row("TREATMENT", True, room=r) for r in range(10)] + [row("HOLDOUT", False, room=r) for r in range(10)]
    assert effect.report(rows)["verdict"] == "INCONCLUSIVE"            # 그룹당 330건 미만
    a = effect.report(rows, min_per_arm=5, iterations=300, seed=7)
    b = effect.report(rows, min_per_arm=5, iterations=300, seed=7)
    assert a["ci95"] == b["ci95"] and a["verdict"] == "RESPONSE_UP"


def test_report_verdicts_down_and_no_difference():
    down = [row("TREATMENT", False, room=r) for r in range(20)] + [row("HOLDOUT", True, room=r) for r in range(20)]
    assert effect.report(down, min_per_arm=10, iterations=300)["verdict"] == "RESPONSE_DOWN"
    same = ([row("TREATMENT", r % 2 == 0, room=r) for r in range(40)]
            + [row("HOLDOUT", r % 2 == 0, room=r) for r in range(40)])
    assert effect.report(same, min_per_arm=10, iterations=300)["verdict"] == "NO_DIFFERENCE"


def test_treatment_link_kinds_are_reference_only():
    rows = [row("TREATMENT", True, kind="CARD_AS_IS"), row("TREATMENT", False, kind="CARD_AS_IS"),
            row("TREATMENT", True, kind="NO_RECOMMENDATION"), row("HOLDOUT", True)]
    out = effect.report(rows, min_per_arm=1, iterations=50)
    assert out["treatment_by_link"] == {"CARD_AS_IS": {"replies": 2, "rate": 0.5},
                                        "NO_RECOMMENDATION": {"replies": 1, "rate": 1.0}}


def test_per_assigned_viewer_uses_assignment_counts():
    rows = [row("TREATMENT", True), row("TREATMENT", False), row("HOLDOUT", True)]
    out = effect.report(rows, min_per_arm=1, iterations=20, assigned={"TREATMENT": 4, "HOLDOUT": 4})
    assert out["per_assigned_viewer"] == {"TREATMENT": {"assigned": 4, "replies": 0.5, "responded": 0.25},
                                          "HOLDOUT": {"assigned": 4, "replies": 0.25, "responded": 0.25}}
    assert effect.report(rows, min_per_arm=1, iterations=20)["per_assigned_viewer"] is None
    assert effect.assigned_for({"cohort:9": {"HOLDOUT": 3}}, 9) == {"HOLDOUT": 3}
    assert effect.assigned_for({"cohort:9": {"HOLDOUT": 3}}, None) is None


def test_by_cohort_splits_and_keeps_unknown():
    rows = [row("TREATMENT", True, cohort=8), row("HOLDOUT", False, cohort=9), row("HOLDOUT", True, cohort=None)]
    out = effect.by_cohort(rows, min_per_arm=1, iterations=20)
    assert list(out) == ["8", "9", "unknown"]


# ───────────────────────── DB ─────────────────────────

def _seed(conn):
    conn.execute(SQL_559.read_text(encoding="utf-8"))
    conn.execute(SQL_561.read_text(encoding="utf-8"))
    conn.execute("TRUNCATE ai_reply.reply_depth_observation, ai_reply.holdout_assignment")


def _set(conn, set_id, example):
    conn.execute(
        """INSERT INTO ai_reply.recommendation_set (set_id, attempt_id, kind, analysis_id, video_id, viewer_id,
               version_id, gallery_snapshot_id, status, cards, internal_cards, expires_at)
           VALUES (%s, 1, 'GENERIC', 1, 101, NULL, 'v1', 'g1', 'READY', %s, %s, %s)""",
        (set_id, jsonb([{"card_id": "c1", "category": "question", "title": "t"}]),
         jsonb([{"card_id": "c1", "category": "question", "title": "t", "example": example}]),
         T0 + timedelta(days=1)))


def _reply(conn, message_id, arm, responded, *, room=7, cohort=9, set_id=None, text=None, minutes=None):
    episode_id = None
    if set_id:
        episode_id = conn.execute(
            """INSERT INTO ai_reply.reply_episode (session_id, set_id, request_id, viewer_id, video_id, room_id,
                   first_impression_at, last_impression_at, provenance)
               VALUES (%s, %s, 'r', 3, 101, 1, %s, %s, 'QA') RETURNING id""",
            (str(uuid.uuid4()), set_id, T0, T0)).fetchone()["id"]
        conn.execute("""INSERT INTO ai_reply.episode_reply_snapshot (episode_id, message_id, reply_text, reply_sha, provenance)
                        VALUES (%s, %s, %s, %s, 'QA')""", (episode_id, message_id, text, sha256_text(text)))
    conn.execute("""INSERT INTO ai_reply.reply_link (message_id, viewer_id, video_id, sent_at, source_status, attribution,
                        episode_id, experiment_arm) VALUES (%s, 3, 101, %s, 'NONE', %s, %s, %s)""",
                 (message_id, T0, "inferred" if set_id else "unexposed", episode_id, arm))
    conn.execute(
        """INSERT INTO ai_reply.reply_depth_observation (message_id, video_id, viewer_id, room_id, width, depth, status,
               window_start, window_end, observed_at, provenance, cohort_id, owner_responded, owner_response_minutes,
               response_window_minutes, bystander_members, bystanders_responded)
           VALUES (%s, 101, 3, %s, 0, 0, 'OBSERVED', %s, %s, %s, 'QA', %s, %s, %s, 30, 4, 1)""",
        (message_id, room, T0, T0 + timedelta(hours=24), T0 + timedelta(hours=25), cohort, responded, minutes))


def test_fetch_rows_reads_labels_arms_and_link_kind(conn):
    _seed(conn)
    _set(conn, "set-1", "처음 만들어 보니 어떤 과정이 제일 어려웠어요?")
    _reply(conn, 1, "TREATMENT", True, set_id="set-1", text="처음 만들어 보니 어떤 과정이 제일 어려웠어요?", minutes=2.5)
    _reply(conn, 2, "TREATMENT", False, set_id="set-1", text="와 맛있겠다")
    _reply(conn, 3, "TREATMENT", True)                       # 카드 노출 없음
    _reply(conn, 4, "HOLDOUT", False, room=8)
    _reply(conn, 5, "HOLDOUT", True, cohort=8)
    conn.execute("""INSERT INTO ai_reply.reply_depth_observation (message_id, video_id, viewer_id, status, window_start,
                        window_end, observed_at, provenance, unobserved_reason)
                    VALUES (6, 101, 3, 'UNOBSERVED', %s, %s, %s, 'QA', 'REPLY_UNAVAILABLE')""",
                 (T0, T0 + timedelta(hours=24), T0 + timedelta(hours=25)))   # 효과 라벨 없음 → 빠짐

    rows = effect.fetch_rows(conn)
    assert [r["message_id"] for r in rows] == [1, 2, 3, 4, 5]
    kinds = {r["message_id"]: r["link_kind"] for r in rows}
    assert kinds == {1: "CARD_AS_IS", 2: "SELF_WRITTEN", 3: "NO_RECOMMENDATION", 4: None, 5: None}
    assert rows[0]["owner_response_minutes"] == 2.5

    only9 = effect.fetch_rows(conn, cohort_id=9)
    assert [r["message_id"] for r in only9] == [1, 2, 3, 4]
    out = effect.report(only9, min_per_arm=1, iterations=100)
    assert out["arms"]["TREATMENT"]["rate"] == 0.6667 and out["arms"]["HOLDOUT"]["rate"] == 0.0
    conn.execute("""INSERT INTO ai_reply.holdout_assignment (group_key, room_id, user_id, arm, hash, ratio, assigned_at)
                    VALUES ('cohort:9', 1, 1, 'TREATMENT', 0.1, 0.5, now()), ('cohort:9', 1, 2, 'HOLDOUT', 0.9, 0.5, now()),
                           ('room:5', 5, 3, 'HOLDOUT', 0.8, 0.5, now())""")
    assert effect.fetch_assigned(conn) == {"cohort:9": {"TREATMENT": 1, "HOLDOUT": 1}, "room:5": {"HOLDOUT": 1}}


def test_fetch_rows_requires_561(conn):
    conn.execute(SQL_559.read_text(encoding="utf-8"))
    conn.execute("DROP TABLE IF EXISTS ai_reply.holdout_assignment")
    conn.execute("ALTER TABLE ai_reply.reply_link DROP COLUMN IF EXISTS experiment_arm")
    with pytest.raises(RuntimeError, match="561"):
        effect.fetch_rows(conn)
    conn.execute(SQL_561.read_text(encoding="utf-8"))
