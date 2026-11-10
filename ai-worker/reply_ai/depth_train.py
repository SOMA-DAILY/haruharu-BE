"""Depth 평가 모델 추가 학습·평가(`reply-ai depth build-pairs | train | eval`). extra `depth`(torch·transformers) 전용.

- 비교 데이터셋: export-outcomes 결과(실제 답장 1건 = 1행)에서 같은 C(한국어 C 턴 sha)의 실제 답장끼리 depth 큰 쪽을 r+ 로 한
  (C, r+, r−). 동점은 빼고 최소 차이는 인자.
- 같은 쌍의 두 답장이 정규화·영어 번역·토큰 잘림 뒤 실제 점수 입력(depth_service.build_ids 의 토큰 열, 같은 C·같은 점수
  프로파일)이 같으면 비교할 수 없으므로 쌍에서 빼고 INPUT_COLLISION 으로 남긴다.
- 분할(3개): 기수 단위. cohort_id(rooms.cohort_id) 최댓값 = 최신 기수 = TEST. 그 이전 기수 안에서 채팅방
  (chat_room_id = reply_depth_observation.room_id = chat_messages.chat_room_id) 해시로 VAL(조기 종료용)·TRAIN.
  cohort_id 를 모르는 쌍은 뺀다. 같은 채팅방은 두 분할에 걸치지 않는다.
- 학습 시작점은 언제나 공개 microsoft/DialogRPT-depth θ0(고정 revision)다. 이전 학습 결과에서 이어서 학습하지 않는다
  (매 기수 θ0 + 누적 전체 쌍으로 다시 학습한다). 다른 base(로컬 디렉터리 포함)는 거부한다.
- 손실: L = −log σ(h(C, r+) − h(C, r−)) (DialogRPT 논문 식 3). h = 답장 마지막 토큰 위치의 logit. 전체 가중치 + 점수 헤드,
  lr 기본 1e-5. N step 마다 VAL 쌍의 같은 손실을 재고, patience 번 연속 나아지지 않으면 멈춘다. 가장 낮은 검증 손실의
  가중치를 저장한다. 최대 step 은 안전장치다.
- 입력·점수 위치는 depth-serve 와 같다(depth_service.build_ids·pad_batch, transformer 은닉상태 + score 헤드를 직접 계산해
  각 줄 길이−1 위치에서 꺼낸다).
- 평가: TEST 쌍에서 θ0 와 새 모델의 쌍 비교 정확도. 쌍이 최소 수(기본 30) 미만이면 INCONCLUSIVE, 이상이고 새 모델이
  θ0 보다 2%p 이상(≥ 0.02) 높을 때만 교체 가능(REPLACEABLE), 아니면 KEEP_BASE 로 판정한다. 교체(depth-serve 가 읽는 모델 바꾸기)는 자동으로 하지 않는다.
- 점수는 같은 C 의 답장끼리 비교하는 값이다. 확률로 해석하지 않는다.
"""
from __future__ import annotations

import hashlib
import json
import math
import random
import time
from pathlib import Path
from typing import Any, Callable, Sequence

from .depth_outcomes import DEFAULT_MAX_HOUR_GAP, make_pairs, pair_accuracy
from .depth_service import (DEFAULT_MAX_SEQ_LEN, MODEL_ID, PINNED_REVISION, REFERENCE_CHECKS, REFERENCE_TOLERANCE,
                            build_ids, context_token_cap, pad_batch, sigmoid)
from .util import iso, now_utc, sha256_json

DEFAULT_MIN_EVAL_PAIRS = 30
DEFAULT_MIN_IMPROVEMENT = 0.02   # 교체 기준: TEST 정확도가 θ0 보다 이만큼(2%p) 이상 높아야 한다
DEFAULT_VAL_FRACTION = 0.2
DEFAULT_EVAL_EVERY = 10
DEFAULT_PATIENCE = 3
DEFAULT_MAX_STEPS = 2000
TRAIN = "train"
VAL = "val"
TEST = "test"
SPLITS = (TRAIN, VAL, TEST)
META_FILE = "depth_train.json"
INPUT_COLLISION = "INPUT_COLLISION"
INPUT_BUILDER = "depth_service.build_ids"


