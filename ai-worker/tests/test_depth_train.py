"""Depth 예측·실측 연결(export-outcomes)·비교 데이터셋(build-pairs)·추가 학습(train)·평가(eval)."""
from __future__ import annotations

import json
import uuid
from datetime import timedelta
from pathlib import Path

import pytest

from reply_ai import depth_outcomes as outcomes
from reply_ai import depth_train
from reply_ai.config import Settings
from reply_ai.provider import ScriptedProvider
from reply_ai.util import sha256_text

from .conftest import T0
from .test_depth import depth_server  # noqa: F401 (fixture)

SQL_559 = Path(__file__).resolve().parents[2] / "scripts" / "sql" / "559-reply-depth-up.sql"
TH = outcomes.LinkThresholds(0.9, 0.4)


# ───────────────────────── 행 만들기(단위) ─────────────────────────

def row(message_id, video_id, depth, *, room_id=7, status="OBSERVED", text_en="reply", context_en=("ctx",),
        link="SELF_WRITTEN", score=None, model="m", text_ko="답장", minute=0, owner_replied=None, followups=None):
    observed = status == "OBSERVED"
    return {"message_id": message_id, "video_id": video_id, "chat_room_id": room_id,
            "actual": {"text_ko": text_ko, "text_en": text_en, "score": score, "score_model_id": model if score is not None else None,
                       "sent_at": None if minute is None else f"2026-11-08T{3 + minute // 60:02d}:{minute % 60:02d}:00Z"},
            "label": {"status": status, "depth": depth if observed else None, "width": 0 if observed else None,
                      "unobserved_reason": None if observed else "CHAIN_BROKEN", "owner_replied": owner_replied,
                      "followup_message_count": followups},
            "link": {"kind": link},
            "context": {"context_en": list(context_en) if context_en else None, "context_sha": f"sha-{video_id}"}}


def cards(*examples):
    return [{"card_id": f"c{i}", "example_ko": e} for i, e in enumerate(examples, 1)]


def test_link_kind_by_char_similarity_with_recorded_thresholds():
    shown = cards("무슨 과제예요?", "마감 화이팅이에요!")
    as_is = outcomes.classify_link("마감  화이팅이에요!", shown, TH)
    assert (as_is["kind"], as_is["matched_card_id"], as_is["similarity"]) == ("CARD_AS_IS", "c2", 1.0)
    assert outcomes.classify_link("마감 화이팅이에요 오늘 밤 꼭 끝내요", shown, TH)["kind"] == "CARD_EDITED"
    assert outcomes.classify_link("ㅋㅋ 커피 몇 잔째", shown, TH)["kind"] == "SELF_WRITTEN"
    none = outcomes.classify_link("아무 말", [], TH)
    assert none["kind"] == "NO_RECOMMENDATION" and none["similarity"] is None
    assert outcomes.classify_link(None, shown, TH)["kind"] is None          # 수집 불가 문장은 판정하지 않는다
    assert as_is["as_is_threshold"] == 0.9 and as_is["edited_threshold"] == 0.4
    assert outcomes.classify_link("같다", cards("같다", "같다"), TH)["matched_card_id"] == "c1"   # 동점이면 앞 카드
    with pytest.raises(ValueError):
        outcomes.LinkThresholds(0.3, 0.5)


def test_pairs_only_within_same_video_deeper_is_positive_ties_and_small_diffs_excluded():
    rows = [row(1, 10, 3), row(2, 10, 1), row(3, 10, 1), row(4, 10, 2),
            row(5, 20, 0), row(6, 20, 4),
            row(7, 30, 5),                                                  # 혼자 → single_reply
            row(8, 10, None, status="UNOBSERVED"), row(9, 10, 2, text_en=None, text_ko=None),
            row(10, 10, 2, text_en=None), row(11, 40, 1, context_en=None)]
    pairs, stats = outcomes.make_pairs(rows, min_depth_diff=1)
    assert {p["pair_id"] for p in pairs} == {"10:1>2", "10:1>3", "10:1>4", "10:4>2", "10:4>3", "20:6>5"}
    assert all(p["pos"]["depth"] > p["neg"]["depth"] for p in pairs)
    assert stats["excluded"] == {"context_unavailable": 1, "english_unavailable": 1, "single_reply": 1, "text_unavailable": 1,
                                 "tie": 1, "unobserved": 1}
    pairs2, stats2 = outcomes.make_pairs(rows, min_depth_diff=2)
    assert {p["pair_id"] for p in pairs2} == {"10:1>2", "10:1>3", "20:6>5"}
    assert stats2["excluded"]["below_min_diff"] == 3
    with pytest.raises(ValueError):
        outcomes.make_pairs(rows, min_depth_diff=0)


