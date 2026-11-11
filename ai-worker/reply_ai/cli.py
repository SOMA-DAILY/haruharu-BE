"""reply-ai 명령. 비밀키 값은 출력하지 않는다."""
from __future__ import annotations

import argparse
import json
import os
import sys
from pathlib import Path
from typing import Any

from .config import Settings, load_dotenv_secret


def _settings(args: argparse.Namespace) -> Settings:
    files = list(getattr(args, "dotenv", None) or [])
    files += [f for f in os.environ.get("REPLY_AI_DOTENV", "").split(",") if f.strip()]
    for dotenv in files:
        load_dotenv_secret(dotenv.strip())
    return Settings.from_env()


def _conn(settings: Settings):
    from .db import connect
    return connect(settings.database_url)


def _print(value: Any) -> None:
    print(json.dumps(value, ensure_ascii=False, indent=2, default=str))


def cmd_worker(args: argparse.Namespace) -> int:
    from .worker import Worker, setup_logging
    settings = _settings(args)
    setup_logging(args.log_level)
    worker = Worker(settings)
    types = args.types.split(",") if args.types else None
    if args.once:
        conn = _conn(settings)
        processed = 0
        while worker.run_once(conn, worker.owner(), types) is not None:
            processed += 1
            if processed >= args.max_jobs:
                break
        _print({"processed": processed})
        return 0
    worker.serve(job_types=types)
    return 0


def cmd_embedding_serve(args: argparse.Namespace) -> int:
    from .embedding_service import PINNED_REVISION, serve
    from .worker import setup_logging
    setup_logging("INFO")
    serve(args.host, args.port, os.environ.get("REPLY_AI_EMBEDDING_REVISION", PINNED_REVISION), args.device)
    return 0


def cmd_depth_serve(args: argparse.Namespace) -> int:
    from .depth_service import PINNED_REVISION, serve
    from .worker import setup_logging
    setup_logging("INFO")
    serve(args.host, args.port, os.environ.get("REPLY_AI_DEPTH_REVISION", PINNED_REVISION), args.device, args.max_seq_len)
    return 0