# ───────────────────────── 시작점 θ0 ─────────────────────────

def require_base(base: str = MODEL_ID, revision: str | None = PINNED_REVISION) -> None:
    """학습·비교 기준은 공개 θ0(MODEL_ID + 고정 revision)만. 로컬 디렉터리·다른 revision 은 거부한다."""
    if base != MODEL_ID or revision != PINNED_REVISION:
        raise SystemExit(f"base 는 {MODEL_ID}@{PINNED_REVISION} 만 쓴다(받은 값: {base}@{revision}). "
                         "이전 학습 결과에서 이어서 학습하지 않는다")


def _cap(value: int | None) -> int:
    return context_token_cap() if value is None else value


def input_profile(max_seq_len: int = DEFAULT_MAX_SEQ_LEN, context_cap: int | None = None) -> dict[str, Any]:
    """실제 점수 입력을 정하는 값(토크나이저·입력 구성·길이 상한·C 토큰 상한). 입력 해시는 이 프로파일 안에서만 비교한다."""
    return {"tokenizer": f"{MODEL_ID}@{PINNED_REVISION}", "builder": INPUT_BUILDER, "max_seq_len": max_seq_len,
            "context_token_cap": _cap(context_cap)}


def scorer_tokenizer():
    """θ0 의 토크나이저(점수 입력 토큰 열을 만든다)."""
    from transformers import AutoTokenizer
    return AutoTokenizer.from_pretrained(MODEL_ID, revision=PINNED_REVISION)


def tokenizer_encoder(tokenizer) -> Callable[[str], list[int]]:
    return lambda text: tokenizer.encode(text, add_special_tokens=False)


def input_hash(encode: Callable[[str], list[int]], context_en: Sequence[str], reply_en: str, max_seq_len: int,
               context_cap: int | None = None) -> str:
    """점수 모델이 실제로 받는 토큰 열(build_ids: 맥락 턴 EOS … 답장, C 상한·잘림 포함)의 sha256."""
    ids = build_ids(encode, context_en, reply_en, max_seq_len, _cap(context_cap))
    return hashlib.sha256(json.dumps(ids, separators=(",", ":")).encode()).hexdigest()


# ───────────────────────── 비교 데이터셋 ─────────────────────────

def room_bucket(chat_room_id: int) -> float:
    """채팅방 id 의 결정적 [0, 1) 값."""
    return int(hashlib.sha256(f"room:{chat_room_id}".encode()).hexdigest()[:8], 16) / 0x1_0000_0000


def room_split(chat_room_id: int, cohort_id: int, test_cohort_id: int, val_fraction: float) -> str:
    """최신 기수 = TEST. 이전 기수는 채팅방 해시 < val_fraction 이면 VAL, 아니면 TRAIN."""
    if cohort_id == test_cohort_id:
        return TEST
    return VAL if room_bucket(chat_room_id) < val_fraction else TRAIN


def _bump(excluded: dict[str, int], reason: str) -> None:
    excluded[reason] = excluded.get(reason, 0) + 1