def test_pairs_need_replies_sent_within_max_hour_gap():
    rows = [row(1, 10, 3, minute=0), row(2, 10, 1, minute=50), row(3, 10, 0, minute=70), row(4, 10, 2, minute=None)]
    pairs, stats = outcomes.make_pairs(rows)                                   # 기본 1시간(논문 §3.1)
    assert {p["pair_id"] for p in pairs} == {"10:1>2", "10:2>3"} and stats["max_hour_gap"] == 1.0
    assert stats["excluded"]["over_hour_gap"] == 1 and stats["excluded"]["sent_at_unknown"] == 3
    assert next(p for p in pairs if p["pair_id"] == "10:1>2")["hour_gap"] == pytest.approx(50 / 60, abs=1e-4)
    loose, loose_stats = outcomes.make_pairs(rows, max_hour_gap=None)          # 보지 않으면 시각 없는 답장도 쓴다
    assert len(loose) == 6 and "over_hour_gap" not in loose_stats["excluded"]
    assert len(outcomes.make_pairs(rows, max_hour_gap=2)[0]) == 3
    _, built = depth_train.build_pairs(rows, max_hour_gap=1.0, eval_chat_room_ids=set())
    assert built["excluded"]["over_hour_gap"] == 1
    with pytest.raises(ValueError):
        outcomes.make_pairs(rows, max_hour_gap=0)


def test_summary_owner_reply_rate_overall_and_by_link_keeps_blank_apart():
    rows = [row(1, 10, 2, link="CARD_AS_IS", owner_replied=True, followups=3),
            row(2, 10, 0, link="CARD_AS_IS", owner_replied=False, followups=0),
            row(3, 10, 1, link="SELF_WRITTEN", owner_replied=None, followups=None),
            row(4, 10, None, status="UNOBSERVED", link="SELF_WRITTEN")]
    s = outcomes.summarize(rows)
    assert s["owner_reply"] == {"owner_reply_known": 2, "owner_replied": 1, "owner_reply_rate": 0.5,
                                "followup_known": 2, "mean_followup_messages": 1.5}
    assert s["mean_depth_by_link"]["CARD_AS_IS"]["owner_reply_rate"] == 0.5
    assert s["mean_depth_by_link"]["SELF_WRITTEN"]["owner_reply_rate"] is None          # 모르면 null(0 과 다르다)
    assert s["mean_depth_by_link"]["SELF_WRITTEN"]["owner_reply_known"] == 0


def test_spearman_and_pair_accuracy():
    assert outcomes.spearman([0.1, 0.2, 0.3, 0.4], [0, 1, 2, 3]) == 1.0
    assert outcomes.spearman([0.4, 0.3, 0.2, 0.1], [0, 1, 2, 3]) == -1.0
    assert outcomes.spearman([0.1, 0.2, 0.2, 0.4], [1, 2, 3, 4]) == pytest.approx(0.9487, abs=1e-4)  # 동순위 평균
    assert outcomes.spearman([0.1, 0.2], [1, 2]) is None
    assert outcomes.spearman([0.1, 0.2, 0.3], [1, 1, 1]) is None
    assert outcomes.pair_accuracy([0.9, 0.5, 0.3], [0.1, 0.5, 0.4]) == {"pairs": 3, "correct": 1, "ties": 1, "accuracy": 0.3333}
    assert outcomes.pair_accuracy([], [])["accuracy"] is None


