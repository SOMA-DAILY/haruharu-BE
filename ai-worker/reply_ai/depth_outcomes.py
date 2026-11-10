"""Depth 예측·실측 연결(`reply-ai depth export-outcomes`). 실제 전송한 영상 답장 1건 = 1행.

- 라벨: ai_reply.reply_depth_observation(앱 서버가 관찰 기간 뒤 한 번 계산). OBSERVED 만 depth·width 가 있고,
  UNOBSERVED 는 null 이다(0 과 다르다).
- 실제 문장: 수집이 허용된 출처만(ai_reply.episode_reply_snapshot, 피드백 배치와 같은 provenance 규칙 —
  REPLY_AI_REAL_USER_FEEDBACK_ENABLED 가 꺼져 있으면 QA·SYNTHETIC 만). 허용되지 않은 출처의 답장은 행을 만들지 않는다.
- 추천 쪽: 답장의 추천 출처(VALID) 세트, 없으면 그 답장 episode 가 본 세트. 노출 순서·예문(한국어/영어)·예측 점수·model_id 는
  추천 당시 기록(internal_cards[].depth)을 그대로 쓴다. 세트가 없으면 "추천 노출 없음", 세트는 있었지만 무효(revoked)·파기
  (카드 비움)·행 없음이면 "추천 기록 없음"(RECOMMENDATION_UNAVAILABLE, 노출 여부를 알 수 없다)으로 따로 둔다.
- 공통 맥락 C: 영상마다 하나. 추천 당시 기록(generation_attempt.candidates.depth_ranking, OK)의 영어 C 를 쓰고, 없으면
  분석 스냅샷에서 같은 규칙(depth.common_context_turns)으로 만들어 영어로 바꾼다. 개인 기록 P 는 넣지 않는다.
- 실제 문장 점수: 지금 지정한 모델(depth-serve 또는 로컬 가중치)로 다시 매기고 그 model_id 를 함께 남긴다. 단 세트에 기록된
  model_id(카드 예측을 낸 모델)와 같은 모델일 때만 남기고, 다르면 점수 null + score_status=MODEL_MISMATCH 다(카드 예측과
  실제 문장 점수를 같은 모델끼리만 비교한다). 추천 노출이 없거나 세트에 기록된 모델이 없으면 지정한 모델 id 로 남긴다.
  학습 입력의 답장은 언제나 실제 전송 문장이다. 카드 예문은 분석용이다.
- 대화 결과: Depth·Width 와 함께 영상 작성자 재답장 여부(owner_replied)·후속 메시지 수(followup_message_count)를 행과 요약에 남긴다
  (559 표에 그 칸이 없으면 null).
- 연결 구분: 노출 카드 예문과 실제 문장의 문자 유사도(difflib ratio, 공백 정규화) 최댓값으로
  카드 그대로 / 카드 보고 수정 / 직접 작성. 임계값은 인자로 받고 각 행과 요약에 남긴다.
- 단체 대화 원문: 한국어 C(context_ko)는 기본으로 내보내지 않고 sha 만 남긴다(include_context_text=True 일 때만 원문).
  번역 캐시도 같다 — 기본은 답장 번역만 캐시하고 C 번역은 같은 플래그일 때만 남긴다. 영어 C(context_en)는 학습 입력이라 남는다.
  내보낸 파일·캐시는 보존 기간 파기 대상이 아니므로 쓰고 나면 지운다.
- 점수는 같은 C 의 답장끼리 비교하는 값이다. 확률로 해석하지 않는다.
"""
from __future__ import annotations

import json
import logging
import math
from dataclasses import dataclass
from difflib import SequenceMatcher
from pathlib import Path
from typing import Any, Iterable, Protocol

from . import budget, depth, errors, tokens
from .config import Settings
from .feedback.engine import allowed_provenance
from .provider import ModelRequest, ModelResult, Provider
from .util import canonical, iso, now_utc, parse_iso, sha256_json, sha256_text

log = logging.getLogger("reply_ai.depth")