def build_pairs(rows: list[dict[str, Any]], *, encode: Callable[[str], list[int]], min_depth_diff: int = 1,
                val_fraction: float = DEFAULT_VAL_FRACTION, max_hour_gap: float | None = DEFAULT_MAX_HOUR_GAP,
                max_seq_len: int = DEFAULT_MAX_SEQ_LEN, context_cap: int | None = None
                ) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    """(쌍 목록, 통계). encode: θ0 토크나이저(텍스트 → 토큰 id, 특수 토큰 없음)."""
    if not 0 <= val_fraction < 1:
        raise ValueError("val_fraction ∈ [0, 1)")
    context_cap = _cap(context_cap)
    profile = input_profile(max_seq_len, context_cap)
    pairs, stats = make_pairs(rows, min_depth_diff, max_hour_gap)
    excluded = stats["excluded"]
    collisions: list[str] = []
    usable = []
    for pair in pairs:
        if pair["chat_room_id"] is None:
            _bump(excluded, "chat_room_unknown")
            continue
        if pair.get("cohort_id") is None:
            _bump(excluded, "cohort_unknown")
            continue
        # 같은 C·같은 프로파일에서 두 답장의 실제 입력이 같으면 비교할 수 없다.
        pos_hash = input_hash(encode, pair["context_en"], pair["pos"]["text_en"], max_seq_len, context_cap)
        neg_hash = input_hash(encode, pair["context_en"], pair["neg"]["text_en"], max_seq_len, context_cap)
        if pos_hash == neg_hash:
            _bump(excluded, INPUT_COLLISION)
            collisions.append(pair["pair_id"])
            continue
        usable.append({**pair, "input_profile": profile, "pos": {**pair["pos"], "input_hash": pos_hash},
                       "neg": {**pair["neg"], "input_hash": neg_hash}})
    # 한 채팅방은 한 기수에 속한다. 기수가 엇갈리는 채팅방은 어느 분할인지 정할 수 없어 뺀다(누수 방지).
    room_cohorts: dict[int, set[int]] = {}
    for pair in usable:
        room_cohorts.setdefault(pair["chat_room_id"], set()).add(pair["cohort_id"])
    conflicted = {room for room, cohorts in room_cohorts.items() if len(cohorts) > 1}
    for pair in usable:
        if pair["chat_room_id"] in conflicted:
            _bump(excluded, "chat_room_cohort_conflict")
    usable = [p for p in usable if p["chat_room_id"] not in conflicted]
    test_cohort = max((p["cohort_id"] for p in usable), default=None)
    kept = [{**p, "split": room_split(p["chat_room_id"], p["cohort_id"], test_cohort, val_fraction)} for p in usable]
    split = {s: {"pairs": sum(1 for p in kept if p["split"] == s),
                 "videos": len({p["video_id"] for p in kept if p["split"] == s}),
                 "chat_rooms": sorted({p["chat_room_id"] for p in kept if p["split"] == s}),
                 "cohorts": sorted({p["cohort_id"] for p in kept if p["split"] == s})} for s in SPLITS}
    stats.update(pairs=len(kept), videos=len({p["video_id"] for p in kept}), split=split, input_profile=profile,
                 input_collisions=sorted(collisions),
                 split_rule={"test_cohort_id": test_cohort, "test": "cohort_id 최댓값(최신 기수)",
                             "val_fraction": val_fraction, "val": "이전 기수 안에서 sha256('room:' + chat_room_id) < val_fraction"})
    stats["excluded"] = dict(sorted(excluded.items()))
    return kept, stats


def load_pairs(path: Path, split: str) -> list[dict[str, Any]]:
    pairs = [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]
    return [p for p in pairs if p["split"] == split]


# ───────────────────────── 모델 ─────────────────────────

