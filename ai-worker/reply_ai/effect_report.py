"""추천 효과 보고(이슈 #560): 무작위 비교 그룹별 게시자 응답률과 차이.

입력은 앱 서버가 남긴 두 표뿐이다(작업기는 서비스 표를 읽지 않는다).
- ai_reply.reply_depth_observation: 효과 라벨(owner_responded 등, 561 칸). Depth(학습 라벨)는 여기서 쓰지 않는다.
- ai_reply.reply_link: 보낸 사람의 비교 그룹(experiment_arm), 노출 연결.

판정은 TREATMENT(카드 받음)와 HOLDOUT(카드 안 받음)의 응답률 차이와 95% 구간(채팅방 단위 bootstrap)이다.
NONE(실험 꺼짐)과 그룹 칸이 없는 행은 비교에서 뺀다. 카드 그대로/수정/직접 작성별 응답률은 참고로만 낸다
(카드를 쓰는 사람이 원래 더 적극적일 수 있어 판정에 쓰지 않는다).
"""
from __future__ import annotations

import random
from collections import Counter, defaultdict
from typing import Any

from .depth_outcomes import NO_RECOMMENDATION, RECOMMENDATION_UNAVAILABLE, LinkThresholds, _cards, classify_link

TREATMENT, HOLDOUT = "TREATMENT", "HOLDOUT"
MIN_PER_ARM = 330          # 기본 응답률 약 64%에서 10%p 차이를 95%/80% 로 잡는 그룹당 표본
BOOTSTRAP = 2000
RESPONSE_COLUMN = "owner_responded"


def schema_ready(conn) -> bool:
    found = conn.execute("""SELECT count(*) AS n FROM information_schema.columns
                             WHERE table_schema = 'ai_reply'
                               AND ((table_name = 'reply_depth_observation' AND column_name = %s)
                                    OR (table_name = 'reply_link' AND column_name = 'experiment_arm'))""",
                         (RESPONSE_COLUMN,)).fetchone()["n"]
    return found == 2


def fetch_rows(conn, cohort_id: int | None = None, thresholds: LinkThresholds = LinkThresholds()) -> list[dict[str, Any]]:
    """효과 라벨이 있는 답장 1건 = 1행. TREATMENT 행에는 노출 카드와 실제 문장으로 연결 구분(link_kind)을 붙인다."""
    if not schema_ready(conn):
        raise RuntimeError("561-reply-effect-up.sql 이 적용되지 않았다(효과 라벨·그룹 칸 없음)")
    params: list[Any] = []
    where = "o.owner_responded IS NOT NULL"
    if cohort_id is not None:
        where += " AND o.cohort_id = %s"
        params.append(cohort_id)
    rows = conn.execute(f"""
        SELECT o.message_id, o.video_id, o.viewer_id, o.room_id, o.cohort_id, o.provenance,
               o.owner_responded, o.owner_response_minutes, o.response_window_minutes,
               o.bystander_members, o.bystanders_responded,
               l.experiment_arm, l.episode_id, e.set_id, x.reply_text
          FROM ai_reply.reply_depth_observation o
          JOIN ai_reply.reply_link l ON l.message_id = o.message_id
          LEFT JOIN ai_reply.reply_episode e ON e.id = l.episode_id
          LEFT JOIN ai_reply.episode_reply_snapshot x ON x.episode_id = l.episode_id AND x.message_id = o.message_id
         WHERE {where}
         ORDER BY o.message_id""", params).fetchall()
    set_ids = sorted({r["set_id"] for r in rows if r["set_id"]})
    sets = {}
    if set_ids:
        for s in conn.execute("""SELECT set_id, cards, internal_cards, revoked_at FROM ai_reply.recommendation_set
                                  WHERE set_id = ANY(%s)""", (set_ids,)).fetchall():
            sets[s["set_id"]] = s
    out = []
    for r in rows:
        link_kind = None
        if r["experiment_arm"] == TREATMENT:
            rset = sets.get(r["set_id"]) if r["set_id"] else None
            if r["episode_id"] is None:
                link_kind = NO_RECOMMENDATION
            elif rset is None or rset["revoked_at"] is not None:
                link_kind = RECOMMENDATION_UNAVAILABLE
            else:
                link_kind = classify_link(r["reply_text"], _cards(rset), thresholds)["kind"]
        out.append({
            "message_id": r["message_id"], "room_id": r["room_id"], "cohort_id": r["cohort_id"],
            "arm": r["experiment_arm"], "owner_responded": bool(r["owner_responded"]),
            "owner_response_minutes": float(r["owner_response_minutes"]) if r["owner_response_minutes"] is not None else None,
            "response_window_minutes": r["response_window_minutes"],
            "bystander_members": r["bystander_members"], "bystanders_responded": r["bystanders_responded"],
            "link_kind": link_kind,
        })
    return out


def fetch_assigned(conn) -> dict[str, dict[str, int]]:
    """묶음 키(cohort:<id> | room:<id>)별 그룹 배정 시청자 수. 영상 게시자도 배정에 들어 있다."""
    out: dict[str, dict[str, int]] = defaultdict(dict)
    for r in conn.execute("""SELECT group_key, arm, count(*) AS n FROM ai_reply.holdout_assignment
                               GROUP BY group_key, arm""").fetchall():
        out[r["group_key"]][r["arm"]] = r["n"]
    return dict(out)


def assigned_for(assigned: dict[str, dict[str, int]], cohort_id: Any) -> dict[str, int] | None:
    """기수 묶음 키의 배정 수. 기수 없는 방(room:<id>)은 기수별 보고에서 합치지 않는다."""
    return assigned.get(f"cohort:{cohort_id}") if cohort_id is not None else None