def test_summary_keeps_blank_and_zero_apart():
    rows = [row(1, 10, 0, link="CARD_AS_IS", score=0.2), row(2, 10, 2, link="CARD_EDITED", score=0.6),
            row(3, 10, 1, link="SELF_WRITTEN", score=0.4), row(4, 10, None, status="UNOBSERVED", link="NO_RECOMMENDATION")]
    s = outcomes.summarize(rows)
    assert (s["replies"], s["observed"], s["unobserved"], s["observed_depth_zero"]) == (4, 3, 1, 1)
    assert s["unobserved_reasons"] == {"CHAIN_BROKEN": 1}
    keys = ("replies", "observed", "unobserved", "mean_depth")
    assert {k: s["mean_depth_by_link"]["CARD_AS_IS"][k] for k in keys} == \
        {"replies": 1, "observed": 1, "unobserved": 0, "mean_depth": 0.0}
    assert {k: s["mean_depth_by_link"]["NO_RECOMMENDATION"][k] for k in keys} == \
        {"replies": 1, "observed": 0, "unobserved": 1, "mean_depth": None}
    assert s["spearman_score_vs_depth"] == {"n": 3, "rho": 1.0}
    assert s["pairwise_same_context"]["accuracy"] == 1.0 and s["pairwise_same_context"]["pairs"] == 3


def test_build_pairs_splits_by_room_never_across():
    rows = [row(i * 10 + j, i, j, room_id=100 + i % 6) for i in range(12) for j in range(3)]
    pairs, stats = depth_train.build_pairs(rows, eval_chat_room_ids={101, 104})
    assert {p["chat_room_id"] for p in pairs if p["split"] == "eval"} == {101, 104}
    assert not set(stats["split"]["train"]["chat_rooms"]) & set(stats["split"]["eval"]["chat_rooms"])
    assert stats["split"]["eval"]["pairs"] + stats["split"]["train"]["pairs"] == stats["pairs"] == 36
    hashed, hstats = depth_train.build_pairs(rows, eval_fraction=0.5)
    assert hashed == depth_train.build_pairs(rows, eval_fraction=0.5)[0]                    # 결정적
    by_room = {}
    for p in hashed:
        by_room.setdefault(p["chat_room_id"], set()).add(p["split"])
    assert all(len(s) == 1 for s in by_room.values())
    assert hstats["split_rule"] == {"eval_chat_room_ids": None, "eval_fraction": 0.5}


# ───────────────────────── 학습·평가(작은 무작위 GPT-2) ─────────────────────────

@pytest.fixture(scope="module")
def tiny_model_dir(tmp_path_factory):
    torch = pytest.importorskip("torch")
    transformers = pytest.importorskip("transformers")
    from reply_ai.depth_service import EOS_ID, MODEL_ID, PINNED_REVISION
    try:
        tokenizer = transformers.AutoTokenizer.from_pretrained(MODEL_ID, revision=PINNED_REVISION, local_files_only=True)
    except OSError:
        pytest.skip("DialogRPT-depth tokenizer not cached")
    torch.manual_seed(0)
    config = transformers.GPT2Config(vocab_size=50257, n_positions=64, n_embd=32, n_layer=2, n_head=2, num_labels=1,
                                     pad_token_id=EOS_ID)
    out = tmp_path_factory.mktemp("tiny")
    transformers.GPT2ForSequenceClassification(config).save_pretrained(out)
    tokenizer.save_pretrained(out)
    return out


def test_local_scorer_takes_each_rows_last_reply_token(tiny_model_dir):
    torch = pytest.importorskip("torch")
    from reply_ai.depth_service import build_ids

    scorer = depth_train.LocalScorer(str(tiny_model_dir), None, "cpu", 50)
    contexts, replies = [["I love NLP!"], ["How are you doing today, friend?"]], ["Me too!", "fine"]
    with torch.no_grad():
        batched = scorer.logits(contexts, replies).tolist()
        for c, r, value in zip(contexts, replies, batched):
            ids = torch.tensor([build_ids(scorer._encode, c, r, 50)])
            alone = scorer.model.score(scorer.model.transformer(input_ids=ids).last_hidden_state)[0, -1, 0]
            assert value == pytest.approx(float(alone), abs=1e-5)


