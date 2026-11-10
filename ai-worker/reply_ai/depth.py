"""Depth 평가 모델 랭킹(최종 카드 정렬). REPLY_AI_DEPTH_RANKING_ENABLED=false(기본)면 아무것도 하지 않는다.

- 공통 맥락 C = 영상 게시 시점의 분석 스냅샷에서 공개 근거만: 분석이 인용한 게시 전 단체 대화 중 최근 N 턴
  (REPLY_AI_DEPTH_CONTEXT_CHAT_TURNS, 입력 순서) + 영상 게시글(캡션 + 장면 설명). 개인 기록 P 는 넣지 않는다
  (개인 세트도 같은 C 로 점수를 매긴다).
- 모델 입력은 최대 50 토큰이라 C 를 그대로 옮기면 장면 설명 꼬리만 남는다. 그래서 변환 단계가 C 를 영어 한두 문장
  (영상 내용·캡션·단체 대화 요지, 목표 약 30 토큰 이하)으로 압축하고, 그 한 턴을 점수 입력의 맥락으로 쓴다.
  실제 입력 토큰 수·잘림 여부는 서비스 응답을 받아 기록에 남긴다.
- 압축 C 와 최종 카드 예문을 기존 provider 로 한 번에 변환(strict 스키마 1번, 비용 예약·단계 캐시는 JobContext.model_call).
  한국어 원문은 바꾸지 않고, 영어·점수·모델 id·순위를 카드(`depth`)와 시도 기록(`candidates.depth_ranking`)에 남긴다.
- 점수 서비스(depth-serve)에 (C, 후보 하나) → 점수 하나를 받아 점수 내림차순(동점은 원래 순서)으로 정렬한다.
- 서비스 꺼짐·실패·영어 변환 실패는 원래 순서를 유지하고 상태만 기록한다(fail-open). 생성·검수를 다시 부르지 않는다.
- 점수는 같은 C 의 후보끼리 비교하는 값이다. 확률로 해석하지 않는다.
"""
from __future__ import annotations

import logging
import math
import os
from dataclasses import dataclass
from typing import Any

import httpx

from . import analysis, errors, generation
from .config import Settings
from .provider import ModelRequest
from .resources import AUX_STAGES, prompt_file
from .util import sha256_json, sha256_text

log = logging.getLogger("reply_ai.depth")

TRANSLATE_STAGE = "depth_translate"
SCORE_STAGE = "depth_score"
VIDEO_TURN_ID = "video"
DEFAULT_CONTEXT_CHAT_TURNS = 4

OK = "OK"
NOT_NEEDED = "NOT_NEEDED"                       # 카드 2개 미만: 정렬할 것이 없다
SERVICE_UNAVAILABLE = "SERVICE_UNAVAILABLE"     # 서비스 연결·시간 초과·HTTP 오류
SERVICE_INVALID = "SERVICE_INVALID"             # 응답 형식·개수·유한값 위반
TRANSLATION_FAILED = "TRANSLATION_FAILED"       # 영어 변환 호출 실패(비용 한도 포함)
RANKING_INVALID = "RANKING_INVALID"             # 캐시·응답을 카드에 대응시키지 못함(정렬 없이 원래 순서)


class DepthUnavailable(Exception):
    def __init__(self, status: str, detail: str = ""):
        self.status = status
        self.detail = detail[:200]
        super().__init__(f"{status}: {self.detail}")


@dataclass(frozen=True)
class Scored:
    scores: list[float]
    model_id: str
    model_revision: str | None
    max_seq_len: int | None
    inputs: list[dict[str, Any]] | None = None