CARD_AS_IS = "CARD_AS_IS"
CARD_EDITED = "CARD_EDITED"
SELF_WRITTEN = "SELF_WRITTEN"
NO_RECOMMENDATION = "NO_RECOMMENDATION"
RECOMMENDATION_UNAVAILABLE = "RECOMMENDATION_UNAVAILABLE"   # 세트가 무효·파기돼 노출 카드를 알 수 없다
LINK_KINDS = (CARD_AS_IS, CARD_EDITED, SELF_WRITTEN, NO_RECOMMENDATION, RECOMMENDATION_UNAVAILABLE)

OBSERVED = "OBSERVED"
DEFAULT_MAX_HOUR_GAP = 1.0   # 논문 §3.1: 같은 맥락의 두 답장은 전송 시각 차가 1시간 이내일 때만 비교한다
# 실제 문장 점수 상태
SCORE_OK = "OK"
MODEL_MISMATCH = "MODEL_MISMATCH"       # 세트에 기록된 모델과 지금 모델이 다르다
NOT_SCORED = "NOT_SCORED"               # 점수 모델 없음(--scorer none)·영어 문장 또는 영어 C 없음
SCORE_FAILED = "SCORE_FAILED"           # 점수 서비스 실패
OUTCOME_COLUMNS = ("owner_replied", "followup_message_count")   # 559 표에 있으면 읽는다
TRANSLATE_CHUNK = 16   # depth-translate.schema.json candidates maxItems
SCORE_CHUNK = 16       # depth-serve MAX_CANDIDATES


@dataclass(frozen=True)
class LinkThresholds:
    as_is: float = 0.9     # 이 값 이상이면 카드 그대로
    edited: float = 0.4    # 이 값 이상이면 카드 보고 수정, 아래면 직접 작성

    def __post_init__(self) -> None:
        if not 0 <= self.edited <= self.as_is <= 1:
            raise ValueError("0 ≤ edited ≤ as_is ≤ 1")

    def record(self) -> dict[str, float]:
        return {"as_is_threshold": self.as_is, "edited_threshold": self.edited}


def normalize(text: str) -> str:
    return " ".join(text.split())


def similarity(a: str, b: str) -> float:
    return SequenceMatcher(None, normalize(a), normalize(b), autojunk=False).ratio()


def classify_link(actual: str | None, cards: list[dict[str, Any]], thresholds: LinkThresholds,
                  set_unavailable: bool = False) -> dict[str, Any]:
    """세트가 무효·파기됐으면 추천 기록 없음, 노출 카드가 없으면 추천 노출 없음.
    실제 문장이 없으면(수집 불가) kind 를 정하지 않는다(null)."""
    base = thresholds.record()
    if set_unavailable:
        return {"kind": RECOMMENDATION_UNAVAILABLE, "matched_card_id": None, "similarity": None, **base}
    if not cards:
        return {"kind": NO_RECOMMENDATION, "matched_card_id": None, "similarity": None, **base}
    if actual is None:
        return {"kind": None, "matched_card_id": None, "similarity": None, **base}
    best_sim, neg_index = max((similarity(actual, c["example_ko"]), -i) for i, c in enumerate(cards))  # 같으면 앞 카드
    best_card = cards[-neg_index]["card_id"]
    kind = CARD_AS_IS if best_sim >= thresholds.as_is else CARD_EDITED if best_sim >= thresholds.edited else SELF_WRITTEN
    return {"kind": kind, "matched_card_id": best_card, "similarity": round(best_sim, 4), **base}


# ───────────────────────── 점수 ─────────────────────────

class Scorer(Protocol):
    def score(self, context_en: list[str], replies_en: list[str]) -> tuple[list[float], str]: ...


class ServeScorer:
    """depth-serve 가 지금 올린 모델로 점수를 매긴다."""

    def __init__(self, client: depth.DepthClient):
        self.client = client

    def score(self, context_en: list[str], replies_en: list[str]) -> tuple[list[float], str]:
        scores: list[float] = []
        model_id = ""
        for i in range(0, len(replies_en), SCORE_CHUNK):
            scored = self.client.score(context_en, replies_en[i:i + SCORE_CHUNK])
            scores += scored.scores
            model_id = scored.model_id
        return scores, model_id


# ───────────────────────── 영어 변환 ─────────────────────────