def pair(pid, room, pos, neg, split):
    return {"pair_id": pid, "video_id": int(pid.split(":")[0]), "chat_room_id": room, "context_en": ["We finished the project!"],
            "context_sha": "s", "depth_diff": 2, "pos": {"text_en": pos}, "neg": {"text_en": neg}, "split": split}


def test_train_lowers_pairwise_loss_and_eval_compares_against_base(tiny_model_dir, tmp_path):
    pairs = [pair("1:a>b", 1, "What did you build? Tell me more", "ok", "train"),
             pair("2:a>b", 2, "How long did it take you?", "nice", "train"),
             pair("3:a>b", 3, "Which part was hardest for you?", "cool", "eval")]
    pairs_path = tmp_path / "pairs.jsonl"
    outcomes.write_jsonl(pairs_path, pairs)
    out = tmp_path / "ft"
    result = depth_train.train(pairs_path, out, base=str(tiny_model_dir), revision=None, steps=25, batch_size=2, lr=1e-3,
                               seed=0)
    assert result["train_pairs"] == 2 and result["train_chat_rooms"] == [1, 2]
    assert result["last_loss"] < result["first_loss"]
    meta = json.loads((out / depth_train.META_FILE).read_text())
    assert meta["loss"] == "-log sigmoid(h(C,r+) - h(C,r-))" and meta["args"]["max_seq_len"] == 50
    assert (out / "model.safetensors").exists()
    with pytest.raises(SystemExit):
        depth_train.train(pairs_path, out, base=str(tiny_model_dir), revision=None, steps=1)   # 덮어쓰지 않는다

    report = depth_train.evaluate(pairs_path, out, base=str(tiny_model_dir), revision=None)
    assert report["pairs"] == 1 and report["chat_rooms"] == [3] and report["min_eval_pairs"] == 30
    assert (report["decision"], report["replaceable"]) == ("INCONCLUSIVE", False)   # 평가 쌍 1개 < 최소 30개
    assert report["base"]["pairs"] == report["candidate"]["pairs"] == 1              # 정확도는 참고로 남긴다
    report = depth_train.evaluate(pairs_path, out, base=str(tiny_model_dir), revision=None, min_eval_pairs=1)
    assert report["decision"] in ("REPLACEABLE", "KEEP_BASE")
    assert report["replaceable"] == (report["candidate"]["accuracy"] > report["base"]["accuracy"])

    leaked = tmp_path / "leaked.jsonl"
    outcomes.write_jsonl(leaked, [pair("4:a>b", 1, "x?", "y", "eval")])
    with pytest.raises(SystemExit, match="겹친다"):
        depth_train.evaluate(leaked, out, base=str(tiny_model_dir), revision=None)


# ───────────────────────── export-outcomes(PostgreSQL) ─────────────────────────

class KeywordScorer:
    def __init__(self, model_id="kw-depth#len50"):
        self.requests = []
        self.model_id = model_id

    def score(self, context_en, replies_en):
        self.requests.append((list(context_en), list(replies_en)))
        return [0.9 if "?" in r else 0.1 for r in replies_en], self.model_id


def _seed_559(conn):
    conn.execute(SQL_559.read_text(encoding="utf-8"))
    # 재답장 칸(559 표에 더해지는 중). 이미 있으면 그대로 둔다.
    conn.execute("""ALTER TABLE ai_reply.reply_depth_observation ADD COLUMN IF NOT EXISTS owner_replied BOOLEAN,
                    ADD COLUMN IF NOT EXISTS followup_message_count INTEGER""")
    conn.execute("TRUNCATE ai_reply.reply_depth_observation")