def cmd_depth(args: argparse.Namespace) -> int:
    """Depth 예측·실측 연결(export-outcomes)·비교 데이터셋(build-pairs)·추가 학습(train)·평가(eval)."""
    from . import depth_outcomes as outcomes
    if args.action == "export-outcomes":
        from .depth import DepthClient
        settings = _settings(args)
        conn = _conn(settings)
        if args.scorer == "serve":
            scorer = outcomes.ServeScorer(DepthClient(settings))
        elif args.scorer == "local":
            from .depth_train import LocalScorer
            scorer = LocalScorer(args.model, args.revision, args.device, args.max_seq_len)
        else:
            scorer = None
        provider = None
        if args.translate == "provider":
            from .provider import OpenAIProvider
            provider = OpenAIProvider(settings)
        translator = outcomes.Translator(conn, settings, provider, Path(args.translation_cache) if args.translation_cache else None,
                                         cache_context=args.include_context_text)
        rows, meta = outcomes.export_outcomes(conn, settings, scorer=scorer, translator=translator,
                                              thresholds=outcomes.LinkThresholds(args.as_is_threshold, args.edited_threshold),
                                              include_context_text=args.include_context_text)
        outcomes.write_jsonl(Path(args.out), rows)
        summary = {"out": args.out, **meta, "metrics": outcomes.summarize(rows, args.min_depth_diff, args.max_hour_gap or None)}
        if args.summary:
            Path(args.summary).write_text(json.dumps(summary, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
        _print(summary)
        return 0
    if args.action == "effect-report":
        from . import effect_report as effect
        conn = _conn(_settings(args))
        rows = effect.fetch_rows(conn, args.cohort, outcomes.LinkThresholds(args.as_is_threshold, args.edited_threshold))
        kwargs = {"min_per_arm": args.min_per_arm, "iterations": args.bootstrap, "seed": args.seed}
        assigned = effect.fetch_assigned(conn)
        result = ({"cohort": args.cohort,
                   **effect.report(rows, assigned=effect.assigned_for(assigned, args.cohort), **kwargs)}
                  if args.cohort is not None else {"by_cohort": effect.by_cohort(rows, assigned, **kwargs)})
        if args.out:
            Path(args.out).write_text(json.dumps(result, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
        _print(result)
        return 0
    from . import depth_train
    if args.action == "build-pairs":
        rows = outcomes.read_jsonl(Path(args.outcomes))
        encode = depth_train.tokenizer_encoder(depth_train.scorer_tokenizer())
        pairs, stats = depth_train.build_pairs(rows, encode=encode, min_depth_diff=args.min_depth_diff,
                                               max_hour_gap=args.max_hour_gap or None, val_fraction=args.val_fraction,
                                               max_seq_len=args.max_seq_len)
        outcomes.write_jsonl(Path(args.out), pairs)
        _print({"out": args.out, "outcome_rows": len(rows), **stats})
        return 0
    if args.action == "train":
        _print(depth_train.train(Path(args.pairs), Path(args.out), max_steps=args.max_steps, eval_every=args.eval_every,
                                 patience=args.patience, batch_size=args.batch_size, lr=args.lr, seed=args.seed,
                                 device=args.device, max_seq_len=args.max_seq_len))
        return 0
    result = depth_train.evaluate(Path(args.pairs), Path(args.candidate), device=args.device,
                                  max_seq_len=args.max_seq_len, batch_size=args.batch_size,
                                  min_eval_pairs=args.min_eval_pairs)
    _print(result)
    return 0


def cmd_init_version(args: argparse.Namespace) -> int:
    from . import versions
    settings = _settings(args)
    _print(versions.init_version(_conn(settings), args.gallery, args.note))
    return 0


def _embedder(settings: Settings, offline: bool):
    from .embedding import HashEmbedder, make_embedder
    return HashEmbedder() if offline else make_embedder(settings)


def cmd_gallery_reindex(args: argparse.Namespace) -> int:
    from .gallery import reindex
    settings = _settings(args)
    _print(reindex(_conn(settings), _embedder(settings, args.offline_embedder), args.gallery))
    return 0


def cmd_embedding(args: argparse.Namespace) -> int:
    """관리형 임베딩(Cloudflare) 연결 시험·점검 기준 만들기(probe)와 출력 변화 점검(check)."""
    from .embedding import PROBE_FILE, CloudflareEmbedder, make_embedder, make_probe_baseline
    from .resources import ROOT
    settings = _settings(args)
    embedder = make_embedder(settings)
    if not isinstance(embedder, CloudflareEmbedder):
        raise SystemExit("REPLY_AI_EMBEDDING_PROVIDER=cloudflare_workers_ai 일 때만 쓴다")
    if args.action == "probe":
        baseline = make_probe_baseline(embedder)
        summary = {k: v for k, v in baseline.items() if k not in ("vectors", "texts")} | {"texts": len(baseline["texts"])}
        if args.write:
            (ROOT / PROBE_FILE).write_text(json.dumps(baseline, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
            summary["written"] = PROBE_FILE
        _print(summary)
        return 0
    result = embedder.check_drift()
    _print(result)
    return 0 if result["stable"] else 3


def cmd_gallery_import(args: argparse.Namespace) -> int:
    from .gallery import import_gallery
    settings = _settings(args)
    data = json.loads(Path(args.file).read_text(encoding="utf-8"))
    _print(import_gallery(_conn(settings), _embedder(settings, args.offline_embedder), data, args.note))
    return 0


def cmd_version(args: argparse.Namespace) -> int:
    from . import versions
    settings = _settings(args)
    conn = _conn(settings)
    if args.action == "show":
        current = versions.active(conn)
        _print(None if current is None else {k: current[k] for k in ("version_id", "revision", "preprocess_version",
                                                                      "gallery_snapshot_id", "status", "created_at")})
    elif args.action == "use-gallery":
        # 로컬 개발용: 활성 버전에서 참고 사례 묶음만 바꾼 새 버전을 만들어 CAS 로 활성화한다(운영에서는 검증 게이트를 거친다).
        current = versions.active(conn)
        row = versions.derive(conn, current, "dev", gallery_snapshot_id=args.gallery, note=args.reason)
        revision = versions.activate(conn, row["version_id"], current["version_id"], current["revision"], args.reason)
        _print({"version_id": row["version_id"], "revision": revision, "gallery_snapshot_id": args.gallery})
    elif args.action == "use-package":
        # 지시문·설정은 버전에 묶여 있어 파일을 바꿔도 활성 버전은 그대로다. 패키지 내용으로 새 버전을 만들어 활성화한다.
        _print(versions.use_package(conn, args.reason))
    elif args.action == "rollback":
        _print(versions.rollback(conn, args.version_id, args.reason))
    return 0


def cmd_batch(args: argparse.Namespace) -> int:
    from .feedback.engine import Engine, schedule
    from .provider import OpenAIProvider
    settings = _settings(args)
    conn = _conn(settings)
    if args.action == "run-scheduled":
        from .feedback.engine import run_scheduled
        from .worker import Worker, setup_logging
        setup_logging("INFO")
        result = run_scheduled(conn, settings, Worker(settings), deadline_seconds=args.deadline_minutes * 60)
        _print(result)
        return 0 if result["status"] != "PENDING" else 2
    if args.action == "tick":
        scheduled = schedule(conn, settings, force=args.force)
        if not scheduled:
            _print({"status": "idle"})
            return 0
        engine = Engine(conn, settings, OpenAIProvider(settings), owner="cli")
        _print(engine.tick(scheduled["batch_run_id"]))
    elif args.action == "status":
        rows = conn.execute("""SELECT id, status, trigger_reason, lo_closure_seq, hi_closure_seq, baseline_version_id,
                                      candidate_version_id, spent_usd, result ->> 'decision' AS decision, created_at, finished_at
                                 FROM ai_reply.batch_run ORDER BY id DESC LIMIT 10""").fetchall()
        _print(rows)
    return 0


def cmd_probe_schema(args: argparse.Namespace) -> int:
    from .probe import probe_strict_keywords
    settings = _settings(args)
    _print(probe_strict_keywords(settings, write=args.write))
    return 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(prog="reply-ai", description="하루하루 영상 답장 추천 AI 작업기(#558)")
    parser.add_argument("--dotenv", action="append",
                        help="OPENAI_API_KEY·CLOUDFLARE_* 만 읽어 올 .env 경로(여러 번 가능, 값은 출력하지 않음)")
    sub = parser.add_subparsers(dest="command", required=True)

    p = sub.add_parser("worker", help="작업 대기열 처리")
    p.add_argument("--once", action="store_true", help="대기 작업을 처리하고 끝낸다")
    p.add_argument("--max-jobs", type=int, default=100)
    p.add_argument("--types", help="처리할 job_type(쉼표)")
    p.add_argument("--log-level", default="INFO")
    p.set_defaults(func=cmd_worker)

    p = sub.add_parser("embedding-serve", help="임베딩 서비스(Qwen3-Embedding-0.6B, revision 고정)")
    p.add_argument("--host", default="127.0.0.1")
    p.add_argument("--port", type=int, default=18452)
    p.add_argument("--device", default=os.environ.get("REPLY_AI_EMBEDDING_DEVICE", "cpu"))
    p.set_defaults(func=cmd_embedding_serve)

    p = sub.add_parser("depth-serve", help="Depth 점수 서비스(microsoft/DialogRPT-depth, revision 고정)")
    p.add_argument("--host", default="127.0.0.1")
    p.add_argument("--port", type=int, default=18453)
    p.add_argument("--device", default=os.environ.get("REPLY_AI_DEPTH_DEVICE", "cpu"))
    p.add_argument("--max-seq-len", type=int, default=int(os.environ.get("REPLY_AI_DEPTH_MAX_SEQ_LEN", "50")),
                   help="맥락+답장 토큰 상한(학습 기본 50). 넘으면 맥락 앞부터 자른다")
    p.set_defaults(func=cmd_depth_serve)

    p = sub.add_parser("depth", help="Depth 예측·실측 연결·비교 데이터셋·추가 학습·평가")
    dsub = p.add_subparsers(dest="action", required=True)

    from .depth_service import MODEL_ID as DEPTH_MODEL_ID, PINNED_REVISION as DEPTH_REVISION

    def model_args(d: argparse.ArgumentParser, revision: bool = True) -> None:
        if revision:   # train·eval 의 기준은 θ0(고정 revision)뿐이라 받지 않는다
            d.add_argument("--revision", default=os.environ.get("REPLY_AI_DEPTH_REVISION", DEPTH_REVISION),
                           help="공개 가중치 revision(기본: REPLY_AI_DEPTH_REVISION 또는 고정 revision)")
        d.add_argument("--device", default=os.environ.get("REPLY_AI_DEPTH_DEVICE", "cpu"))
        d.add_argument("--max-seq-len", type=int, default=int(os.environ.get("REPLY_AI_DEPTH_MAX_SEQ_LEN", "50")))

    d = dsub.add_parser("export-outcomes", help="실제 답장 1건 = 1행: 추천 당시 예측 + 실제 문장·점수·Depth + 연결 구분, 지표 요약")
    d.add_argument("--out", required=True, help="결과 jsonl")
    d.add_argument("--summary", help="요약 JSON 도 파일로 저장")
    d.add_argument("--scorer", choices=["serve", "local", "none"], default="serve",
                   help="실제 문장 점수: depth-serve(REPLY_AI_DEPTH_URL) | 로컬 가중치(--model) | 매기지 않음")
    d.add_argument("--model", default=DEPTH_MODEL_ID, help="--scorer local: 허브 id 또는 train 결과 디렉터리")
    d.add_argument("--translate", choices=["provider", "off"], default="provider",
                   help="실제 문장·C 영어 변환: 기존 provider(depth_translate, 온라인 일일 한도) | 캐시만")
    d.add_argument("--translation-cache", help="원문 sha256 → 영어 jsonl(읽고 이어 쓴다, 원문은 넣지 않는다). "
                                              "기본은 답장 번역만 남긴다")
    d.add_argument("--include-context-text", action="store_true",
                   help="단체 대화 원문(한국어 C)을 결과에 넣고 C 번역도 캐시에 남긴다(기본: sha 만). 파일은 파기 대상이 아니니 쓰고 지운다")
    d.add_argument("--as-is-threshold", type=float, default=0.9, help="문자 유사도 ≥ 이 값: 카드 그대로")
    d.add_argument("--edited-threshold", type=float, default=0.4, help="문자 유사도 ≥ 이 값: 카드 보고 수정(아래는 직접 작성)")
    d.add_argument("--min-depth-diff", type=int, default=1, help="지표의 쌍 비교에 쓰는 최소 depth 차이")
    d.add_argument("--max-hour-gap", type=float, default=1.0, help="지표의 쌍 비교: 두 답장 전송 시각 차 상한(시간, 0 이면 보지 않음)")
    model_args(d)
    d.set_defaults(func=cmd_depth)

    d = dsub.add_parser("effect-report", help="추천 효과: 무작위 비교 그룹(카드 받음/안 받음)별 게시자 응답률, 차이와 95%% 구간"
                                              "(채팅방 단위 bootstrap), 대조 확인, 연결 구분별 참고")
    d.add_argument("--cohort", type=int, help="이 기수만(없으면 기수별로 모두)")
    d.add_argument("--out", help="결과 JSON 도 파일로 저장")
    d.add_argument("--min-per-arm", type=int, default=330, help="그룹당 답장이 이보다 적으면 INCONCLUSIVE")
    d.add_argument("--bootstrap", type=int, default=2000, help="bootstrap 반복 수")
    d.add_argument("--seed", type=int, default=0)
    d.add_argument("--as-is-threshold", type=float, default=0.9, help="참고 연결 구분: 카드 그대로 유사도 기준")
    d.add_argument("--edited-threshold", type=float, default=0.4, help="참고 연결 구분: 카드 보고 수정 유사도 기준")
    d.set_defaults(func=cmd_depth)

    d = dsub.add_parser("build-pairs", help="export-outcomes 결과 → 같은 C 의 (C, r+, r−) 쌍, 최신 기수 test·이전 기수 "
                                            "채팅방 단위 val/train 분할, 입력이 같은 쌍(INPUT_COLLISION) 제외")
    d.add_argument("--outcomes", required=True)
    d.add_argument("--out", required=True)
    d.add_argument("--min-depth-diff", type=int, default=1)
    d.add_argument("--max-hour-gap", type=float, default=1.0,
                   help="두 답장 전송 시각 차가 이 시간을 넘으면 쌍에서 뺀다(논문 §3.1, 기본 1, 0 이면 보지 않음)")
    d.add_argument("--val-fraction", type=float, default=0.2,
                   help="최신 기수 이전 기수의 채팅방 중 검증(조기 종료용)으로 보낼 비율(채팅방 id 해시)")
    d.add_argument("--max-seq-len", type=int, default=int(os.environ.get("REPLY_AI_DEPTH_MAX_SEQ_LEN", "50")),
                   help="입력 해시(INPUT_COLLISION)를 잴 점수 입력 토큰 상한(train·eval 과 같아야 한다)")
    d.set_defaults(func=cmd_depth)

    d = dsub.add_parser("train", help="train 분할로 공개 DialogRPT-depth θ0(고정 revision)에서 학습"
                                      "(−log σ(h(C,r+) − h(C,r−)), val 손실 조기 종료)")
    d.add_argument("--pairs", required=True)
    d.add_argument("--out", required=True, help="결과 가중치 디렉터리(비어 있어야 한다)")
    d.add_argument("--max-steps", type=int, default=2000, help="안전 상한(보통 조기 종료로 먼저 멈춘다)")
    d.add_argument("--eval-every", type=int, default=10, help="이 step 마다 val 손실을 잰다")
    d.add_argument("--patience", type=int, default=3, help="val 손실이 이 횟수만큼 연속으로 나아지지 않으면 멈춘다")
    d.add_argument("--batch-size", type=int, default=8)
    d.add_argument("--lr", type=float, default=1e-5)
    d.add_argument("--seed", type=int, default=0)
    model_args(d, revision=False)
    d.set_defaults(func=cmd_depth)

    d = dsub.add_parser("eval", help="test 분할(최신 기수)에서 θ0 vs 새 모델 쌍 비교 정확도 → 교체 가능 판정")
    d.add_argument("--pairs", required=True)
    d.add_argument("--candidate", required=True, help="train 결과 디렉터리")
    d.add_argument("--batch-size", type=int, default=16)
    d.add_argument("--min-eval-pairs", type=int, default=30, help="평가 쌍이 이보다 적으면 INCONCLUSIVE(교체 판정 안 함)")
    model_args(d, revision=False)
    d.set_defaults(func=cmd_depth)

    p = sub.add_parser("init-version", help="첫 버전·활성 포인터 등록(멱등)")
    p.add_argument("--gallery", help="시작할 gallery_snapshot id(기본: 빈 묶음)")
    p.add_argument("--note")
    p.set_defaults(func=cmd_init_version)

    p = sub.add_parser("gallery", help="참고 사례")
    gsub = p.add_subparsers(dest="action", required=True)
    g = gsub.add_parser("import", help="합성·QA 사례 가져오기 → 새 gallery_snapshot")
    g.add_argument("file")
    g.add_argument("--note")
    g.add_argument("--offline-embedder", action="store_true", help="테스트용 해시 임베더(실제 모델과 섞이지 않음)")
    g.set_defaults(func=cmd_gallery_import)
    g = gsub.add_parser("reindex", help="묶음 전체를 지금 임베딩 버전으로 채우기(이어서 가능, 활성화는 안 함)")
    g.add_argument("--gallery", required=True)
    g.add_argument("--offline-embedder", action="store_true")
    g.set_defaults(func=cmd_gallery_reindex)

    p = sub.add_parser("embedding", help="관리형 임베딩 연결 시험(probe)·출력 변화 점검(check)")
    p.add_argument("action", choices=["probe", "check"])
    p.add_argument("--write", action="store_true", help="probe 결과를 resources/embedding_probe.json 에 저장")
    p.set_defaults(func=cmd_embedding)

    p = sub.add_parser("version", help="버전 보기·전환(use-gallery: 참고 사례만, use-package: 패키지 지시문·설정)·롤백")
    p.add_argument("action", choices=["show", "use-gallery", "use-package", "rollback"])
    p.add_argument("--gallery")
    p.add_argument("--version-id")
    p.add_argument("--reason", default="manual")
    p.set_defaults(func=cmd_version)

    p = sub.add_parser("batch", help="피드백 배치")
    p.add_argument("action", choices=["run-scheduled", "tick", "status"])
    p.add_argument("--force", action="store_true")
    p.add_argument("--deadline-minutes", type=float, default=120, help="run-scheduled 가 재시도를 기다리는 최대 시간")
    p.set_defaults(func=cmd_batch)

    p = sub.add_parser("probe-schema", help="strict 구조화 출력이 받는 스키마 키워드 확인(실제 호출)")
    p.add_argument("--write", action="store_true", help="결과를 resources/strict_schema_support.json 에 저장")
    p.set_defaults(func=cmd_probe_schema)

    args = parser.parse_args(argv)
    return args.func(args)


if __name__ == "__main__":
    sys.exit(main())