class LocalScorer:
    """공개 가중치(허브 id + 고정 revision) 또는 학습 결과 디렉터리를 불러 depth-serve 와 같은 방식으로 점수를 매긴다."""

    def __init__(self, source: str = MODEL_ID, revision: str | None = PINNED_REVISION, device: str = "cpu",
                 max_seq_len: int = DEFAULT_MAX_SEQ_LEN, context_cap: int | None = None):
        import torch
        import transformers
        from transformers import AutoTokenizer, GPT2ForSequenceClassification

        local = Path(source).is_dir()
        kwargs = {} if local else {"revision": revision}
        self.torch = torch
        self.tokenizer = AutoTokenizer.from_pretrained(source, **kwargs)
        self.model = GPT2ForSequenceClassification.from_pretrained(source, dtype=torch.float32, **kwargs)
        if self.model.config.num_labels != 1:
            raise SystemExit(f"unexpected num_labels {self.model.config.num_labels}")
        self.model.to(device).eval()
        self.device = device
        self.max_seq_len = max_seq_len
        self.context_cap = _cap(context_cap)
        self.local = local
        libs = f"tf{transformers.__version__}#torch{torch.__version__.split('+')[0]}#{device}"
        if local:
            meta = json.loads((Path(source) / META_FILE).read_text(encoding="utf-8")) if (Path(source) / META_FILE).exists() else {}
            self.model_id = f"{meta.get('model_name') or Path(source).name}#len{max_seq_len}#ctx{self.context_cap}#fp32#{libs}"
        else:
            self.model_id = f"{source}@{(revision or 'unpinned')[:12]}#len{max_seq_len}#ctx{self.context_cap}#fp32#{libs}"

    def _encode(self, text: str) -> list[int]:
        return self.tokenizer.encode(text, add_special_tokens=False)

    def logits(self, contexts: Sequence[Sequence[str]], replies: Sequence[str]):
        """줄마다 [맥락 EOS … 답장] → 답장 마지막 토큰(길이−1)의 logit. 학습 때는 기울기가 흐른다."""
        torch = self.torch
        rows = [build_ids(self._encode, c, r, self.max_seq_len, self.context_cap)
                for c, r in zip(contexts, replies, strict=True)]
        ids, mask, last = pad_batch(rows)
        input_ids = torch.tensor(ids, device=self.device)
        attention = torch.tensor(mask, device=self.device)
        hidden = self.model.transformer(input_ids=input_ids, attention_mask=attention).last_hidden_state
        logits = self.model.score(hidden)[:, :, 0]
        return logits[torch.arange(len(rows), device=self.device), torch.tensor(last, device=self.device)]

    def score_rows(self, contexts: Sequence[Sequence[str]], replies: Sequence[str], batch_size: int = 16) -> list[float]:
        self.model.eval()
        out: list[float] = []
        with self.torch.no_grad():
            for i in range(0, len(replies), batch_size):
                out += [sigmoid(float(v)) for v in self.logits(contexts[i:i + batch_size], replies[i:i + batch_size]).cpu().tolist()]
        return out

    def score(self, context_en: list[str], replies_en: list[str]) -> tuple[list[float], str]:
        """export-outcomes 용(같은 C 의 답장들)."""
        return self.score_rows([context_en] * len(replies_en), replies_en), self.model_id

    def reference_check(self) -> list[dict[str, Any]]:
        """공개 가중치가 공식 코드 출력(depth-serve 시작 점검과 같은 값)을 재현하는지."""
        results = []
        for context, reply, want in REFERENCE_CHECKS:
            got = self.score_rows([[context]], [reply])[0]
            if not math.isfinite(got) or abs(got - want) > REFERENCE_TOLERANCE:
                raise SystemExit(f"depth reference check: {reply!r} → {got:.4f}, official {want:.4f}")
            results.append({"reply": reply, "score": round(got, 4), "official": round(want, 4)})
        return results


def pairwise_scores(scorer: LocalScorer, pairs: list[dict[str, Any]], batch_size: int = 16) -> tuple[list[float], list[float]]:
    contexts = [p["context_en"] for p in pairs]
    return (scorer.score_rows(contexts, [p["pos"]["text_en"] for p in pairs], batch_size),
            scorer.score_rows(contexts, [p["neg"]["text_en"] for p in pairs], batch_size))


def base_scorer(device: str = "cpu", max_seq_len: int = DEFAULT_MAX_SEQ_LEN) -> tuple[LocalScorer, list[dict[str, Any]] | None]:
    """θ0(공개 가중치, 고정 revision)와 공식 출력 재현 확인 결과."""
    scorer = LocalScorer(MODEL_ID, PINNED_REVISION, device, max_seq_len)
    return scorer, scorer.reference_check()


def _file_sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def _check_profile(pairs: list[dict[str, Any]], max_seq_len: int) -> None:
    """쌍을 만든(입력 해시를 잰) 프로파일과 지금 점수 입력이 같아야 한다."""
    want = input_profile(max_seq_len)
    found = {json.dumps(p.get("input_profile"), sort_keys=True) for p in pairs}
    if found != {json.dumps(want, sort_keys=True)}:
        raise SystemExit(f"쌍의 input_profile 이 지금 점수 입력({want})과 다르다: {sorted(found)}")