class _OfflineContext:
    """depth._translate 가 쓰는 model_call 만 갖춘 대역. 온라인 일일 한도(budget_day)로 비용을 예약·정산한다."""

    def __init__(self, conn, settings: Settings, provider: Provider):
        self.conn, self.settings, self.provider = conn, settings, provider
        self.spent_usd = 0.0
        self.calls = 0

    def model_call(self, key: str, request: ModelRequest) -> ModelResult:
        estimate = tokens.estimate_input(request.instructions, canonical(request.payload), [])
        reservation = budget.reserve_online(self.conn, self.settings,
                                            budget.estimate_usd(self.settings, estimate, request.max_output_tokens))
        try:
            result = self.provider.call(request)
        except errors.JobFailure as failure:
            budget.settle(self.conn, reservation, failure.cost_usd)
            raise
        except Exception:
            budget.settle(self.conn, reservation, 0.0)
            raise
        budget.settle(self.conn, reservation, result.cost_usd)
        self.spent_usd += result.cost_usd
        self.calls += 1
        return result


class Translator:
    """한국어 → 영어(depth_translate 지시문·스키마 그대로). 캐시 파일은 원문 sha256 → 영어만 담는다(원문 없음).

    provider 가 None 이면 캐시에 있는 것만 쓴다(없으면 null).
    """

    def __init__(self, conn, settings: Settings, provider: Provider | None, cache_path: Path | None = None,
                 cache_context: bool = False):
        self.ctx = _OfflineContext(conn, settings, provider) if provider is not None else None
        self.cache_path = cache_path
        self.cache_context = cache_context   # C(단체 대화) 번역을 캐시 파일에 남기는가(기본 아니오, 실행 중 메모리에만)
        self.cache: dict[str, str] = {}
        self.failures = 0
        if cache_path and cache_path.exists():
            for line in cache_path.read_text(encoding="utf-8").splitlines():
                if line.strip():
                    item = json.loads(line)
                    self.cache[item["ko_sha"]] = item["en"]

    def _remember(self, key: str, en: str, persist: bool = True) -> None:
        if key in self.cache:
            return
        self.cache[key] = en
        if self.cache_path and persist:
            with self.cache_path.open("a", encoding="utf-8") as f:
                f.write(json.dumps({"ko_sha": key, "en": en}, ensure_ascii=False) + "\n")

    def cached(self, ko: str) -> str | None:
        return self.cache.get(sha256_text(ko))

    def translate(self, task_id: str, context_ko: list[dict[str, str]], texts: dict[str, str]
                  ) -> tuple[list[str] | None, dict[str, str]]:
        """(영어 C 또는 None, {id: 영어}). 실패한 것은 빠진다.

        영어 C 는 depth_translate 가 맥락 턴 전체를 압축한 한 줄이다(depth-serve 입력 [C_en]). 캐시 키는 턴 원문 목록의 sha.
        """
        context_key = sha256_json([t["text"] for t in context_ko]) if context_ko else None
        context_en = self.cache.get(context_key) if context_key else None
        need_context = bool(context_ko) and context_en is None
        done = {k: en for k, v in texts.items() if (en := self.cached(v)) is not None}
        todo = [{"card_id": k, "text": v} for k, v in texts.items() if k not in done]
        if self.ctx is not None and (need_context or todo):
            chunks = [todo[i:i + TRANSLATE_CHUNK] for i in range(0, len(todo), TRANSLATE_CHUNK)] or [[]]
            for n, chunk in enumerate(chunks):
                turns = context_ko if n == 0 and need_context else []
                try:
                    out = depth._translate(self.ctx, f"{task_id}#{n}", turns, chunk)
                except errors.JobFailure as failure:
                    self.failures += 1
                    log.warning("depth_outcome_translate_failed", extra={"failure_code": failure.code})
                    continue
                if turns and out["context_en"]:
                    context_en = out["context_en"][0]
                    self._remember(context_key, context_en, persist=self.cache_context)
                for c in chunk:
                    self._remember(sha256_text(c["text"]), out["candidates"][c["card_id"]])
                    done[c["card_id"]] = out["candidates"][c["card_id"]]
        return ([context_en] if context_en else None), done


