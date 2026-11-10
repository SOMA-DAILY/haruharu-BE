"""Depth 평가 모델 추가 학습·평가(`reply-ai depth build-pairs | train | eval`). extra `depth`(torch·transformers) 전용.

- 비교 데이터셋: export-outcomes 결과(실제 답장 1건 = 1행)에서 같은 영상(같은 C)의 실제 답장끼리 depth 큰 쪽을 r+ 로 한
  (C, r+, r−). 동점은 빼고 최소 차이는 인자. 학습·평가는 채팅방(chat_room_id = reply_depth_observation.room_id =
  chat_messages.chat_room_id, 3일 방 rooms.id 가 아니다) 단위로 나눈다(같은 채팅방이 양쪽에 들어가지 않는다).
- 손실: L = −log σ(h(C, r+) − h(C, r−)) (DialogRPT 논문 식 3). h = 답장 마지막 토큰 위치의 logit.
- 입력·점수 위치는 depth-serve 와 같다(depth_service.build_ids·pad_batch, transformer 은닉상태 + score 헤드를 직접 계산해
  각 줄 길이−1 위치에서 꺼낸다). 시작 가중치는 공개 microsoft/DialogRPT-depth(고정 revision).
- 평가: 학습에 쓰지 않은 채팅방의 쌍에서 공개 모델과 새 모델의 쌍 비교 정확도. 평가 쌍이 최소 수(기본 30) 미만이면
  INCONCLUSIVE, 이상이고 새 모델이 더 높을 때만 교체 가능으로 판정한다.
  교체(depth-serve 가 읽는 모델 바꾸기)는 자동으로 하지 않는다.
- 점수는 같은 C 의 답장끼리 비교하는 값이다. 확률로 해석하지 않는다.
"""
from __future__ import annotations

import hashlib
import json
import math
import random
import time
from pathlib import Path
from typing import Any, Sequence

from .depth_outcomes import DEFAULT_MAX_HOUR_GAP, make_pairs, pair_accuracy
from .depth_service import (DEFAULT_MAX_SEQ_LEN, MODEL_ID, PINNED_REVISION, REFERENCE_CHECKS, REFERENCE_TOLERANCE,
                            build_ids, pad_batch, sigmoid)
from .util import iso, now_utc, sha256_json

DEFAULT_MIN_EVAL_PAIRS = 30
TRAIN = "train"
EVAL = "eval"
META_FILE = "depth_train.json"


# ───────────────────────── 비교 데이터셋 ─────────────────────────

def room_split(chat_room_id: int, eval_chat_room_ids: set[int] | None, eval_fraction: float) -> str:
    """채팅방 단위 분할. eval_chat_room_ids(chat_rooms.id — 미사용 기수 방들의 단체 채팅방)를 주면 그 채팅방만 평가,
    아니면 채팅방 id 해시로 eval_fraction 만큼."""
    if eval_chat_room_ids is not None:
        return EVAL if chat_room_id in eval_chat_room_ids else TRAIN
    bucket = int(hashlib.sha256(f"room:{chat_room_id}".encode()).hexdigest()[:8], 16) / 0x1_0000_0000
    return EVAL if bucket < eval_fraction else TRAIN


def build_pairs(rows: list[dict[str, Any]], *, min_depth_diff: int = 1, eval_chat_room_ids: set[int] | None = None,
                eval_fraction: float = 0.2, max_hour_gap: float | None = DEFAULT_MAX_HOUR_GAP
                ) -> tuple[list[dict[str, Any]], dict[str, Any]]:
    if not 0 <= eval_fraction <= 1:
        raise ValueError("eval_fraction ∈ [0, 1]")
    pairs, stats = make_pairs(rows, min_depth_diff, max_hour_gap)
    kept = []
    for pair in pairs:
        if pair["chat_room_id"] is None:
            stats["excluded"]["chat_room_unknown"] = stats["excluded"].get("chat_room_unknown", 0) + 1
            continue
        kept.append({**pair, "split": room_split(pair["chat_room_id"], eval_chat_room_ids, eval_fraction)})
    split = {s: {"pairs": sum(1 for p in kept if p["split"] == s),
                 "videos": len({p["video_id"] for p in kept if p["split"] == s}),
                 "chat_rooms": sorted({p["chat_room_id"] for p in kept if p["split"] == s})} for s in (TRAIN, EVAL)}
    stats.update(pairs=len(kept), videos=len({p["video_id"] for p in kept}), split=split,
                 split_rule={"eval_chat_room_ids": sorted(eval_chat_room_ids) if eval_chat_room_ids is not None else None,
                             "eval_fraction": None if eval_chat_room_ids is not None else eval_fraction})
    stats["excluded"] = dict(sorted(stats["excluded"].items()))
    return kept, stats