def _pair_loss(scorer: LocalScorer, batch: list[dict[str, Any]]):
    """L = −log σ(h(C,r+) − h(C,r−)) 의 평균과 (h+ > h−) 비율."""
    import torch
    contexts = [p["context_en"] for p in batch]
    h_pos = scorer.logits(contexts, [p["pos"]["text_en"] for p in batch])
    h_neg = scorer.logits(contexts, [p["neg"]["text_en"] for p in batch])
    return -torch.nn.functional.logsigmoid(h_pos - h_neg).mean(), (h_pos > h_neg).float().mean()


def validation_loss(scorer: LocalScorer, pairs: list[dict[str, Any]], batch_size: int = 16) -> dict[str, float]:
    """VAL 쌍 전체의 같은 손실(쌍 평균)과 쌍 비교 정확도. 기울기 없음."""
    import torch
    scorer.model.eval()
    total = correct = 0.0
    with torch.no_grad():
        for i in range(0, len(pairs), batch_size):
            batch = pairs[i:i + batch_size]
            loss, accuracy = _pair_loss(scorer, batch)
            total += float(loss) * len(batch)
            correct += float(accuracy) * len(batch)
    return {"loss": round(total / len(pairs), 6), "accuracy": round(correct / len(pairs), 4)}


# ───────────────────────── 학습 ─────────────────────────