# ───────────────────────── DB 읽기 ─────────────────────────

ROWS_SQL = """
SELECT o.message_id, o.video_id, o.viewer_id, o.room_id, o.source_set_id, o.source_card_id, o.width, o.depth, o.status,
       o.unobserved_reason, o.window_start, o.window_end, o.observed_at, o.provenance, {outcome_columns}
       l.sent_at, e.set_id AS episode_set_id, s.reply_text
  FROM ai_reply.reply_depth_observation o
  LEFT JOIN ai_reply.reply_link l ON l.message_id = o.message_id
  LEFT JOIN ai_reply.reply_episode e ON e.id = l.episode_id
  LEFT JOIN LATERAL (SELECT x.reply_text FROM ai_reply.episode_reply_snapshot x
                      WHERE x.message_id = o.message_id AND x.provenance = ANY(%(allowed)s)
                      ORDER BY x.episode_id LIMIT 1) s ON true
 WHERE o.provenance = ANY(%(allowed)s)
 ORDER BY o.video_id, o.message_id
"""


def _cards(rset: dict[str, Any]) -> list[dict[str, Any]]:
    """노출 순서(공개 cards 순서)의 카드. 예측 점수는 추천 당시 기록(없으면 null)."""
    internal = {c["card_id"]: c for c in rset["internal_cards"]}
    cards = []
    for order, public in enumerate(rset["cards"], start=1):
        card = internal.get(public["card_id"], {})
        predicted = card.get("depth") or {}
        cards.append({"card_id": public["card_id"], "order": order, "category": card.get("category"),
                      "example_ko": card.get("example") or "", "example_en": predicted.get("example_en"),
                      "predicted_score": predicted.get("score"), "model_id": predicted.get("model_id")})
    return cards


def _outcome_columns(conn) -> list[str]:
    rows = conn.execute("""SELECT column_name FROM information_schema.columns
                            WHERE table_schema = 'ai_reply' AND table_name = 'reply_depth_observation'
                              AND column_name = ANY(%s)""", (list(OUTCOME_COLUMNS),)).fetchall()
    return [c for c in OUTCOME_COLUMNS if c in {r["column_name"] for r in rows}]


def _set_model_id(cards: list[dict[str, Any]]) -> str | None:
    """세트의 카드 예측을 낸 모델(카드마다 같다). 랭킹이 꺼져 있던 세트면 None."""
    return next((c["model_id"] for c in cards if c.get("model_id")), None)


def _usable(rset: dict[str, Any]) -> bool:
    """무효(revoked)·파기(카드 비움)된 세트는 노출 카드를 알 수 없다."""
    return rset["revoked_at"] is None and bool(rset["cards"])


def _context_records(conn, video_ids: list[int]) -> dict[int, dict[str, Any]]:
    """영상마다 처음 기록된 OK 랭킹(일반 세트 우선)의 영어 C."""
    rows = conn.execute(
        """SELECT DISTINCT ON (video_id) video_id, id AS attempt_id, analysis_id, candidates -> 'depth_ranking' AS ranking
             FROM ai_reply.generation_attempt
            WHERE video_id = ANY(%s) AND candidates -> 'depth_ranking' ->> 'status' = 'OK'
            ORDER BY video_id, (stage <> 'GENERIC'), id""", (video_ids,)).fetchall()
    return {r["video_id"]: r for r in rows}


def _context_ko(conn, analysis_id: int | None, video_id: int) -> tuple[list[dict[str, str]] | None, int | None]:
    """분석 스냅샷의 공통 맥락(한국어). 원문이 지워졌거나 스냅샷이 없으면 None."""
    snapshot = None
    if analysis_id is not None:
        snapshot = conn.execute("SELECT * FROM ai_reply.analysis_snapshot WHERE id = %s AND status = 'READY'",
                                (analysis_id,)).fetchone()
    if snapshot is None:
        snapshot = conn.execute("""SELECT * FROM ai_reply.analysis_snapshot WHERE video_id = %s AND status = 'READY'
                                    ORDER BY id DESC LIMIT 1""", (video_id,)).fetchone()
    if snapshot is None:
        return None, None
    source = conn.execute("SELECT * FROM ai_reply.analysis_input WHERE id = %s", (snapshot["analysis_input_id"],)).fetchone()
    if source is None or source["purged_at"] is not None:
        return None, snapshot["id"]
    return depth.common_context_turns(source, snapshot), snapshot["id"]


