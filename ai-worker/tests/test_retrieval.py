"""6장 검색: 질의 형식, 제외 규칙, 상위 3편·답장 5개."""
from datetime import timedelta

import numpy as np

from reply_ai.embedding import HashEmbedder, check_vectors
from reply_ai.resources import package_config
from reply_ai.retrieval import Current, Protected, document_text, query_text, retrieve

from .conftest import T0

INSTRUCTION = package_config()["embedding"]["query_instruction"]


def test_model01_query_instruction_applied_once_and_vectors_valid():
    """MODEL01: query instruction 은 한 번만, 문서에는 붙이지 않는다. 1024 유한·정규화."""
    q = query_text(INSTRUCTION, "화이팅", "TV 야구 중계")
    assert q == f"Instruct: {INSTRUCTION}\nQuery:화이팅\nTV 야구 중계"
    assert q.count("Instruct:") == 1 and "Instruct" not in document_text("화이팅", "TV 야구 중계")
    vectors = HashEmbedder().embed([q, document_text("화이팅", "TV")])
    check_vectors(vectors, 2)
    assert np.allclose(HashEmbedder().embed([q]), vectors[:1])


def ref(rid, room=None, posted=-60, **kw):
    return {"id": rid, "reference_key": f"ref-{rid}", "source_video_id": kw.get("video"), "source_room_id": room,
            "caption": f"캡션{rid}", "current_visual_description": f"설명{rid}", "video_posted_at": T0 + timedelta(minutes=posted),
            "media_sha": kw.get("media"), "frame_shas": kw.get("frames", []), "media_cluster_id": None,
            "approval": kw.get("approval", "APPROVED"), "protected": kw.get("protected", False), "deleted_at": None}


def pair(pid, rid, minutes=-30, text="화이팅!"):
    return {"id": pid, "reference_video_id": rid, "reply_key": f"r{pid}", "source_reply_id": None, "sanitized_text": text,
            "reply_posted_at": T0 + timedelta(minutes=minutes), "approval": "APPROVED", "eligible": True, "deleted_at": None}


def run(refs, pairs, scores, protected=None, current=None):
    vectors = {}
    for rid, score in scores.items():  # 질의와의 코사인이 score 가 되도록 만든 벡터
        v = np.zeros(1024, dtype=np.float32)
        v[0], v[1] = score, np.sqrt(1 - score ** 2)
        vectors[rid] = v
    query = np.zeros(1024, dtype=np.float32)
    query[0] = 1.0
    current = current or Current(video_id=500, room_id=7, published_at=T0, media_sha="m-current", frame_shas={"f-current"})
    return retrieve(current, refs, pairs, vectors, query, protected or Protected(), query="q",
                    embedding_version={"model_id": "test"}, gallery_snapshot_id="g")


def test_ret01_skip_no_reply_and_same_room_then_take_next():
    """RET01: 상위 영상에 답장이 없거나 같은 방이면 빼고 다음 적격 영상."""
    refs = [ref(1), ref(2, room=7), ref(3), ref(4)]
    pairs = {2: [pair(20, 2)], 3: [pair(30, 3)], 4: [pair(40, 4)]}
    found = run(refs, pairs, {1: 0.99, 2: 0.98, 3: 0.5, 4: 0.4})
    assert [r["reference_key"] for r in found.record["ranked"]] == ["ref-3", "ref-4"]
    assert {(e["reference_key"], e["reason"]) for e in found.record["excluded"]} == {("ref-1", "no_replies"), ("ref-2", "same_room")}


def test_ret02_three_question_references_all_allowed():
    """RET02: 참고 영상 세 개가 모두 질문 답장이어도 세 사례 모두 입력."""
    refs = [ref(i) for i in (1, 2, 3, 4)]
    pairs = {i: [pair(i * 10, i, text="어디예요?")] for i in (1, 2, 3, 4)}
    found = run(refs, pairs, {1: 0.9, 2: 0.8, 3: 0.7, 4: 0.6})
    assert [r["reference_id"] for r in found.references] == ["R1", "R2", "R3"]
    assert all(r["replies"][0]["text"] == "어디예요?" for r in found.references)


def test_ret03_duplicate_media_future_and_protected_are_excluded():
    """RET03: 평가 대상의 중복 영상·미래 답장·보호 대상은 검색 입력에서 제외."""
    refs = [ref(1, media="m-current"), ref(2, frames=["f-current"]), ref(3, posted=5), ref(4, video=900), ref(5), ref(6)]
    pairs = {i: [pair(i * 10, i)] for i in range(1, 7)}
    pairs[5] = [pair(50, 5, minutes=10)]  # 게시 뒤 답장뿐
    found = run(refs, pairs, {i: 0.9 - i * 0.01 for i in range(1, 7)}, protected=Protected(videos={"900"}))
    reasons = {e["reference_key"]: e["reason"] for e in found.record["excluded"]}
    assert reasons == {"ref-1": "media_duplicate", "ref-2": "frame_overlap", "ref-3": "future_video", "ref-4": "protected",
                       "ref-5": "no_replies"}
    assert [r["reference_key"] for r in found.record["ranked"]] == ["ref-6"]


def test_at_most_five_replies_in_time_order_and_no_threshold():
    refs = [ref(1)]
    pairs = {1: [pair(i, 1, minutes=-i) for i in range(1, 8)]}
    found = run(refs, pairs, {1: 0.01})  # 유사도가 낮아도 임계값으로 자르지 않는다
    assert [s["reply_key"] for s in found.record["selected_replies"]] == ["r7", "r6", "r5", "r4", "r3"]
    assert found.record["cosine_threshold"] is None