class DepthClient:
    """depth-serve HTTP 클라이언트. 실패는 모두 DepthUnavailable 로 바꾼다."""

    def __init__(self, settings: Settings, transport: httpx.BaseTransport | None = None):
        self.url = settings.depth_url.rstrip("/")
        self.timeout = settings.depth_timeout_seconds
        self.transport = transport

    def _client(self) -> httpx.Client:
        return httpx.Client(timeout=self.timeout, transport=self.transport)

    def info(self) -> dict[str, Any]:
        try:
            with self._client() as client:
                response = client.get(self.url + "/info")
                response.raise_for_status()
                body = response.json()
        except (httpx.HTTPError, ValueError) as error:
            raise DepthUnavailable(SERVICE_UNAVAILABLE, type(error).__name__) from None
        if not isinstance(body, dict) or not isinstance(body.get("model_id"), str):
            raise DepthUnavailable(SERVICE_INVALID, "info without model_id")
        return body

    def score(self, context_en: list[str], candidates_en: list[str]) -> Scored:
        try:
            with self._client() as client:
                response = client.post(self.url + "/score", json={"context_en": context_en, "candidates_en": candidates_en})
                response.raise_for_status()
                body = response.json()
        except (httpx.HTTPError, ValueError) as error:
            raise DepthUnavailable(SERVICE_UNAVAILABLE, type(error).__name__) from None
        scores = body.get("scores") if isinstance(body, dict) else None
        if not isinstance(scores, list) or len(scores) != len(candidates_en):
            raise DepthUnavailable(SERVICE_INVALID, "score count")
        if not all(isinstance(s, (int, float)) and not isinstance(s, bool) and math.isfinite(s) for s in scores):
            raise DepthUnavailable(SERVICE_INVALID, "non-finite score")
        if not isinstance(body.get("model_id"), str) or not body["model_id"]:
            raise DepthUnavailable(SERVICE_INVALID, "model_id")
        inputs = body.get("inputs")
        if not (isinstance(inputs, list) and len(inputs) == len(candidates_en) and all(isinstance(i, dict) for i in inputs)):
            inputs = None
        return Scored([float(s) for s in scores], body["model_id"], body.get("model_revision"), body.get("max_seq_len"), inputs)


def context_chat_turns() -> int:
    """C 에 넣을 인용 단체 대화 턴 수(최근 N). 작업기 Settings 와 내보내기·학습이 같은 값을 쓴다."""
    value = os.environ.get("REPLY_AI_DEPTH_CONTEXT_CHAT_TURNS", "")
    return max(0, int(value)) if value.strip() else DEFAULT_CONTEXT_CHAT_TURNS


def common_context_turns(source: dict[str, Any], snapshot: dict[str, Any], max_chat_turns: int | None = None
                         ) -> list[dict[str, str]]:
    """공통 맥락 C(한국어). 분석이 인용한 게시 전 단체 대화 최근 N 턴 + 영상 게시글. 개인 기록은 쓰지 않는다."""
    limit = context_chat_turns() if max_chat_turns is None else max(0, max_chat_turns)
    history = analysis.cited_records(list(source.get("history_records") or []), snapshot["output_json"])
    turns = [{"turn_id": r["source_id"], "text": r["text"]} for r in history if r.get("role") == "room" and r["text"].strip()]
    turns = turns[len(turns) - limit:] if limit else []
    video = "\n".join(t for t in (source.get("caption") or "", snapshot["current_visual_description"]) if t.strip())
    turns.append({"turn_id": VIDEO_TURN_ID, "text": video})
    return turns


def rank_by_scores(cards: list[dict[str, Any]], scores: list[float]) -> list[dict[str, Any]]:
    """점수 내림차순. 동점은 원래 순서."""
    order = sorted(range(len(cards)), key=lambda i: (-scores[i], i))
    return [cards[i] for i in order]


def _translate(ctx: Any, task_id: str, context_ko: list[dict[str, str]], cards_ko: list[dict[str, str]]) -> dict[str, Any]:
    """(압축한 영어 C, 카드별 영어). context_ko 가 비어 있으면 context_en 은 None.

    단계 캐시 키에 입력 sha 를 넣어, 재시도에서 입력이 달라지면 이전 결과를 쓰지 않는다.
    """
    payload = {"task_id": task_id, "context_turns": context_ko, "candidates": cards_ko}
    instructions = prompt_file(AUX_STAGES[TRANSLATE_STAGE][0])
    result = ctx.model_call(f"{TRANSLATE_STAGE}:{sha256_json(payload)[:16]}",
                            ModelRequest(TRANSLATE_STAGE, instructions, payload,
                                         max_output_tokens=ctx.settings.max_output_tokens(TRANSLATE_STAGE)))
    output = result.output
    if [c["card_id"] for c in output["candidates"]] != [c["card_id"] for c in cards_ko] \
            or not all(c["text_en"].strip() for c in output["candidates"]) \
            or bool(context_ko) != bool(output["context_en"].strip()):
        raise errors.semantic("depth_translate output differs from input")
    return {"context_en": [output["context_en"].strip()] if context_ko else None,
            "candidates": {c["card_id"]: c["text_en"] for c in output["candidates"]},
            "prompt_sha": sha256_json(instructions), "model": result.model}