def export_outcomes(conn, settings: Settings, *, scorer: Scorer | None, translator: Translator,
                    thresholds: LinkThresholds = LinkThresholds(), include_context_text: bool = False
                    ) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    """(행 목록, 내보내기 기록). 행 순서는 video_id, message_id. include_context_text=False(기본)면 한국어 C 는 sha 만."""
    allowed = allowed_provenance(settings)
    present = _outcome_columns(conn)
    columns = "".join(f"o.{c}, " for c in present)
    raw = conn.execute(ROWS_SQL.format(outcome_columns=columns), {"allowed": allowed}).fetchall()
    skipped = conn.execute("""SELECT count(*) AS n FROM ai_reply.reply_depth_observation
                               WHERE NOT provenance = ANY(%s)""", (allowed,)).fetchone()["n"]
    set_ids = sorted({r["source_set_id"] or r["episode_set_id"] for r in raw} - {None})
    sets = {r["set_id"]: r for r in conn.execute(
        """SELECT set_id, analysis_id, cards, internal_cards, revoked_at FROM ai_reply.recommendation_set
            WHERE set_id = ANY(%s)""",
        (set_ids,)).fetchall()}
    videos: dict[int, list[dict[str, Any]]] = {}
    for r in raw:
        videos.setdefault(r["video_id"], []).append(r)
    stored = _context_records(conn, list(videos))
    rows: list[dict[str, Any]] = []
    model_ids: set[str] = set()
    for video_id, group in videos.items():
        record = stored.get(video_id)
        exposed_analysis = next((sets[s]["analysis_id"] for r in group
                                 if (s := r["source_set_id"] or r["episode_set_id"]) in sets and _usable(sets[s])), None)
        context_ko, analysis_id = _context_ko(conn, record["analysis_id"] if record else exposed_analysis, video_id)
        if record:
            context = {"source": "recommendation", "attempt_id": record["attempt_id"],
                       "context_sha": record["ranking"].get("context_sha"), "context_en": record["ranking"]["context_en"]}
        else:
            context = {"source": "translated" if context_ko else "unavailable", "attempt_id": None,
                       "context_sha": sha256_json(context_ko) if context_ko else None, "context_en": None}
        context["analysis_id"] = analysis_id
        context["context_ko_sha"] = sha256_json([t["text"] for t in context_ko]) if context_ko else None
        context["context_ko"] = [t["text"] for t in context_ko] if context_ko and include_context_text else None

        prepared = []
        for r in group:
            set_id = r["source_set_id"] or r["episode_set_id"]
            unavailable = set_id is not None and (set_id not in sets or not _usable(sets[set_id]))
            cards = [] if set_id is None or unavailable else _cards(sets[set_id])
            actual_ko = r["reply_text"]
            same_card = next((c for c in cards if actual_ko is not None and c["example_en"]
                              and normalize(c["example_ko"]) == normalize(actual_ko)), None)
            prepared.append((r, set_id, unavailable, cards, actual_ko, same_card["example_en"] if same_card else None))
        to_translate = {str(r["message_id"]): ko for r, _, _, _, ko, en in prepared if ko is not None and en is None}
        context_turns = context_ko if context["context_en"] is None and context_ko else []
        translated_context, translated = translator.translate(f"outcomes:video:{video_id}", context_turns, to_translate)
        if context["context_en"] is None and translated_context:
            context["context_en"] = translated_context

        actual_en = {str(r["message_id"]): en if en is not None else translated.get(str(r["message_id"]))
                     for r, _, _, _, _, en in prepared}
        scores: dict[str, float] = {}
        score_model = None
        failed = False
        targets = [k for k, v in actual_en.items() if v]
        if scorer is not None and context["context_en"] and targets:
            try:
                values, score_model = scorer.score(context["context_en"], [actual_en[k] for k in targets])
                scores = dict(zip(targets, values))
            except depth.DepthUnavailable as unavailable:
                failed = True
                log.warning("depth_outcome_score_failed", extra={"status": unavailable.status})
        for r, set_id, unavailable, cards, actual_ko, _ in prepared:
            key = str(r["message_id"])
            observed = r["status"] == OBSERVED
            expected_model = _set_model_id(cards)
            if key in scores and expected_model is not None and expected_model != score_model:
                score_status = MODEL_MISMATCH
            elif key in scores:
                score_status = SCORE_OK
                model_ids.add(score_model)
            else:
                score_status = SCORE_FAILED if failed and actual_en.get(key) else NOT_SCORED
            kept = score_status == SCORE_OK
            rows.append({
                "message_id": r["message_id"], "video_id": video_id, "chat_room_id": r["room_id"], "viewer_id": r["viewer_id"],
                "provenance": r["provenance"],
                "recommendation": {"set_id": set_id,
                                   "set_status": "NONE" if set_id is None else "UNAVAILABLE" if unavailable else "AVAILABLE",
                                   "exposed": None if unavailable else bool(cards), "cards": cards,
                                   "selected_card_id": r["source_card_id"] if set_id else None},
                "actual": {"text_ko": actual_ko, "text_en": actual_en.get(key),
                           "sent_at": iso(r["sent_at"]) if r["sent_at"] else None,
                           "score": round(scores[key], 6) if kept else None,
                           "score_model_id": score_model if kept else None,
                           "score_status": score_status, "set_model_id": expected_model,
                           "scorer_model_id": score_model},
                "label": {"status": r["status"], "depth": r["depth"] if observed else None,
                          "width": r["width"] if observed else None, "unobserved_reason": r["unobserved_reason"],
                          "owner_replied": r.get("owner_replied"),
                          "followup_message_count": r.get("followup_message_count"),
                          "window_start": iso(r["window_start"]), "window_end": iso(r["window_end"])},
                "link": classify_link(actual_ko, cards, thresholds, set_unavailable=unavailable),
                "context": context,
            })
    meta = {"exported_at": iso(now_utc()), "allowed_provenance": allowed, "skipped_provenance": skipped,
            "include_context_text": include_context_text, "outcome_columns": present,
            "thresholds": thresholds.record(), "score_model_ids": sorted(model_ids),
            "translation": {"calls": translator.ctx.calls if translator.ctx else 0,
                            "spent_usd": round(translator.ctx.spent_usd, 6) if translator.ctx else 0.0,
                            "failures": translator.failures}}
    return rows, meta