def _reply(conn, *, message_id, set_id, card_id, text, depth_value, provenance="QA", status="OBSERVED", viewer_id=3,
           video_id=101, room_id=7, exposed=True, sent_minutes=0, owner_replied=None, followups=None):
    episode_id = None
    if exposed:
        episode_id = conn.execute(
            """INSERT INTO ai_reply.reply_episode (session_id, set_id, request_id, viewer_id, video_id, room_id,
                   first_impression_at, last_impression_at, actual_reply_message_id, outcome, closed_at, provenance)
               VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, 'REPLIED', %s, %s) RETURNING id""",
            (str(uuid.uuid4()), set_id, str(uuid.uuid4()), viewer_id, video_id, room_id, T0, T0, message_id, T0,
             provenance)).fetchone()["id"]
        conn.execute("""INSERT INTO ai_reply.episode_reply_snapshot (episode_id, message_id, reply_text, reply_sha, provenance)
                        VALUES (%s, %s, %s, %s, %s)""", (episode_id, message_id, text, sha256_text(text), provenance))
    conn.execute(
        """INSERT INTO ai_reply.reply_link (message_id, viewer_id, video_id, sent_at, source_set_id, source_card_id,
               source_status, attribution, episode_id) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s)""",
        (message_id, viewer_id, video_id, T0 + timedelta(minutes=sent_minutes), set_id if card_id else None, card_id,
         "VALID" if card_id else "NONE",
         "explicit" if card_id else ("inferred" if exposed else "unexposed"), episode_id))
    observed = status == "OBSERVED"
    conn.execute(
        """INSERT INTO ai_reply.reply_depth_observation (message_id, video_id, viewer_id, room_id, source_set_id, source_card_id,
               width, depth, status, unobserved_reason, window_start, window_end, observed_at, provenance)
           VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)""",
        (message_id, video_id, viewer_id, room_id, set_id if card_id else None, card_id, 1 if observed else None,
         depth_value if observed else None, status, None if observed else "CHAIN_BROKEN", T0, T0 + timedelta(hours=24),
         T0 + timedelta(hours=25), provenance))
    conn.execute("""UPDATE ai_reply.reply_depth_observation SET owner_replied = %s, followup_message_count = %s
                     WHERE message_id = %s""", (owner_replied, followups, message_id))


def _translate_tagged(request):
    p = request.payload
    return {"context_en": ("EN " + " / ".join(t["text"] for t in p["context_turns"]))[:240] if p["context_turns"] else "",
            "candidates": [{"card_id": c["card_id"], "text_en": "EN " + c["text"] + "?" * ("과제" in c["text"])}
                           for c in p["candidates"]]}