def load_pairs(path: Path, split: str) -> list[dict[str, Any]]:
    pairs = [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]
    return [p for p in pairs if p["split"] == split]


# ───────────────────────── 모델 ─────────────────────────

class LocalScorer:
    """공개 가중치(허브 id + 고정 revision) 또는 학습 결과 디렉터리를 불러 depth-serve 와 같은 방식으로 점수를 매긴다."""

    def __init__(self, source: str = MODEL_ID, revision: str | None = PINNED_REVISION, device: str = "cpu",
                 max_seq_len: int = DEFAULT_MAX_SEQ_LEN):
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
        self.local = local
        libs = f"tf{transformers.__version__}#torch{torch.__version__.split('+')[0]}#{device}"
        if local:
            meta = json.loads((Path(source) / META_FILE).read_text(encoding="utf-8")) if (Path(source) / META_FILE).exists() else {}
            self.model_id = f"{meta.get('model_name') or Path(source).name}#len{max_seq_len}#fp32#{libs}"
        else:
            self.model_id = f"{source}@{(revision or 'unpinned')[:12]}#len{max_seq_len}#fp32#{libs}"

    def _encode(self, text: str) -> list[int]:
        return self.tokenizer.encode(text, add_special_tokens=False)

    def logits(self, contexts: Sequence[Sequence[str]], replies: Sequence[str]):
        """줄마다 [맥락 EOS … 답장] → 답장 마지막 토큰(길이−1)의 logit. 학습 때는 기울기가 흐른다."""
        torch = self.torch
        rows = [build_ids(self._encode, c, r, self.max_seq_len) for c, r in zip(contexts, replies, strict=True)]
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