# ───────────────────────── 쌍·지표 ─────────────────────────

def pair_exclusion(row: dict[str, Any]) -> str | None:
    if row["label"]["status"] != OBSERVED or row["label"]["depth"] is None:
        return "unobserved"
    if row["actual"]["text_ko"] is None and row["actual"]["text_en"] is None:
        return "text_unavailable"
    if not row["actual"]["text_en"]:
        return "english_unavailable"
    if not row["context"]["context_en"]:
        return "context_unavailable"
    return None


def _hours_apart(a: dict[str, Any], b: dict[str, Any]) -> float | None:
    sa, sb = a["actual"].get("sent_at"), b["actual"].get("sent_at")
    if not sa or not sb:
        return None
    return abs((parse_iso(sa) - parse_iso(sb)).total_seconds()) / 3600


def make_pairs(rows: Iterable[dict[str, Any]], min_depth_diff: int = 1, max_hour_gap: float | None = DEFAULT_MAX_HOUR_GAP
               ) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    """같은 영상(같은 C)의 실제 답장끼리 depth 큰 쪽을 r+ 로. 동점·차이 부족·전송 시각 차 초과는 뺀다.

    제외 이유: 답장 단위(unobserved·text_unavailable·english_unavailable·context_unavailable),
    영상 단위(single_reply: 쓸 수 있는 답장이 하나뿐), 쌍 단위(tie·below_min_diff·over_hour_gap·sent_at_unknown).
    max_hour_gap: 두 답장 전송 시각 차 상한(시간, 논문 §3.1 기본 1). None 이면 보지 않는다.
    """
    if min_depth_diff < 1:
        raise ValueError("min_depth_diff ≥ 1 (동점은 언제나 뺀다)")
    if max_hour_gap is not None and not max_hour_gap > 0:
        raise ValueError("max_hour_gap > 0")
    excluded: dict[str, int] = {}
    groups: dict[int, list[dict[str, Any]]] = {}
    for row in rows:
        reason = pair_exclusion(row)
        if reason:
            excluded[reason] = excluded.get(reason, 0) + 1
            continue
        groups.setdefault(row["video_id"], []).append(row)
    pairs = []
    for video_id, group in groups.items():
        if len(group) < 2:
            excluded["single_reply"] = excluded.get("single_reply", 0) + 1
            continue
        group = sorted(group, key=lambda r: r["message_id"])
        for i, a in enumerate(group):
            for b in group[i + 1:]:
                diff = a["label"]["depth"] - b["label"]["depth"]
                if diff == 0:
                    excluded["tie"] = excluded.get("tie", 0) + 1
                    continue
                if abs(diff) < min_depth_diff:
                    excluded["below_min_diff"] = excluded.get("below_min_diff", 0) + 1
                    continue
                gap = _hours_apart(a, b)
                if max_hour_gap is not None and gap is None:
                    excluded["sent_at_unknown"] = excluded.get("sent_at_unknown", 0) + 1
                    continue
                if max_hour_gap is not None and gap > max_hour_gap:
                    excluded["over_hour_gap"] = excluded.get("over_hour_gap", 0) + 1
                    continue
                pos, neg = (a, b) if diff > 0 else (b, a)
                pairs.append({
                    "pair_id": f"{video_id}:{pos['message_id']}>{neg['message_id']}", "video_id": video_id,
                    "chat_room_id": pos["chat_room_id"], "context_en": pos["context"]["context_en"],
                    "context_sha": pos["context"]["context_sha"], "depth_diff": abs(diff),
                    "hour_gap": round(gap, 4) if gap is not None else None,
                    "pos": _side(pos), "neg": _side(neg)})
    stats = {"pairs": len(pairs), "videos": len({p["video_id"] for p in pairs}), "min_depth_diff": min_depth_diff,
             "max_hour_gap": max_hour_gap,
             "excluded": dict(sorted(excluded.items()))}
    return pairs, stats