@pytest.mark.pg
def test_export_links_prediction_to_actual_outcome(conn, database_url, sample_video, depth_server, tmp_path):
    from .test_depth import FakeDepthModel, generic_provider, run_generic

    _seed_559(conn)
    model = FakeDepthModel({"밤새면": 0.9, "화이팅": 0.5, "무슨 과제": 0.1})
    rset, attempt = run_generic(conn, database_url, sample_video, generic_provider(), depth_ranking_enabled=True,
                                depth_url=depth_server(model))
    shown = {c["example"]: c for c in rset["internal_cards"]}
    sid = rset["set_id"]
    _reply(conn, message_id=1, set_id=sid, card_id=shown["밤새면 너무 피곤하죠"]["card_id"], text="밤새면 너무 피곤하죠", depth_value=0)
    _reply(conn, message_id=2, set_id=sid, card_id=shown["무슨 과제예요?"]["card_id"], text="무슨 과제예요? 저도 마감이라", depth_value=3)
    _reply(conn, message_id=3, set_id=sid, card_id=None, text="오 커피 몇 잔째임 ㅋㅋ 과제 화이팅", depth_value=2)
    _reply(conn, message_id=4, set_id=None, card_id=None, text="", depth_value=1, exposed=False)
    _reply(conn, message_id=5, set_id=sid, card_id=None, text="ㅋㅋ 그 과제 나도 함", depth_value=None, status="UNOBSERVED")
    _reply(conn, message_id=6, set_id=sid, card_id=None, text="실사용 답장", depth_value=4, provenance="REAL_USER")

    provider = ScriptedProvider({"depth_translate": _translate_tagged})
    cache = tmp_path / "cache.jsonl"
    scorer = KeywordScorer("fake-depth#len50")                                       # 세트에 기록된 모델과 같은 모델
    rows, meta = outcomes.export_outcomes(conn, Settings(), scorer=scorer,
                                          translator=outcomes.Translator(conn, Settings(), provider, cache), thresholds=TH)
    by_id = {r["message_id"]: r for r in rows}
    assert sorted(by_id) == [1, 2, 3, 4, 5] and meta["skipped_provenance"] == 1        # 실사용 수집이 꺼져 있다
    r1, r2, r3, r4, r5 = (by_id[i] for i in range(1, 6))
    # 추천 쪽: 노출 순서·예문·추천 당시 예측 점수·model_id
    assert [c["example_ko"] for c in r1["recommendation"]["cards"]] == [c["example"] for c in rset["internal_cards"]]
    assert [c["order"] for c in r1["recommendation"]["cards"]] == [1, 2, 3]
    assert r1["recommendation"]["cards"][0]["predicted_score"] == 0.9
    assert r1["recommendation"]["cards"][0]["model_id"] == "fake-depth#len50"
    assert r1["recommendation"]["selected_card_id"] == shown["밤새면 너무 피곤하죠"]["card_id"]
    assert r3["recommendation"]["selected_card_id"] is None and r3["recommendation"]["exposed"]
    # 연결 구분
    assert [r["link"]["kind"] for r in (r1, r2, r3, r4)] == ["CARD_AS_IS", "CARD_EDITED", "SELF_WRITTEN", "NO_RECOMMENDATION"]
    assert r5["link"]["kind"] == "SELF_WRITTEN" and r5["label"]["depth"] is None and r5["label"]["status"] == "UNOBSERVED"
    # 실제 쪽: 카드 그대로면 그 영어를 쓰고 변환하지 않는다. 노출 없는 답장은 수집 출처가 없어 문장이 없다.
    assert r1["actual"]["text_en"] == shown["밤새면 너무 피곤하죠"]["depth"]["example_en"]
    sent = [c.payload for c in provider.calls]
    assert len(sent) == 1 and sent[0]["context_turns"] == []                          # C 는 추천 당시 영어 C 를 쓴다
    assert sorted(c["text"] for c in sent[0]["candidates"]) == sorted(["무슨 과제예요? 저도 마감이라", "오 커피 몇 잔째임 ㅋㅋ 과제 화이팅", "ㅋㅋ 그 과제 나도 함"])
    assert {k: r4["actual"][k] for k in ("text_ko", "text_en", "score", "score_model_id", "score_status")} == \
        {"text_ko": None, "text_en": None, "score": None, "score_model_id": None, "score_status": "NOT_SCORED"}
    assert r2["actual"]["score"] == 0.9 and r2["actual"]["score_model_id"] == "fake-depth#len50"
    assert r2["actual"]["score_status"] == "OK" and r2["actual"]["set_model_id"] == "fake-depth#len50"
    assert r2["actual"]["sent_at"] == "2026-11-08T03:00:00Z" and meta["outcome_columns"] == ["owner_replied", "followup_message_count"]
    assert r1["context"]["context_en"] == attempt["candidates"]["depth_ranking"]["context_en"] == scorer.requests[0][0]
    assert r1["context"]["source"] == "recommendation" and r1["context"]["context_ko"] is None    # 단체 대화 원문은 기본 미출력
    assert r1["context"]["context_ko_sha"] and meta["include_context_text"] is False
    assert "밤샐" not in json.dumps(rows, ensure_ascii=False)
    assert "밤샐" not in cache.read_text() and len(cache.read_text().splitlines()) == 3   # 캐시에 원문 없음

    summary = outcomes.summarize(rows)
    assert summary["unobserved"] == 1 and summary["observed_depth_zero"] == 1
    assert summary["mean_depth_by_link"]["CARD_EDITED"]["mean_depth"] == 3.0
    assert summary["pairwise_same_context"]["pairs"] == 3 and summary["spearman_score_vs_depth"]["n"] == 3

    # 두 번째 내보내기는 캐시만으로 같은 영어를 얻는다(추가 호출 없음). 실사용 수집을 켜면 그 답장도 들어온다.
    again, meta2 = outcomes.export_outcomes(conn, Settings(real_user_feedback_enabled=True), scorer=scorer,
                                            translator=outcomes.Translator(conn, Settings(), None, cache), thresholds=TH)
    assert len(provider.calls) == 1 and meta2["skipped_provenance"] == 0
    assert {r["message_id"]: r["actual"]["text_en"] for r in again if r["message_id"] != 6} == \
        {r["message_id"]: r["actual"]["text_en"] for r in rows}
    assert {r["message_id"] for r in again} == {1, 2, 3, 4, 5, 6}

    # 지금 모델이 세트에 기록된 모델과 다르면 점수를 남기지 않는다(MODEL_MISMATCH).
    other, meta3 = outcomes.export_outcomes(conn, Settings(), scorer=KeywordScorer("other-depth#len50"),
                                            translator=outcomes.Translator(conn, Settings(), None, cache), thresholds=TH)
    o2 = next(r for r in other if r["message_id"] == 2)
    assert (o2["actual"]["score"], o2["actual"]["score_model_id"], o2["actual"]["score_status"]) == (None, None, "MODEL_MISMATCH")
    assert o2["actual"]["set_model_id"] == "fake-depth#len50" and o2["actual"]["scorer_model_id"] == "other-depth#len50"
    assert meta3["score_model_ids"] == [] and outcomes.summarize(other)["score_status"]["MODEL_MISMATCH"] == 4