def _rate(rows: list[dict[str, Any]]) -> float | None:
    return round(sum(r["owner_responded"] for r in rows) / len(rows), 4) if rows else None


def _arm_stats(rows: list[dict[str, Any]]) -> dict[str, Any]:
    minutes = sorted(r["owner_response_minutes"] for r in rows if r["owner_response_minutes"] is not None)
    return {"replies": len(rows), "responded": sum(r["owner_responded"] for r in rows), "rate": _rate(rows),
            "median_minutes": minutes[len(minutes) // 2] if minutes else None,
            "rooms": len({r["room_id"] for r in rows})}


def _diff(rows: list[dict[str, Any]]) -> float | None:
    t = [r for r in rows if r["arm"] == TREATMENT]
    h = [r for r in rows if r["arm"] == HOLDOUT]
    if not t or not h:
        return None
    return _rate(t) - _rate(h)


def bootstrap_ci(rows: list[dict[str, Any]], iterations: int = BOOTSTRAP, seed: int = 0) -> list[float] | None:
    """채팅방을 복원 추출해 차이의 2.5%/97.5% 분위수. 같은 방 답장은 함께 움직인다(방 분위기가 겹치므로)."""
    by_room: dict[Any, list[dict[str, Any]]] = defaultdict(list)
    for r in rows:
        by_room[r["room_id"]].append(r)
    rooms = sorted(by_room, key=lambda x: (x is None, x))
    if len(rooms) < 2:
        return None
    rng = random.Random(seed)
    diffs = []
    for _ in range(iterations):
        sample = [r for room in (rng.choice(rooms) for _ in rooms) for r in by_room[room]]
        d = _diff(sample)
        if d is not None:
            diffs.append(d)
    if not diffs:
        return None
    diffs.sort()
    lo = diffs[int(0.025 * (len(diffs) - 1))]
    hi = diffs[int(0.975 * (len(diffs) - 1))]
    return [round(lo, 4), round(hi, 4)]


def report(rows: list[dict[str, Any]], *, min_per_arm: int = MIN_PER_ARM, iterations: int = BOOTSTRAP,
           seed: int = 0, assigned: dict[str, int] | None = None) -> dict[str, Any]:
    compared = [r for r in rows if r["arm"] in (TREATMENT, HOLDOUT)]
    t = [r for r in compared if r["arm"] == TREATMENT]
    h = [r for r in compared if r["arm"] == HOLDOUT]
    diff = _diff(compared)
    ci = bootstrap_ci(compared, iterations, seed) if diff is not None else None
    if len(t) < min_per_arm or len(h) < min_per_arm or ci is None:
        verdict = "INCONCLUSIVE"
    elif ci[0] > 0:
        verdict = "RESPONSE_UP"
    elif ci[1] < 0:
        verdict = "RESPONSE_DOWN"
    else:
        verdict = "NO_DIFFERENCE"
    members = sum(r["bystander_members"] or 0 for r in rows)
    spoke = sum(r["bystanders_responded"] or 0 for r in rows)
    kinds: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for r in t:
        kinds[r["link_kind"] or "UNKNOWN"].append(r)
    return {
        "rows": len(rows),
        "excluded_arm": dict(Counter(r["arm"] or "MISSING" for r in rows if r["arm"] not in (TREATMENT, HOLDOUT))),
        "windows_minutes": sorted({r["response_window_minutes"] for r in rows if r["response_window_minutes"]}),
        "arms": {TREATMENT: _arm_stats(t), HOLDOUT: _arm_stats(h)},
        "difference": round(diff, 4) if diff is not None else None,
        "ci95": ci,
        "bootstrap": {"unit": "chat_room", "iterations": iterations, "seed": seed},
        "min_per_arm": min_per_arm,
        "verdict": verdict,
        # 대조 확인: 답장받은 게시자 응답률이 같은 방 다른 멤버의 같은 창 안 채팅률보다 높아야 라벨이 반응을 잡는 것이다.
        "placebo": {"owner_rate": _rate(rows), "bystander_rate": round(spoke / members, 4) if members else None,
                    "bystander_members": members},
        # 참고(판정에 안 씀): 카드 받은 그룹 안 연결 구분별 응답률.
        "treatment_by_link": {k: {"replies": len(v), "rate": _rate(v)} for k, v in sorted(kinds.items())},
        # 시청자 단위(배정 수 기준): 카드가 답장을 쓰는 사람·횟수를 바꿀 수 있어 답장 단위 비교만으로는 선택 편향이 남는다.
        "per_assigned_viewer": ({arm: {"assigned": assigned.get(arm, 0),
                                       "replies": round(len(g) / assigned[arm], 4) if assigned.get(arm) else None,
                                       "responded": round(sum(r["owner_responded"] for r in g) / assigned[arm], 4)
                                       if assigned.get(arm) else None}
                                 for arm, g in ((TREATMENT, t), (HOLDOUT, h))} if assigned else None),
        "notes": ["답장 단위 비교는 답장을 보낸 사람만 본다. per_assigned_viewer 로 답장 수 변화도 같이 본다.",
                  "placebo 는 답장 1건당 게시자 응답률과 멤버 1명당 채팅률의 근사 비교다."],
    }


def by_cohort(rows: list[dict[str, Any]], assigned: dict[str, dict[str, int]] | None = None,
              **kwargs: Any) -> dict[str, Any]:
    groups: dict[Any, list[dict[str, Any]]] = defaultdict(list)
    for r in rows:
        groups[r["cohort_id"]].append(r)
    return {("unknown" if c is None else str(c)): report(v, assigned=assigned_for(assigned or {}, c), **kwargs)
            for c, v in sorted(groups.items(), key=lambda kv: (kv[0] is None, kv[0] or 0))}