def _side(row: dict[str, Any]) -> dict[str, Any]:
    return {"message_id": row["message_id"], "text_en": row["actual"]["text_en"], "depth": row["label"]["depth"],
            "link": row["link"]["kind"], "score": row["actual"]["score"], "score_model_id": row["actual"]["score_model_id"]}


def pair_accuracy(pos: list[float], neg: list[float]) -> dict[str, Any]:
    """r+ 점수가 r− 보다 높은 비율. 같은 점수는 맞힌 것으로 세지 않고 따로 센다. 쌍이 없으면 accuracy=null."""
    correct = sum(1 for p, n in zip(pos, neg, strict=True) if p > n)
    ties = sum(1 for p, n in zip(pos, neg) if p == n)
    return {"pairs": len(pos), "correct": correct, "ties": ties,
            "accuracy": round(correct / len(pos), 4) if pos else None}


def _ranks(values: list[float]) -> list[float]:
    order = sorted(range(len(values)), key=lambda i: values[i])
    ranks = [0.0] * len(values)
    i = 0
    while i < len(order):
        j = i
        while j + 1 < len(order) and values[order[j + 1]] == values[order[i]]:
            j += 1
        for k in range(i, j + 1):
            ranks[order[k]] = (i + j) / 2 + 1
        i = j + 1
    return ranks


def spearman(xs: list[float], ys: list[float]) -> float | None:
    """동순위 평균 순위의 Pearson. 3개 미만이거나 한쪽이 모두 같으면 null."""
    if len(xs) < 3:
        return None
    rx, ry = _ranks(xs), _ranks(ys)
    mx, my = sum(rx) / len(rx), sum(ry) / len(ry)
    sxy = sum((a - mx) * (b - my) for a, b in zip(rx, ry))
    sxx, syy = sum((a - mx) ** 2 for a in rx), sum((b - my) ** 2 for b in ry)
    if sxx == 0 or syy == 0:
        return None
    return round(sxy / math.sqrt(sxx * syy), 4)