@pytest.mark.pg
def test_export_without_ranking_record_translates_common_context(conn, database_url, sample_video, tmp_path):
    from .test_depth import generic_provider, run_generic

    _seed_559(conn)
    rset, _ = run_generic(conn, database_url, sample_video, generic_provider())               # 랭킹 꺼짐(기본)
    _reply(conn, message_id=1, set_id=rset["set_id"], card_id=None, text="무슨 과제예요?", depth_value=2, owner_replied=True,
           followups=2)
    _reply(conn, message_id=2, set_id=rset["set_id"], card_id=None, text="힘내요", depth_value=0, sent_minutes=40,
           owner_replied=False, followups=0)
    _reply(conn, message_id=3, set_id=rset["set_id"], card_id=None, text="늦은 답장", depth_value=5, sent_minutes=120)
    provider = ScriptedProvider({"depth_translate": _translate_tagged})
    rows, _ = outcomes.export_outcomes(conn, Settings(), scorer=KeywordScorer(),
                                       translator=outcomes.Translator(conn, Settings(), provider, None), thresholds=TH)
    sent = provider.calls[0].payload
    assert [t["turn_id"] for t in sent["context_turns"]] == ["chat:1", "video"]                  # P 없이 공통 C 만
    assert rows[0]["context"]["source"] == "translated" and rows[0]["context"]["context_en"][0].startswith("EN ")
    assert all(c["predicted_score"] is None for c in rows[0]["recommendation"]["cards"])          # 당시 예측 없음 = null
    assert rows[0]["link"]["kind"] == "CARD_AS_IS" and rows[1]["link"]["kind"] == "SELF_WRITTEN"
    pairs, stats = outcomes.make_pairs(rows)
    assert [p["pair_id"] for p in pairs] == ["101:1>2"] and stats["excluded"]["over_hour_gap"] == 2   # 3 은 1시간 넘게 늦다
    # 세트에 기록된 모델이 없으면(랭킹 꺼짐) 지정한 모델 id 로 점수를 남긴다.
    assert rows[0]["actual"]["set_model_id"] is None and rows[0]["actual"]["score_model_id"] == "kw-depth#len50"
    assert (rows[0]["label"]["owner_replied"], rows[0]["label"]["followup_message_count"]) == (True, 2)
    assert rows[2]["label"]["owner_replied"] is None
    assert outcomes.summarize(rows)["owner_reply"]["owner_reply_rate"] == 0.5
    spent = conn.execute("SELECT reserved_usd, spent_usd FROM ai_reply.budget_day").fetchone()
    assert spent["reserved_usd"] == 0 and spent["spent_usd"] > 0                               # 온라인 한도로 예약·정산
    assert rows[0]["context"]["context_ko"] is None