def _file_sha(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


# ───────────────────────── 학습 ─────────────────────────

def train(pairs_path: Path, out_dir: Path, *, base: str = MODEL_ID, revision: str | None = PINNED_REVISION,
          steps: int = 100, batch_size: int = 8, lr: float = 1e-5, seed: int = 0, device: str = "cpu",
          max_seq_len: int = DEFAULT_MAX_SEQ_LEN, log_every: int = 1) -> dict[str, Any]:
    """train 분할의 쌍으로 공개 가중치에서 이어서 학습한다. 결과 가중치·토크나이저·학습 기록을 out_dir 에 쓴다."""
    if steps < 1 or batch_size < 1 or not lr > 0:
        raise SystemExit("steps ≥ 1, batch_size ≥ 1, lr > 0")
    pairs = load_pairs(pairs_path, TRAIN)
    if not pairs:
        raise SystemExit("train 분할에 쌍이 없다(build-pairs 통계를 확인)")
    if out_dir.exists() and any(out_dir.iterdir()):
        raise SystemExit(f"{out_dir} 가 비어 있지 않다(덮어쓰지 않는다)")
    import torch

    random.seed(seed)
    torch.manual_seed(seed)
    scorer = LocalScorer(base, revision, device, max_seq_len)
    base_model_id = scorer.model_id
    reference = None if scorer.local else scorer.reference_check()
    model = scorer.model
    optimizer = torch.optim.AdamW(model.parameters(), lr=lr)
    order: list[int] = []
    rng = random.Random(seed)
    history = []
    started = time.monotonic()
    started_at = iso(now_utc())
    model.train()
    for step in range(1, steps + 1):
        batch = []
        while len(batch) < batch_size:
            if not order:
                order = list(range(len(pairs)))
                rng.shuffle(order)
            batch.append(pairs[order.pop()])
        contexts = [p["context_en"] for p in batch]
        h_pos = scorer.logits(contexts, [p["pos"]["text_en"] for p in batch])
        h_neg = scorer.logits(contexts, [p["neg"]["text_en"] for p in batch])
        loss = -torch.nn.functional.logsigmoid(h_pos - h_neg).mean()
        optimizer.zero_grad()
        loss.backward()
        optimizer.step()
        if step % log_every == 0 or step == steps:
            history.append({"step": step, "loss": round(float(loss.detach()), 6),
                            "batch_accuracy": round(float((h_pos > h_neg).float().mean()), 4)})
    model.eval()
    out_dir.mkdir(parents=True, exist_ok=True)
    model.save_pretrained(out_dir, safe_serialization=True)
    scorer.tokenizer.save_pretrained(out_dir)
    weights_sha = _file_sha(out_dir / "model.safetensors")
    meta = {
        "model_name": f"dialogrpt-depth-ft@{weights_sha[:12]}",
        "base_model": base, "base_revision": None if scorer.local else revision, "base_model_id": base_model_id,
        "base_reference_check": reference, "weights_sha256": weights_sha,
        "loss": "-log sigmoid(h(C,r+) - h(C,r-))", "score_position": "last reply token",
        "args": {"steps": steps, "batch_size": batch_size, "lr": lr, "seed": seed, "device": device,
                 "max_seq_len": max_seq_len, "optimizer": "AdamW"},
        "pairs_file_sha256": _file_sha(pairs_path), "train_pairs": len(pairs),
        "train_pair_ids_sha": sha256_json(sorted(p["pair_id"] for p in pairs)),
        "train_chat_rooms": sorted({p["chat_room_id"] for p in pairs}), "train_videos": len({p["video_id"] for p in pairs}),
        "history": history, "started_at": started_at, "finished_at": iso(now_utc()),
        "seconds": round(time.monotonic() - started, 1),
    }
    (out_dir / META_FILE).write_text(json.dumps(meta, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    return {k: meta[k] for k in ("model_name", "base_model_id", "train_pairs", "train_chat_rooms", "train_videos", "args",
                                 "seconds")} | {"out_dir": str(out_dir), "first_loss": history[0]["loss"],
                                                "last_loss": history[-1]["loss"], "history": history}


# ───────────────────────── 평가 ─────────────────────────

def evaluate(pairs_path: Path, candidate_dir: Path, *, base: str = MODEL_ID, revision: str | None = PINNED_REVISION,
             device: str = "cpu", max_seq_len: int = DEFAULT_MAX_SEQ_LEN, batch_size: int = 16,
             min_eval_pairs: int = DEFAULT_MIN_EVAL_PAIRS) -> dict[str, Any]:
    """eval 분할(학습에 쓰지 않은 채팅방)에서 공개 모델 vs 새 모델.
    평가 쌍이 min_eval_pairs 미만이면 INCONCLUSIVE(정확도는 참고로 남긴다), 이상이고 새 모델이 더 높을 때만 REPLACEABLE."""
    if min_eval_pairs < 1:
        raise SystemExit("min_eval_pairs ≥ 1")
    meta_path = candidate_dir / META_FILE
    if not meta_path.exists():
        raise SystemExit(f"{meta_path} 가 없다(train 결과 디렉터리가 아니다)")
    meta = json.loads(meta_path.read_text(encoding="utf-8"))
    pairs = load_pairs(pairs_path, EVAL)
    rooms = sorted({p["chat_room_id"] for p in pairs})
    overlap = sorted(set(rooms) & set(meta["train_chat_rooms"]))
    if overlap:
        raise SystemExit(f"평가 채팅방이 학습 채팅방과 겹친다: {overlap}")
    result: dict[str, Any] = {"pairs": len(pairs), "videos": len({p["video_id"] for p in pairs}), "chat_rooms": rooms,
                              "train_chat_rooms": meta["train_chat_rooms"], "min_eval_pairs": min_eval_pairs,
                              "candidate_dir": str(candidate_dir)}
    if not pairs:
        return result | {"decision": "INCONCLUSIVE", "replaceable": False, "reason": "eval 분할에 쌍이 없다"}
    scored = {}
    for name, source, rev in (("base", base, revision), ("candidate", str(candidate_dir), None)):
        scorer = LocalScorer(source, rev, device, max_seq_len)
        pos, neg = pairwise_scores(scorer, pairs, batch_size)
        scored[name] = {"model_id": scorer.model_id, **pair_accuracy(pos, neg)}
        del scorer
    if len(pairs) < min_eval_pairs:
        return result | scored | {"decision": "INCONCLUSIVE", "replaceable": False,
                                  "reason": f"평가 쌍 {len(pairs)}개 < 최소 {min_eval_pairs}개"}
    better = scored["candidate"]["accuracy"] > scored["base"]["accuracy"]
    return result | scored | {
        "decision": "REPLACEABLE" if better else "KEEP_BASE", "replaceable": better,
        "replace_how": "교체는 depth-serve 가 읽는 모델을 이 디렉터리로 바꾸는 것이다(자동으로 하지 않는다)."}