def _owner_reply(group: list[dict[str, Any]]) -> dict[str, Any]:
    """영상 작성자 재답장률·평균 후속 메시지 수. 값이 있는(null 아닌) 행만 센다."""
    known = [r["label"].get("owner_replied") for r in group if r["label"].get("owner_replied") is not None]
    counts = [r["label"]["followup_message_count"] for r in group if r["label"].get("followup_message_count") is not None]
    return {"owner_reply_known": len(known), "owner_replied": sum(1 for v in known if v),
            "owner_reply_rate": round(sum(1 for v in known if v) / len(known), 4) if known else None,
            "followup_known": len(counts), "mean_followup_messages": round(sum(counts) / len(counts), 4) if counts else None}


def summarize(rows: list[dict[str, Any]], min_depth_diff: int = 1,
              max_hour_gap: float | None = DEFAULT_MAX_HOUR_GAP) -> dict[str, Any]:
    """지표 요약. 값이 없으면 null(0 과 구분), 개수는 함께 남긴다."""
    observed = [r for r in rows if r["label"]["status"] == OBSERVED]
    unobserved_reasons: dict[str, int] = {}
    for r in rows:
        if r["label"]["status"] != OBSERVED:
            reason = r["label"]["unobserved_reason"] or "unknown"
            unobserved_reasons[reason] = unobserved_reasons.get(reason, 0) + 1
    scored = [r for r in observed if r["actual"]["score"] is not None]
    model_ids = sorted({r["actual"]["score_model_id"] for r in scored})
    pairs, pair_stats = make_pairs(rows, min_depth_diff, max_hour_gap)
    same_model = [p for p in pairs if p["pos"]["score"] is not None and p["neg"]["score"] is not None
                  and p["pos"]["score_model_id"] == p["neg"]["score_model_id"]]
    by_link = {}
    for kind in (*LINK_KINDS, None):
        group = [r for r in rows if r["link"]["kind"] == kind]
        depths = [r["label"]["depth"] for r in group if r["label"]["status"] == OBSERVED]
        by_link[kind or "UNKNOWN_TEXT_UNAVAILABLE"] = {
            "replies": len(group), "observed": len(depths), "unobserved": len(group) - len(depths),
            "mean_depth": round(sum(depths) / len(depths), 4) if depths else None, **_owner_reply(group)}
    return {
        "replies": len(rows), "observed": len(observed), "unobserved": len(rows) - len(observed),
        "unobserved_reasons": dict(sorted(unobserved_reasons.items())),
        "observed_depth_zero": sum(1 for r in observed if r["label"]["depth"] == 0),
        "actual_text_available": sum(1 for r in rows if r["actual"]["text_ko"] is not None),
        "actual_scored": sum(1 for r in rows if r["actual"]["score"] is not None),
        "score_model_ids": model_ids,
        "score_status": dict(sorted(_count(r["actual"].get("score_status") or "UNKNOWN" for r in rows).items())),
        "owner_reply": _owner_reply(rows),
        "spearman_score_vs_depth": {"n": len(scored),
                                    "rho": spearman([r["actual"]["score"] for r in scored],
                                                    [float(r["label"]["depth"]) for r in scored])},
        "pairwise_same_context": {**pair_accuracy([p["pos"]["score"] for p in same_model],
                                                  [p["neg"]["score"] for p in same_model]),
                                  "candidate_pairs": pair_stats["pairs"], "excluded": pair_stats["excluded"]},
        "mean_depth_by_link": by_link,
    }


def _count(values: Iterable[str]) -> dict[str, int]:
    out: dict[str, int] = {}
    for v in values:
        out[v] = out.get(v, 0) + 1
    return out


def write_jsonl(path: Path, rows: Iterable[dict[str, Any]]) -> int:
    n = 0
    with path.open("w", encoding="utf-8") as f:
        for row in rows:
            f.write(json.dumps(row, ensure_ascii=False, default=str) + "\n")
            n += 1
    return n


def read_jsonl(path: Path) -> list[dict[str, Any]]:
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]