@pytest.mark.pg
def test_context_text_and_its_translation_kept_only_with_flag(conn, database_url, sample_video, tmp_path):
    from .test_depth import generic_provider, run_generic

    _seed_559(conn)
    rset, _ = run_generic(conn, database_url, sample_video, generic_provider())
    _reply(conn, message_id=1, set_id=rset["set_id"], card_id=None, text="힘내요", depth_value=0)
    for flag in (False, True):
        cache = tmp_path / f"cache-{flag}.jsonl"
        provider = ScriptedProvider({"depth_translate": _translate_tagged})
        rows, meta = outcomes.export_outcomes(conn, Settings(), scorer=None, include_context_text=flag, thresholds=TH,
                                              translator=outcomes.Translator(conn, Settings(), provider, cache, cache_context=flag))
        cached = cache.read_text()
        assert meta["include_context_text"] is flag and rows[0]["context"]["context_en"][0].startswith("EN 내일까지")
        if flag:
            assert rows[0]["context"]["context_ko"][0] == "내일까지 과제 내야 해서 밤샐 듯"
            assert len(cached.splitlines()) == 2 and "EN 내일까지" in cached                        # 답장 1 + C 1
        else:
            assert rows[0]["context"]["context_ko"] is None
            assert len(cached.splitlines()) == 1 and "EN 내일까지" not in cached                    # 답장 번역만


@pytest.mark.pg
def test_revoked_or_purged_set_is_unavailable_not_no_recommendation(conn, database_url, sample_video):
    from .test_depth import generic_provider, run_generic

    _seed_559(conn)
    rset, _ = run_generic(conn, database_url, sample_video, generic_provider())
    card = rset["internal_cards"][0]
    _reply(conn, message_id=1, set_id=rset["set_id"], card_id=card["card_id"], text=card["example"], depth_value=2)
    _reply(conn, message_id=2, set_id=None, card_id=None, text="", depth_value=1, exposed=False)
    _reply(conn, message_id=3, set_id=str(uuid.uuid4()), card_id=None, text="세트 행이 없다", depth_value=0)

    def export():
        rows, _ = outcomes.export_outcomes(conn, Settings(), scorer=None, thresholds=TH,
                                           translator=outcomes.Translator(conn, Settings(), None, None))
        return {r["message_id"]: r for r in rows}

    assert export()[1]["link"]["kind"] == "CARD_AS_IS"
    for change in ("UPDATE ai_reply.recommendation_set SET revoked_at = now(), revoke_reason = 'VIDEO_DELETED'",
                   "UPDATE ai_reply.recommendation_set SET revoked_at = NULL, cards = '[]'::jsonb, internal_cards = '[]'::jsonb"):
        conn.execute(change)
        rows = export()
        assert rows[1]["link"]["kind"] == "RECOMMENDATION_UNAVAILABLE"
        assert rows[1]["recommendation"]["set_status"] == "UNAVAILABLE" and rows[1]["recommendation"]["exposed"] is None
        assert rows[1]["recommendation"]["cards"] == [] and rows[1]["recommendation"]["set_id"] == rset["set_id"]
        assert rows[2]["link"]["kind"] == "NO_RECOMMENDATION" and rows[2]["recommendation"]["set_status"] == "NONE"
        assert rows[3]["link"]["kind"] == "RECOMMENDATION_UNAVAILABLE"
        summary = outcomes.summarize(list(rows.values()))
        assert summary["mean_depth_by_link"]["RECOMMENDATION_UNAVAILABLE"]["replies"] == 2
        assert summary["mean_depth_by_link"]["NO_RECOMMENDATION"]["replies"] == 1