def original_order(cards: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """Depth 정렬 전(생성·선택) 순서. 병합 같은 순서 의존 규칙은 이 순서로 동작한다(Depth 는 표시 순서에만)."""
    if cards and all(isinstance(c.get("depth"), dict) and isinstance(c["depth"].get("original_rank"), int) for c in cards):
        return sorted(cards, key=lambda c: c["depth"]["original_rank"])
    return list(cards)


def _text_key(text: str) -> str:
    return "t" + sha256_text(text)[:16]


def strip(cards: list[dict[str, Any]]) -> list[dict[str, Any]]:
    """다른 세트에서 복사해 온 카드의 이전 순위 기록을 뗀다."""
    return [{k: v for k, v in c.items() if k != "depth"} for c in cards]


def _compute(ctx: Any, client: DepthClient, cards: list[dict[str, Any]], context_ko: list[dict[str, str]],
             context_sha: str, task_id: str, base_record: dict[str, Any] | None) -> dict[str, Any]:
    info = client.info()  # 꺼진 서비스 때문에 변환 비용을 쓰지 않도록 먼저 확인한다
    reuse = base_record if base_record and base_record.get("status") == OK \
        and base_record.get("context_sha") == context_sha and base_record.get("context_en") else None
    known = {cid: en for cid, en in (reuse.get("candidates_en") or {}).items()} if reuse else {}
    # 변환 id 는 예문 sha(재시도에서 card_id 가 새로 생겨도 같은 입력이면 같은 변환 캐시를 쓴다).
    todo = list({_text_key(c["example"]): {"card_id": _text_key(c["example"]), "text": c["example"]}
                 for c in cards if c["card_id"] not in known}.values())
    translated: dict[str, Any] = {"context_en": None, "candidates": {}, "prompt_sha": None, "model": None}
    if reuse is None or todo:
        translated = _translate(ctx, task_id, [] if reuse else context_ko, todo)
    context_en = reuse["context_en"] if reuse else translated["context_en"]
    candidates_en = {c["card_id"]: known.get(c["card_id"]) or translated["candidates"][_text_key(c["example"])] for c in cards}
    scored = client.score(context_en, [candidates_en[c["card_id"]] for c in cards])
    return {"card_ids": [c["card_id"] for c in cards], "context_en": context_en, "candidates_en": candidates_en,
            "scores": dict(zip((c["card_id"] for c in cards), scored.scores)),
            "inputs": [{"card_id": c["card_id"], **i} for c, i in zip(cards, scored.inputs)] if scored.inputs else None,
            "model_id": scored.model_id, "model_revision": scored.model_revision, "max_seq_len": scored.max_seq_len,
            "service_model_id": info["model_id"], "context_reused_from": reuse.get("task_id") if reuse else None,
            "translate_prompt_sha": translated["prompt_sha"], "translate_model": translated["model"]}


def _score(ctx: Any, items: list[dict[str, Any]], *, source: dict[str, Any], snapshot: dict[str, Any], task_id: str,
           base_record: dict[str, Any] | None = None, client: DepthClient | None = None
           ) -> tuple[dict[str, Any] | None, dict[str, Any]]:
    """items(`card_id`=점수 키, `example`)를 (C, example) 별로 채점한다. (검사한 점수 단계 또는 None, 기록).

    실패(서비스·변환·캐시 불일치)는 예외 없이 (None, 상태 기록) — 호출자는 기존 동작을 그대로 쓴다.
    """
    settings: Settings = ctx.settings
    context_ko = common_context_turns(source, snapshot, settings.depth_context_chat_turns)
    cited = len([r for r in analysis.cited_records(list(source.get("history_records") or []), snapshot["output_json"])
                 if r.get("role") == "room"])
    ids = [c["card_id"] for c in items]
    record: dict[str, Any] = {"status": None, "task_id": task_id, "context_sha": sha256_json(context_ko),
                              "context_chat_turns": len(context_ko) - 1, "context_chat_turns_cited": cited}
    client = client or DepthClient(settings)
    stage = ctx.stage(SCORE_STAGE)
    if stage is not None and stage.get("card_ids") != ids:
        stage = None  # 재시도에서 카드가 새로 만들어졌다(card_id 가 다르다). 이전 점수를 쓰지 않고 다시 계산한다.
    try:
        if stage is None:
            stage = _compute(ctx, client, items, context_ko, record["context_sha"], task_id, base_record)
            ctx.save_stage(SCORE_STAGE, stage)
        for cid in ids:  # 대응·유한값을 여기서 모두 확인한다(이후 단계는 실패하지 않는다)
            if not math.isfinite(float(stage["scores"][cid])) or not isinstance(stage["candidates_en"][cid], str):
                raise ValueError("invalid cached score")
        if not isinstance(stage["model_id"], str):
            raise ValueError("model_id")
    except DepthUnavailable as unavailable:
        log.warning("depth_unavailable", extra={"job_id": ctx.job.id, "status": unavailable.status})
        return None, {**record, "status": unavailable.status, "detail": unavailable.detail}
    except errors.JobFailure as failure:
        log.warning("depth_translate_failed", extra={"job_id": ctx.job.id, "failure_code": failure.code})
        return None, {**record, "status": TRANSLATION_FAILED, "detail": failure.code}
    except (KeyError, TypeError, ValueError) as error:
        log.warning("depth_ranking_invalid", extra={"job_id": ctx.job.id, "error": type(error).__name__})
        return None, {**record, "status": RANKING_INVALID, "detail": type(error).__name__}
    record.update({k: v for k, v in stage.items() if k not in ("scores", "card_ids")}, status=OK)
    return stage, record


def _annotate(cards: list[dict[str, Any]], stage: dict[str, Any], key: str = "card_id") -> list[dict[str, Any]]:
    """점수 내림차순(동점은 들어온 순서)으로 정렬하고 카드마다 depth 기록을 붙인다. original_rank = 들어온 순서."""
    scores = [float(stage["scores"][c[key]]) for c in cards]
    ranked = rank_by_scores(cards, scores)
    original = {c[key]: i + 1 for i, c in enumerate(cards)}
    return [{**card, "depth": {"score": round(stage["scores"][card[key]], 6), "rank": rank, "original_rank": original[card[key]],
                               "example_en": stage["candidates_en"][card[key]], "model_id": stage["model_id"]}}
            for rank, card in enumerate(ranked, start=1)]


def rank_cards(ctx: Any, cards: list[dict[str, Any]], *, source: dict[str, Any], snapshot: dict[str, Any], task_id: str,
               base_record: dict[str, Any] | None = None, client: DepthClient | None = None
               ) -> tuple[list[dict[str, Any]], dict[str, Any] | None]:
    """개인 세트: 병합까지 끝난 최종 카드의 표시 순서만 점수순으로. (정렬한 카드, 시도 기록).

    꺼져 있으면 (카드 그대로, None) — 기존 동작과 같다. base_record: 일반 세트의 기록. 같은 C 를 쓰도록 그 영어 맥락을
    다시 쓰고, 이미 영어가 있는 카드는 다시 변환하지 않는다.
    """
    if not ctx.settings.depth_ranking_enabled:
        return (strip(cards) if any("depth" in c for c in cards) else cards), None
    # 일반 세트에서 이미 옮긴 예문(카드의 depth.example_en)은 같은 C 를 다시 쓸 때 변환하지 않는다.
    known_en = {c["card_id"]: c["depth"]["example_en"] for c in cards
                if isinstance(c.get("depth"), dict) and isinstance(c["depth"].get("example_en"), str)}
    if base_record and known_en:
        base_record = {**base_record, "candidates_en": {**(base_record.get("candidates_en") or {}), **known_en}}
    cards = strip(cards)
    if len(cards) < 2:
        return cards, {"status": NOT_NEEDED, "card_count": len(cards)}
    stage, record = _score(ctx, cards, source=source, snapshot=snapshot, task_id=task_id, base_record=base_record,
                           client=client)
    record["original_order"] = [c["card_id"] for c in cards]
    if stage is None:
        return cards, record
    result = _annotate(cards, stage)
    record.update(scores=[{"card_id": c["card_id"], "score": stage["scores"][c["card_id"]]} for c in result],
                  ranked_order=[c["card_id"] for c in result])
    log.info("depth_ranked", extra={"job_id": ctx.job.id, "model_id": stage["model_id"], "cards": len(cards),
                                    "changed": record["ranked_order"] != record["original_order"]})
    return result, record


def select_generic(ctx: Any, accepted: list[dict[str, Any]], *, source: dict[str, Any], snapshot: dict[str, Any],
                   task_id: str, client: DepthClient | None = None
                   ) -> tuple[list[dict[str, Any]], dict[str, Any] | None]:
    """일반 세트: 검수 통과 후보 전부를 (C, example) 별로 채점하고, 기존 선택 규칙(최대 3개·유형당 1개·의미 그룹당 1개·
    품질 우선) 안에서 같은 품질끼리의 우선순위를 Depth 점수로 정한다. 결과 카드는 점수순.

    accepted 는 generation.accepted_candidates 의 순서(품질 내림차순, 후보 번호 오름차순). 반환 카드는 선택된 후보
    (card_id 없음). 꺼짐·실패면 (generation.select_generic(accepted), 기록 또는 None) — 기존 선택과 같다.
    점수 키는 candidate_id(생성 단계 캐시에서 다시 만들어도 같다).
    """
    baseline = generation.select_generic(accepted)
    if not ctx.settings.depth_ranking_enabled:
        return baseline, None
    if len(accepted) < 2:
        return baseline, {"status": NOT_NEEDED, "card_count": len(accepted)}
    items = [{"card_id": c["candidate_id"], "example": c["example"]} for c in accepted]
    stage, record = _score(ctx, items, source=source, snapshot=snapshot, task_id=task_id, client=client)
    baseline_ids = [c["candidate_id"] for c in baseline]
    record.update(scope="candidates", candidate_order=[c["candidate_id"] for c in accepted], baseline_selected=baseline_ids)
    if stage is None:
        return baseline, record
    scores = {cid: float(stage["scores"][cid]) for cid in record["candidate_order"]}
    position = {cid: i for i, cid in enumerate(record["candidate_order"])}
    prioritized = sorted(accepted, key=lambda c: (-c["quality"], -scores[c["candidate_id"]], position[c["candidate_id"]]))
    selected = generation.select_generic(prioritized)
    # original_rank = Depth 없이 정했을 순서(accepted 순서). 개인화 병합은 이 순서로 동작한다(depth.original_order).
    chosen = {c["candidate_id"] for c in selected}
    result = _annotate([c for c in accepted if c["candidate_id"] in chosen], stage, key="candidate_id")
    selected_ids = [c["candidate_id"] for c in selected]
    record.update(scores=[{"candidate_id": cid, "score": scores[cid]} for cid in record["candidate_order"]],
                  prioritized_order=[c["candidate_id"] for c in prioritized], selected=selected_ids,
                  selection_changed=set(selected_ids) != set(baseline_ids),
                  added_by_depth=[cid for cid in selected_ids if cid not in baseline_ids],
                  removed_by_depth=[cid for cid in baseline_ids if cid not in selected_ids],
                  ranked_order=[c["candidate_id"] for c in result])
    log.info("depth_selected", extra={"job_id": ctx.job.id, "model_id": stage["model_id"], "candidates": len(accepted),
                                      "selection_changed": record["selection_changed"]})
    return result, record