def train(pairs_path: Path, out_dir: Path, *, base: str = MODEL_ID, revision: str | None = PINNED_REVISION,
          max_steps: int = DEFAULT_MAX_STEPS, eval_every: int = DEFAULT_EVAL_EVERY, patience: int = DEFAULT_PATIENCE,
          batch_size: int = 8, lr: float = 1e-5, seed: int = 0, device: str = "cpu",
          max_seq_len: int = DEFAULT_MAX_SEQ_LEN, val_batch_size: int = 16) -> dict[str, Any]:
    """θ0 에서 train 분할로 학습하고 val 분할의 손실로 조기 종료한다. 최저 검증 손실 가중치·토크나이저·학습 기록을 out_dir 에 쓴다.

    eval_every step 마다 검증 손실을 재고, patience 번 연속 최저값보다 낮아지지 않으면 멈춘다. max_steps 는 안전장치.
    step 0(θ0 그대로)의 검증 손실도 재므로, 한 번도 나아지지 않으면 θ0 가중치가 그대로 저장된다.
    """
    require_base(base, revision)
    if max_steps < 1 or eval_every < 1 or patience < 1 or batch_size < 1 or not lr > 0:
        raise SystemExit("max_steps ≥ 1, eval_every ≥ 1, patience ≥ 1, batch_size ≥ 1, lr > 0")
    pairs = load_pairs(pairs_path, TRAIN)
    val_pairs = load_pairs(pairs_path, VAL)
    if not pairs:
        raise SystemExit("train 분할에 쌍이 없다(build-pairs 통계를 확인)")
    if not val_pairs:
        raise SystemExit("val 분할에 쌍이 없다(조기 종료에 쓸 검증 쌍이 필요하다, build-pairs --val-fraction 확인)")
    _check_profile(pairs + val_pairs, max_seq_len)
    leaked = sorted({p["chat_room_id"] for p in pairs} & {p["chat_room_id"] for p in val_pairs})
    if leaked:
        raise SystemExit(f"train·val 채팅방이 겹친다: {leaked}")
    if out_dir.exists() and any(out_dir.iterdir()):
        raise SystemExit(f"{out_dir} 가 비어 있지 않다(덮어쓰지 않는다)")
    import torch

    random.seed(seed)
    torch.manual_seed(seed)
    scorer, reference = base_scorer(device, max_seq_len)
    base_model_id = scorer.model_id
    model = scorer.model
    optimizer = torch.optim.AdamW(model.parameters(), lr=lr)
    order: list[int] = []
    rng = random.Random(seed)
    train_history: list[dict[str, Any]] = []
    val_history: list[dict[str, Any]] = []
    started = time.monotonic()
    started_at = iso(now_utc())

    def snapshot() -> dict[str, Any]:
        return {k: v.detach().to("cpu", copy=True) for k, v in model.state_dict().items()}

    best = {"step": 0, **validation_loss(scorer, val_pairs, val_batch_size)}
    val_history.append({"step": 0, "val_loss": best["loss"], "val_accuracy": best["accuracy"], "best": True})
    best_state = snapshot()
    stale = 0
    stop_reason = "max_steps"
    step = 0
    for step in range(1, max_steps + 1):
        model.train()
        batch = []
        while len(batch) < batch_size:
            if not order:
                order = list(range(len(pairs)))
                rng.shuffle(order)
            batch.append(pairs[order.pop()])
        loss, accuracy = _pair_loss(scorer, batch)
        optimizer.zero_grad()
        loss.backward()
        optimizer.step()
        train_history.append({"step": step, "loss": round(float(loss.detach()), 6), "batch_accuracy": round(float(accuracy), 4)})
        if step % eval_every:
            continue
        current = validation_loss(scorer, val_pairs, val_batch_size)
        improved = current["loss"] < best["loss"]
        val_history.append({"step": step, "val_loss": current["loss"], "val_accuracy": current["accuracy"], "best": improved})
        if improved:
            best = {"step": step, **current}
            best_state = snapshot()
            stale = 0
        else:
            stale += 1
            if stale >= patience:
                stop_reason = "early_stop"
                break
    model.load_state_dict(best_state)
    model.eval()
    out_dir.mkdir(parents=True, exist_ok=True)
    model.save_pretrained(out_dir, safe_serialization=True)
    scorer.tokenizer.save_pretrained(out_dir)
    weights_sha = _file_sha(out_dir / "model.safetensors")
    meta = {
        "model_name": f"dialogrpt-depth-ft@{weights_sha[:12]}",
        "base_model": MODEL_ID, "base_revision": PINNED_REVISION, "base_model_id": base_model_id,
        "base_reference_check": reference, "weights_sha256": weights_sha,
        "loss": "-log sigmoid(h(C,r+) - h(C,r-))", "score_position": "last reply token",
        "input_profile": input_profile(max_seq_len),
        "args": {"max_steps": max_steps, "eval_every": eval_every, "patience": patience, "batch_size": batch_size,
                 "lr": lr, "seed": seed, "device": device, "max_seq_len": max_seq_len, "optimizer": "AdamW",
                 "trainable": "all weights + score head"},
        "stop_reason": stop_reason, "steps_run": step, "best_step": best["step"], "best_val_loss": best["loss"],
        "best_val_accuracy": best["accuracy"],
        "pairs_file_sha256": _file_sha(pairs_path), "train_pairs": len(pairs), "val_pairs": len(val_pairs),
        "train_pair_ids_sha": sha256_json(sorted(p["pair_id"] for p in pairs)),
        "val_pair_ids_sha": sha256_json(sorted(p["pair_id"] for p in val_pairs)),
        "train_chat_rooms": sorted({p["chat_room_id"] for p in pairs}),
        "val_chat_rooms": sorted({p["chat_room_id"] for p in val_pairs}),
        "train_cohorts": sorted({p["cohort_id"] for p in pairs}), "val_cohorts": sorted({p["cohort_id"] for p in val_pairs}),
        "train_videos": len({p["video_id"] for p in pairs}),
        "train_history": train_history, "val_history": val_history, "started_at": started_at,
        "finished_at": iso(now_utc()), "seconds": round(time.monotonic() - started, 1),
    }
    (out_dir / META_FILE).write_text(json.dumps(meta, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    return {k: meta[k] for k in ("model_name", "base_model", "base_revision", "base_model_id", "train_pairs", "val_pairs",
                                 "train_chat_rooms", "val_chat_rooms", "train_cohorts", "val_cohorts", "args",
                                 "stop_reason", "steps_run", "best_step", "best_val_loss", "best_val_accuracy",
                                 "val_history", "seconds")} | {
        "out_dir": str(out_dir), "first_loss": train_history[0]["loss"], "last_loss": train_history[-1]["loss"]}


# ───────────────────────── 평가 ─────────────────────────

def replaceable(base: dict[str, Any], candidate: dict[str, Any], min_improvement: float = DEFAULT_MIN_IMPROVEMENT) -> bool:
    """같은 TEST 쌍에서 새 모델 정확도 − θ0 정확도 ≥ min_improvement(경계 포함). 맞힌 수로 계산한다(반올림 전 값)."""
    if not base["pairs"] or base["pairs"] != candidate["pairs"]:
        return False
    return (candidate["correct"] - base["correct"]) / base["pairs"] >= min_improvement - 1e-9


def evaluate(pairs_path: Path, candidate_dir: Path, *, device: str = "cpu", max_seq_len: int = DEFAULT_MAX_SEQ_LEN,
             batch_size: int = 16, min_eval_pairs: int = DEFAULT_MIN_EVAL_PAIRS,
             min_improvement: float = DEFAULT_MIN_IMPROVEMENT) -> dict[str, Any]:
    """test 분할(최신 기수, 학습·검증에 쓰지 않은 채팅방)에서 θ0 vs 새 모델.
    쌍이 min_eval_pairs 미만이면 INCONCLUSIVE(정확도는 참고로 남긴다), 이상이고 새 모델 정확도가 θ0 보다
    min_improvement(기본 0.02 = 2%p) 이상 높을 때만 REPLACEABLE, 아니면 KEEP_BASE."""
    if min_eval_pairs < 1:
        raise SystemExit("min_eval_pairs ≥ 1")
    if not 0 <= min_improvement < 1:
        raise SystemExit("min_improvement ∈ [0, 1)")
    meta_path = candidate_dir / META_FILE
    if not meta_path.exists():
        raise SystemExit(f"{meta_path} 가 없다(train 결과 디렉터리가 아니다)")
    meta = json.loads(meta_path.read_text(encoding="utf-8"))
    require_base(meta.get("base_model"), meta.get("base_revision"))
    pairs = load_pairs(pairs_path, TEST)
    rooms = sorted({p["chat_room_id"] for p in pairs})
    cohorts = sorted({p["cohort_id"] for p in pairs})
    used_rooms = set(meta["train_chat_rooms"]) | set(meta["val_chat_rooms"])
    overlap = sorted(set(rooms) & used_rooms)
    if overlap:
        raise SystemExit(f"test 채팅방이 학습·검증 채팅방과 겹친다: {overlap}")
    cohort_overlap = sorted(set(cohorts) & (set(meta["train_cohorts"]) | set(meta["val_cohorts"])))
    if cohort_overlap:
        raise SystemExit(f"test 기수가 학습·검증 기수와 겹친다: {cohort_overlap}")
    result: dict[str, Any] = {"pairs": len(pairs), "videos": len({p["video_id"] for p in pairs}), "chat_rooms": rooms,
                              "cohorts": cohorts, "train_chat_rooms": meta["train_chat_rooms"],
                              "val_chat_rooms": meta["val_chat_rooms"], "min_eval_pairs": min_eval_pairs,
                              "min_improvement": min_improvement,
                              "candidate_dir": str(candidate_dir), "best_step": meta.get("best_step")}
    if not pairs:
        return result | {"decision": "INCONCLUSIVE", "replaceable": False, "reason": "test 분할에 쌍이 없다"}
    _check_profile(pairs, max_seq_len)
    scored = {}
    for name in ("base", "candidate"):
        scorer = base_scorer(device, max_seq_len)[0] if name == "base" else \
            LocalScorer(str(candidate_dir), None, device, max_seq_len)
        pos, neg = pairwise_scores(scorer, pairs, batch_size)
        scored[name] = {"model_id": scorer.model_id, **pair_accuracy(pos, neg)}
        del scorer
    if len(pairs) < min_eval_pairs:
        return result | scored | {"decision": "INCONCLUSIVE", "replaceable": False,
                                  "reason": f"test 쌍 {len(pairs)}개 < 최소 {min_eval_pairs}개"}
    better = replaceable(scored["base"], scored["candidate"], min_improvement)
    return result | scored | {
        "decision": "REPLACEABLE" if better else "KEEP_BASE", "replaceable": better,
        "replace_how": "교체는 depth-serve 가 읽는 모델을 이 디렉터리로 바꾸는 것이다(자동으로 하지 않는다)."}
