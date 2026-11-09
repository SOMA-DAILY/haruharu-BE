-- #558: 영상 답장 추천 AI. 앱 서버(Java)와 Python AI 작업기가 함께 쓰는 별도 스키마 ai_reply 와
-- 영상 캡션 수정 이력(video_caption_revisions)을 만든다. 멱등이다(여러 번 실행해도 결과가 같다).
--
-- ai_reply 는 Hibernate 가 관리하지 않는다(엔티티 없음). 이 파일이 유일한 정의다.
-- 앱 배포 전에 적용한다: 운영은 시작할 때 스키마를 검증하고, 앱은 스위치와 무관하게 video_caption_revisions 를 쓴다.
-- 누가 어느 표를 쓰는지는 docs/engineering/reply-recommendation.md 에 있다.
-- 시각은 모두 timestamptz(UTC)다. 서비스 표(chat_messages 등)의 KST LocalDateTime 은 앱 서버가 변환해 넣는다.
-- 서비스 표를 가리키는 id(video_id, viewer_id, message_id …)에는 외래 키를 두지 않는다(원본은 소프트 삭제).
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';

-- 영상 캡션 수정 이력(서비스 표, video 도메인). 게시 시각 T 의 캡션을 복원해 과거 기록을 정확한 시점으로 넣는다.
CREATE TABLE IF NOT EXISTS video_caption_revisions (
    id               BIGSERIAL PRIMARY KEY,
    video_post_id    BIGINT       NOT NULL,
    previous_caption VARCHAR(100),
    new_caption      VARCHAR(100),
    changed_at       TIMESTAMP(6) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_video_caption_revisions_post ON video_caption_revisions (video_post_id, changed_at);

CREATE SCHEMA IF NOT EXISTS ai_reply;

-- ───────────────────────── 버전 · 비용 ─────────────────────────

-- 모델·프롬프트·전처리·참고 사례 묶음. 행 내용은 바꾸지 않는다(status 만 바뀐다).
CREATE TABLE IF NOT EXISTS ai_reply.version_registry (
    version_id          VARCHAR(64)  PRIMARY KEY,
    parent_version_id   VARCHAR(64),
    config_json         JSONB        NOT NULL,
    prompts_json        JSONB        NOT NULL,
    full_config_sha     VARCHAR(64)  NOT NULL,
    prompts_sha         VARCHAR(64)  NOT NULL,
    preprocess_version  VARCHAR(64)  NOT NULL,  -- 분석 프롬프트·전처리 설정·모델의 해시. 분석 재사용 판단에 쓴다
    gallery_snapshot_id VARCHAR(64)  NOT NULL,  -- 이 버전이 검색하는 참고 사례 묶음
    status              VARCHAR(20)  NOT NULL,  -- CANDIDATE | VALIDATED | ACTIVE | RETIRED | REJECTED
    validation_id       BIGINT,
    change_note         VARCHAR(500),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 활성 버전 포인터. 바꿀 때는 revision 으로 compare-and-swap 한다.
CREATE TABLE IF NOT EXISTS ai_reply.active_version (
    scope               VARCHAR(30)  PRIMARY KEY,  -- 'online'
    version_id          VARCHAR(64)  NOT NULL REFERENCES ai_reply.version_registry (version_id),
    revision            BIGINT       NOT NULL,
    previous_version_id VARCHAR(64),
    change_reason       VARCHAR(200),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 하루 비용 한도(KST 날짜). 단계 시작 전 예약(reserved)하고 끝나면 실제 사용(spent)으로 옮긴다.
CREATE TABLE IF NOT EXISTS ai_reply.budget_day (
    day          DATE          NOT NULL,
    scope        VARCHAR(30)   NOT NULL,  -- 'online'
    limit_usd    NUMERIC(10,4) NOT NULL,
    reserved_usd NUMERIC(12,6) NOT NULL DEFAULT 0,
    spent_usd    NUMERIC(12,6) NOT NULL DEFAULT 0,
    updated_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    PRIMARY KEY (day, scope)
);

-- ───────────────────────── 작업 대기열 ─────────────────────────

-- 게시 트랜잭션의 outbox 이자 작업기의 큐. 같은 job_key 는 한 번만 생긴다.
CREATE TABLE IF NOT EXISTS ai_reply.ai_job (
    id              BIGSERIAL    PRIMARY KEY,
    job_key         VARCHAR(200) NOT NULL UNIQUE,
    job_type        VARCHAR(30)  NOT NULL,  -- ANALYSIS | GENERIC | PERSONAL | FEEDBACK_BATCH | PURGE_VIDEO
    status          VARCHAR(20)  NOT NULL,  -- QUEUED | RUNNING | SUCCEEDED | RETRY_WAIT | FAILED | CANCELLED
    payload         JSONB        NOT NULL,
    priority        INT          NOT NULL DEFAULT 0,
    attempt_count   INT          NOT NULL DEFAULT 0,
    max_attempts    INT          NOT NULL DEFAULT 3,
    next_attempt_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    lease_owner     VARCHAR(100),
    lease_until     TIMESTAMPTZ,
    failure_code    VARCHAR(60),
    failure_detail  VARCHAR(300),           -- 원문 없이 짧은 진단만
    result          JSONB,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    started_at      TIMESTAMPTZ,
    finished_at     TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_ai_job_ready ON ai_reply.ai_job (status, next_attempt_at);
CREATE INDEX IF NOT EXISTS idx_ai_job_type_status ON ai_reply.ai_job (job_type, status);
-- 앱이 분석별 최신 일반 추천 작업을 찾는다(payload 의 analysis_id).
CREATE INDEX IF NOT EXISTS idx_ai_job_generic_analysis
    ON ai_reply.ai_job ((payload->>'analysis_id')) WHERE job_type = 'GENERIC';

-- ───────────────────────── 영상 이해 ─────────────────────────

-- 분석 요청(앱 서버). 게시 트랜잭션 또는 추천 요청 때 만든다. 같은 키의 유효 요청은 하나다.
-- 원본 삭제 등으로 무효가 되면 CANCELLED 로 두고 revision_no 를 올려 새로 만든다.
CREATE TABLE IF NOT EXISTS ai_reply.analysis_request (
    id                 BIGSERIAL    PRIMARY KEY,
    video_id           BIGINT       NOT NULL,
    room_id            BIGINT       NOT NULL,
    author_id          BIGINT       NOT NULL,
    scope_hash         VARCHAR(64)  NOT NULL,  -- 시청 범위(차단 관계로 빼는 사람 목록)의 해시
    excluded_actor_ids JSONB        NOT NULL,
    caption_revision   VARCHAR(64)  NOT NULL,  -- sha256(캡션)
    media_revision     VARCHAR(64)  NOT NULL,  -- sha256(저장된 영상 주소)
    preprocess_version VARCHAR(64)  NOT NULL,
    revision_no        INT          NOT NULL DEFAULT 1,
    origin             VARCHAR(20)  NOT NULL,  -- PUBLISH | ENSURE
    status             VARCHAR(20)  NOT NULL,  -- SNAPSHOT_PENDING | SNAPSHOT_READY | SNAPSHOT_FAILED | CANCELLED
    analysis_input_id  BIGINT,
    attempt_count      INT          NOT NULL DEFAULT 0,
    next_attempt_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    claimed_until      TIMESTAMPTZ,
    failure_code       VARCHAR(60),
    cancel_reason      VARCHAR(60),
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_analysis_request_key
        UNIQUE (video_id, scope_hash, caption_revision, media_revision, preprocess_version, revision_no)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_analysis_request_active
    ON ai_reply.analysis_request (video_id, scope_hash, caption_revision, media_revision, preprocess_version)
    WHERE status <> 'CANCELLED';
CREATE INDEX IF NOT EXISTS idx_analysis_request_pending ON ai_reply.analysis_request (status, next_attempt_at);

-- 권한을 확인한 분석 입력 스냅샷(앱 서버). 게시 시각 T 이전·T 이전에 이용 가능했던 방 기록만 담는다.
CREATE TABLE IF NOT EXISTS ai_reply.analysis_input (
    id                    BIGSERIAL    PRIMARY KEY,
    analysis_request_id   BIGINT       NOT NULL UNIQUE,
    video_id              BIGINT       NOT NULL,
    room_id               BIGINT       NOT NULL,
    author_id             BIGINT       NOT NULL,
    published_at          TIMESTAMPTZ  NOT NULL,  -- T
    caption               TEXT         NOT NULL,
    caption_revision      VARCHAR(64)  NOT NULL,
    media_url             VARCHAR(500) NOT NULL,  -- 저장된 원본 주소(서명 없음). 작업기는 허용된 출처만 내려받는다
    media_revision        VARCHAR(64)  NOT NULL,
    scope_hash            VARCHAR(64)  NOT NULL,
    history_records       JSONB        NOT NULL,  -- analysis-input.schema.json 의 history_records 배열(role=room)
    history_snapshot_hash VARCHAR(64)  NOT NULL,
    history_status        VARCHAR(30)  NOT NULL,  -- COMPLETE | NO_RECORDS | SOURCE_UNAVAILABLE
    excluded_sources      JSONB        NOT NULL,  -- [{source_id, reason}] 원문 없음
    preprocess_version    VARCHAR(64)  NOT NULL,
    purged_at             TIMESTAMPTZ,            -- 보관 기간(30일)이 지나 원문(history_records)을 지운 시각
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 분석·개인화 입력에 들어간 원문 출처(원문 대신 해시). 원본이 삭제되면 파생 결과를 막는 데 쓴다.
CREATE TABLE IF NOT EXISTS ai_reply.evidence_source (
    id            BIGSERIAL    PRIMARY KEY,
    owner_kind    VARCHAR(20)  NOT NULL,  -- ANALYSIS | PERSONAL
    owner_id      BIGINT       NOT NULL,  -- analysis_input.id | personal_input.id
    source_id     VARCHAR(80)  NOT NULL,  -- chat:<message id> | video:<video id>
    source_kind   VARCHAR(30)  NOT NULL,  -- chat | video_description
    source_ref_id BIGINT       NOT NULL,
    actor_id      VARCHAR(40)  NOT NULL,  -- 사용자 id 문자열 또는 system
    occurred_at   TIMESTAMPTZ  NOT NULL,
    available_at  TIMESTAMPTZ  NOT NULL,
    revision      VARCHAR(64)  NOT NULL,
    text_sha      VARCHAR(64)  NOT NULL,
    role          VARCHAR(20)  NOT NULL,  -- room | own | interaction | parent_context
    cited         BOOLEAN      NOT NULL DEFAULT false,  -- 결과가 인용했는가(작업기가 표시)
    deleted_at    TIMESTAMPTZ,
    CONSTRAINT uq_evidence_source UNIQUE (owner_kind, owner_id, source_id)
);
CREATE INDEX IF NOT EXISTS idx_evidence_source_ref ON ai_reply.evidence_source (source_kind, source_ref_id);

-- 분석 결과(작업기). 분석 입력당 첫 유효 결과 하나만 저장한다.
CREATE TABLE IF NOT EXISTS ai_reply.analysis_snapshot (
    id                         BIGSERIAL     PRIMARY KEY,
    analysis_input_id          BIGINT        NOT NULL UNIQUE,
    analysis_key               VARCHAR(64)   NOT NULL UNIQUE,  -- sha256(video·media·caption·history·scope·preprocess)
    video_id                   BIGINT        NOT NULL,
    scope_hash                 VARCHAR(64)   NOT NULL,
    caption_revision           VARCHAR(64)   NOT NULL,
    media_revision             VARCHAR(64)   NOT NULL,
    history_snapshot_hash      VARCHAR(64)   NOT NULL,
    preprocess_version         VARCHAR(64)   NOT NULL,
    version_id                 VARCHAR(64)   NOT NULL,
    cutoff_at                  TIMESTAMPTZ   NOT NULL,
    input_sha                  VARCHAR(64)   NOT NULL,
    output_sha                 VARCHAR(64)   NOT NULL,
    media_sha                  VARCHAR(64),             -- 내려받은 영상 바이트의 sha256(중복 미디어 제외용)
    frames                     JSONB         NOT NULL,  -- [{frame_id, position_percent, timestamp_ms, width, height, sha256, …}]
    output_json                JSONB         NOT NULL,  -- analysis.schema.json 을 통과한 결과
    current_visual_description TEXT          NOT NULL,
    contextual_description     TEXT          NOT NULL,
    history_input_status       VARCHAR(30)   NOT NULL,  -- COMPLETE | NO_RECORDS | OVER_BUDGET | SOURCE_UNAVAILABLE
    history_requirement        VARCHAR(20)   NOT NULL,
    history_used               BOOLEAN       NOT NULL,
    model                      VARCHAR(60)   NOT NULL,
    provider_request_id        VARCHAR(120),
    usage                      JSONB,
    cost_usd                   NUMERIC(12,6),
    status                     VARCHAR(20)   NOT NULL,  -- READY | REVOKED
    revoked_at                 TIMESTAMPTZ,
    revoke_reason              VARCHAR(60),
    created_at                 TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_analysis_snapshot_video ON ai_reply.analysis_snapshot (video_id);

-- ───────────────────────── 참고 사례 · 임베딩 ─────────────────────────

-- 다른 방의 영상(허가·비식별된 사례만). 합성·QA 사례는 provenance 로 구분한다.
CREATE TABLE IF NOT EXISTS ai_reply.reference_video (
    id                         BIGSERIAL    PRIMARY KEY,
    reference_key              VARCHAR(80)  NOT NULL UNIQUE,
    source_video_id            BIGINT,
    source_room_id             BIGINT,
    caption                    TEXT         NOT NULL,
    current_visual_description TEXT         NOT NULL,
    video_posted_at            TIMESTAMPTZ  NOT NULL,
    media_sha                  VARCHAR(64),
    frame_shas                 JSONB        NOT NULL DEFAULT '[]',
    media_cluster_id           VARCHAR(64),
    provenance                 VARCHAR(20)  NOT NULL,  -- SYNTHETIC | QA | REAL_USER
    approval                   VARCHAR(20)  NOT NULL,  -- APPROVED | PENDING | REVOKED
    protected                  BOOLEAN      NOT NULL DEFAULT false,
    deleted_at                 TIMESTAMPTZ,
    created_at                 TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 참고 영상에 달린 실제 답장(적절성이 확인된 것만 eligible).
CREATE TABLE IF NOT EXISTS ai_reply.reference_pair (
    id                 BIGSERIAL    PRIMARY KEY,
    reference_video_id BIGINT       NOT NULL REFERENCES ai_reply.reference_video (id) ON DELETE CASCADE,
    reply_key          VARCHAR(80)  NOT NULL,
    source_reply_id    BIGINT,
    sanitized_text     TEXT         NOT NULL,
    reply_posted_at    TIMESTAMPTZ  NOT NULL,
    provenance         VARCHAR(20)  NOT NULL,
    approval           VARCHAR(20)  NOT NULL,
    eligible           BOOLEAN      NOT NULL DEFAULT true,
    deleted_at         TIMESTAMPTZ,
    created_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_reference_pair UNIQUE (reference_video_id, reply_key)
);

-- 참고 사례 묶음(불변). 사례를 더하면 새 id 를 만든다.
CREATE TABLE IF NOT EXISTS ai_reply.gallery_snapshot (
    id                  VARCHAR(64)  PRIMARY KEY,
    reference_video_ids JSONB        NOT NULL,
    content_sha         VARCHAR(64)  NOT NULL,
    status              VARCHAR(20)  NOT NULL,  -- CANDIDATE | ACTIVE | RETIRED
    note                VARCHAR(300),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 참고 영상 문서 벡터. 제공자·모델·revision·요청 방식·역할·차원·정규화가 하나라도 다르면 서로 비교하지 않는다.
-- 관리형(Cloudflare Workers AI)은 불변 revision 이 없어 model_revision 에 'unpinned-<점검 기준 id>' 를 남긴다.
-- 질의 벡터는 저장하지 않으므로 role 은 document 뿐이다.
CREATE TABLE IF NOT EXISTS ai_reply.embedding (
    id                 BIGSERIAL     PRIMARY KEY,
    reference_video_id BIGINT        NOT NULL REFERENCES ai_reply.reference_video (id) ON DELETE CASCADE,
    provider           VARCHAR(40)   NOT NULL,  -- cloudflare_workers_ai | local_http | test_hash
    model_id           VARCHAR(100)  NOT NULL,
    model_revision     VARCHAR(64)   NOT NULL,
    request_mode       VARCHAR(20)   NOT NULL,  -- text(질의 지시는 작업기가 한 번 붙임)
    role               VARCHAR(10)   NOT NULL DEFAULT 'document',
    dimensions         INT           NOT NULL,
    normalization      VARCHAR(10)   NOT NULL DEFAULT 'l2',
    text_sha           VARCHAR(64)   NOT NULL,
    vector             REAL[]        NOT NULL,
    created_at         TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT uq_embedding UNIQUE (reference_video_id, provider, model_id, model_revision, request_mode, role,
                                    dimensions, normalization, text_sha)
);

-- 평가 보호 대상. 검색·사례 축적에서 영구 제외한다. 추가만 한다.
CREATE TABLE IF NOT EXISTS ai_reply.protected_registry (
    id         BIGSERIAL    PRIMARY KEY,
    kind       VARCHAR(20)  NOT NULL,  -- video | media_sha | media_cluster | reply | episode
    value      VARCHAR(100) NOT NULL,
    reason     VARCHAR(200),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_protected_registry UNIQUE (kind, value)
);

-- ───────────────────────── 생성 · 추천 ─────────────────────────

-- 생성 시도(작업기). 일반은 분석·버전·사례 묶음·revision 당 하나, 개인화는 개인 입력당 하나.
CREATE TABLE IF NOT EXISTS ai_reply.generation_attempt (
    id                   BIGSERIAL     PRIMARY KEY,
    attempt_key          VARCHAR(200)  NOT NULL UNIQUE,
    stage                VARCHAR(20)   NOT NULL,  -- GENERIC | PERSONAL
    analysis_id          BIGINT        NOT NULL,
    video_id             BIGINT        NOT NULL,
    viewer_id            BIGINT,
    personal_input_id    BIGINT,
    base_set_id          VARCHAR(36),
    revision_no          INT           NOT NULL DEFAULT 1,
    version_id           VARCHAR(64)   NOT NULL,
    gallery_snapshot_id  VARCHAR(64)   NOT NULL,
    retrieval            JSONB,                   -- query_hash·embedding_version·필터·순위·유사도·선택한 reply id
    generation_input_sha VARCHAR(64),
    gate_input_sha       VARCHAR(64),
    candidates           JSONB,
    gate                 JSONB,
    result_set_id        VARCHAR(36),             -- 이 시도가 낸 세트(개인화 미채택이면 일반 세트 id)
    status               VARCHAR(20)   NOT NULL,  -- SUCCEEDED | FAILED
    failure_code         VARCHAR(60),
    usage                JSONB,
    cost_usd             NUMERIC(12,6),
    model                VARCHAR(60),
    provider_request_ids JSONB,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_generation_attempt_analysis ON ai_reply.generation_attempt (analysis_id, stage);

-- 최종 카드 세트(작업기). 내용은 바꾸지 않는다. 일반 세트는 같은 분석을 보는 시청자가 함께 쓴다(viewer_id 없음).
CREATE TABLE IF NOT EXISTS ai_reply.recommendation_set (
    set_id              VARCHAR(36)  PRIMARY KEY,
    attempt_id          BIGINT       NOT NULL UNIQUE,
    kind                VARCHAR(20)  NOT NULL,  -- GENERIC | PERSONAL
    base_set_id         VARCHAR(36),
    viewer_id           BIGINT,
    analysis_id         BIGINT       NOT NULL,
    video_id            BIGINT       NOT NULL,
    revision_no         INT          NOT NULL DEFAULT 1,
    version_id          VARCHAR(64)  NOT NULL,
    gallery_snapshot_id VARCHAR(64)  NOT NULL,
    status              VARCHAR(20)  NOT NULL,  -- READY | EMPTY
    cards               JSONB        NOT NULL,  -- 공개 [{card_id, category, title}] 최대 3
    internal_cards      JSONB        NOT NULL,  -- 내부 [{card_id, candidate_id, category, title, example, quality, redundancy_group, personalization}]
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at          TIMESTAMPTZ  NOT NULL,
    revoked_at          TIMESTAMPTZ,
    revoke_reason       VARCHAR(60)
);
CREATE INDEX IF NOT EXISTS idx_recommendation_set_lookup
    ON ai_reply.recommendation_set (analysis_id, version_id, gallery_snapshot_id, kind, revision_no);

-- 개인화 입력 스냅샷(앱 서버). 현재 방의 T 이전 본인 발화·영상과 명시적 답글 관계만 담는다.
CREATE TABLE IF NOT EXISTS ai_reply.personal_input (
    id                     BIGSERIAL    PRIMARY KEY,
    viewer_id              BIGINT       NOT NULL,
    video_id               BIGINT       NOT NULL,
    analysis_id            BIGINT       NOT NULL,
    base_set_id            VARCHAR(36)  NOT NULL,
    personal_records       JSONB        NOT NULL,  -- history_records 형식(role=own|interaction|parent_context)
    personal_snapshot_hash VARCHAR(64)  NOT NULL,
    version_id             VARCHAR(64)  NOT NULL,
    purged_at              TIMESTAMPTZ,            -- 보관 기간이 지나 원문(personal_records)을 지운 시각
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_personal_input UNIQUE (base_set_id, viewer_id, personal_snapshot_hash, version_id)
);

-- 시청자별 추천 요청(앱 서버). request_id 는 API 응답에 나간다. 유효 요청은 키당 하나다.
CREATE TABLE IF NOT EXISTS ai_reply.recommendation_request (
    request_id             VARCHAR(36)  PRIMARY KEY,
    viewer_id              BIGINT       NOT NULL,
    video_id               BIGINT       NOT NULL,
    room_id                BIGINT       NOT NULL,
    analysis_request_id    BIGINT       NOT NULL,
    version_id             VARCHAR(64)  NOT NULL,
    personal_mode          VARCHAR(10)  NOT NULL,  -- OFF | ON
    personal_snapshot_hash VARCHAR(64)  NOT NULL,  -- OFF 이면 'none'
    personal_input_id      BIGINT,
    status                 VARCHAR(20)  NOT NULL,  -- PENDING | READY | EMPTY | UNAVAILABLE
    reason_code            VARCHAR(40),
    set_id                 VARCHAR(36),
    created_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    resolved_at            TIMESTAMPTZ,
    expires_at             TIMESTAMPTZ,
    revoked_at             TIMESTAMPTZ,
    revoke_reason          VARCHAR(60)
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_recommendation_request_active
    ON ai_reply.recommendation_request (viewer_id, analysis_request_id, version_id, personal_snapshot_hash)
    WHERE revoked_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_recommendation_request_viewer_video
    ON ai_reply.recommendation_request (viewer_id, video_id, created_at);
CREATE INDEX IF NOT EXISTS idx_recommendation_request_set ON ai_reply.recommendation_request (set_id);

-- ───────────────────────── 노출 · 실제 답장 ─────────────────────────

-- 앱이 보낸 사건(노출·선택·입력 시작·닫기). 초안 글은 받지 않는다.
CREATE TABLE IF NOT EXISTS ai_reply.recommendation_event (
    event_id    VARCHAR(36)  PRIMARY KEY,
    session_id  VARCHAR(36)  NOT NULL,
    request_id  VARCHAR(36)  NOT NULL,
    set_id      VARCHAR(36)  NOT NULL,
    card_id     VARCHAR(64),
    viewer_id   BIGINT       NOT NULL,
    video_id    BIGINT       NOT NULL,
    event_type  VARCHAR(20)  NOT NULL,  -- impression | card_selected | composer_started | dismissed
    occurred_at TIMESTAMPTZ  NOT NULL,
    received_at TIMESTAMPTZ  NOT NULL,
    late        BOOLEAN      NOT NULL,
    payload_sha VARCHAR(64)  NOT NULL,
    provenance  VARCHAR(20)  NOT NULL   -- REAL_USER | QA
);
CREATE INDEX IF NOT EXISTS idx_recommendation_event_set ON ai_reply.recommendation_event (set_id, session_id);
CREATE INDEX IF NOT EXISTS idx_recommendation_event_viewer ON ai_reply.recommendation_event (viewer_id, video_id, occurred_at);

-- 노출 한 번(세션·세트·시청자·영상). 첫 노출 뒤 24시간 또는 실제 답장으로 닫힌다.
CREATE TABLE IF NOT EXISTS ai_reply.reply_episode (
    id                      BIGSERIAL    PRIMARY KEY,
    session_id              VARCHAR(36)  NOT NULL,
    set_id                  VARCHAR(36)  NOT NULL,
    request_id              VARCHAR(36)  NOT NULL,
    viewer_id               BIGINT       NOT NULL,
    video_id                BIGINT       NOT NULL,
    room_id                 BIGINT       NOT NULL,
    first_impression_at     TIMESTAMPTZ  NOT NULL,
    last_impression_at      TIMESTAMPTZ  NOT NULL,
    first_selected_card_id  VARCHAR(64),
    composer_started_at     TIMESTAMPTZ,
    actual_reply_message_id BIGINT,
    actual_reply_at         TIMESTAMPTZ,
    attribution             VARCHAR(20),  -- explicit | inferred | superseded
    outcome                 VARCHAR(20),  -- REPLIED | NO_REPLY
    closed_at               TIMESTAMPTZ,
    provenance              VARCHAR(20)  NOT NULL,  -- REAL_USER | QA | SYNTHETIC
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_reply_episode UNIQUE (session_id, set_id, viewer_id, video_id)
);
CREATE INDEX IF NOT EXISTS idx_reply_episode_open ON ai_reply.reply_episode (viewer_id, video_id) WHERE closed_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_reply_episode_closed ON ai_reply.reply_episode (closed_at, id);

-- 저장된 실제 영상 답장과 추천 출처의 연결(앱 서버). 첫 저장 메시지의 연결만 남는다.
CREATE TABLE IF NOT EXISTS ai_reply.reply_link (
    message_id        BIGINT       PRIMARY KEY,
    viewer_id         BIGINT       NOT NULL,
    video_id          BIGINT       NOT NULL,
    sent_at           TIMESTAMPTZ  NOT NULL,
    source_set_id     VARCHAR(36),
    source_card_id    VARCHAR(64),
    source_session_id VARCHAR(36),
    source_status     VARCHAR(20)  NOT NULL,  -- NONE | VALID | INVALID
    invalid_reason    VARCHAR(40),
    attribution       VARCHAR(20)  NOT NULL,  -- explicit | inferred | unexposed
    episode_id        BIGINT,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_reply_link_viewer_video ON ai_reply.reply_link (viewer_id, video_id, sent_at);

-- 피드백 판정에 쓸 실제 답장 원문(수집이 허용된 출처만). 원문 삭제 시 이 행을 지운다.
CREATE TABLE IF NOT EXISTS ai_reply.episode_reply_snapshot (
    episode_id  BIGINT       PRIMARY KEY,
    message_id  BIGINT       NOT NULL,
    reply_text  TEXT         NOT NULL,
    reply_sha   VARCHAR(64)  NOT NULL,
    provenance  VARCHAR(20)  NOT NULL,
    captured_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- ───────────────────────── 피드백 배치 · 검증 ─────────────────────────

-- 배치 한 번. cutoff·baseline 을 고정하고 같은 배치를 재개한다.
CREATE TABLE IF NOT EXISTS ai_reply.batch_run (
    id                   BIGSERIAL     PRIMARY KEY,
    status               VARCHAR(20)   NOT NULL,  -- PENDING | KEPT_BASELINE | INCONCLUSIVE | VALIDATED | ACTIVE | ROLLBACK | FAILED
    trigger_reason       VARCHAR(30)   NOT NULL,  -- COUNT | MAX_WAIT | MANUAL | SCHEDULED(운영 월 1회)
    cutoff_closed_at     TIMESTAMPTZ   NOT NULL,
    lo_episode_id        BIGINT        NOT NULL,  -- 범위 안 episode id 최솟값(진단용)
    hi_episode_id        BIGINT        NOT NULL,  -- 범위 안 episode id 최댓값(진단용)
    lo_closure_seq       BIGINT,                  -- 이전 배치 watermark(feedback_closure.seq, 이 값 초과부터)
    hi_closure_seq       BIGINT,                  -- 이 배치가 처리하는 마지막 closure seq
    baseline_version_id  VARCHAR(64)   NOT NULL,
    baseline_revision    BIGINT        NOT NULL,
    candidate_version_id VARCHAR(64),
    frozen_suite_sha     VARCHAR(64),
    gallery_snapshot_id  VARCHAR(64),
    budget_usd           NUMERIC(10,4) NOT NULL,
    reserved_usd         NUMERIC(12,6) NOT NULL DEFAULT 0,
    spent_usd            NUMERIC(12,6) NOT NULL DEFAULT 0,
    reason_counts        JSONB,
    patch                JSONB,
    result               JSONB,
    audit                JSONB         NOT NULL DEFAULT '[]',
    lease_owner          VARCHAR(100),
    lease_until          TIMESTAMPTZ,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    finished_at          TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS idx_batch_run_status ON ai_reply.batch_run (status, created_at);
-- 먼저 만든 DB(이 열이 없던 버전)에도 열을 더한다.
ALTER TABLE ai_reply.batch_run ADD COLUMN IF NOT EXISTS lo_closure_seq BIGINT;
ALTER TABLE ai_reply.batch_run ADD COLUMN IF NOT EXISTS hi_closure_seq BIGINT;

-- 피드백 배치가 본 '닫힌 episode' 의 순서. episode 는 만든 순서와 닫힌 순서가 달라서(id·closed_at 둘 다
-- 늦게 닫힌 것을 놓칠 수 있다) 작업기가 처음 본 순서로 seq 를 매기고 배치 watermark 를 이 seq 로 잡는다.
CREATE TABLE IF NOT EXISTS ai_reply.feedback_closure (
    seq         BIGSERIAL    PRIMARY KEY,
    episode_id  BIGINT       NOT NULL UNIQUE,
    closed_at   TIMESTAMPTZ  NOT NULL,
    observed_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 활성 버전이 실제로 바뀐 기록. 롤백은 여기에 있는(예전에 실제 활성이던) 버전으로만 한다.
CREATE TABLE IF NOT EXISTS ai_reply.version_activation_log (
    id              BIGSERIAL    PRIMARY KEY,
    scope           VARCHAR(30)  NOT NULL,
    from_version_id VARCHAR(64),
    to_version_id   VARCHAR(64)  NOT NULL,
    revision        BIGINT       NOT NULL,
    reason          VARCHAR(200) NOT NULL,
    batch_run_id    BIGINT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 독립 판정 결과. 같은 입력·프롬프트·모델의 첫 유효 판정만 남긴다(재판정하지 않는다).
CREATE TABLE IF NOT EXISTS ai_reply.feedback_judgment (
    id                BIGSERIAL    PRIMARY KEY,
    batch_run_id      BIGINT,
    episode_id        BIGINT,
    validation_run_id BIGINT,
    case_key          VARCHAR(120),
    stage             VARCHAR(30)  NOT NULL,  -- ACTUAL_APPROPRIATENESS | DIRECTION_MATCH | CARD_QUALITY
    model             VARCHAR(60)  NOT NULL,
    prompt_sha        VARCHAR(64)  NOT NULL,
    version_id        VARCHAR(64),
    input_sha         VARCHAR(64)  NOT NULL,
    output_json       JSONB        NOT NULL,
    output_sha        VARCHAR(64)  NOT NULL,
    verdict           VARCHAR(20)  NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_feedback_judgment UNIQUE (stage, input_sha, prompt_sha, model)
);
CREATE INDEX IF NOT EXISTS idx_feedback_judgment_episode ON ai_reply.feedback_judgment (episode_id, stage);

-- 적절하고 의미가 안정된 실제 답장 관찰(사례 축적·원인 집계). 평가 보호·QA·합성은 참고 사례가 되지 않는다.
CREATE TABLE IF NOT EXISTS ai_reply.feedback_case (
    id                   BIGSERIAL    PRIMARY KEY,
    episode_id           BIGINT       NOT NULL UNIQUE,
    batch_run_id         BIGINT       NOT NULL,
    provenance           VARCHAR(20)  NOT NULL,
    room_id              BIGINT       NOT NULL,
    viewer_id            BIGINT       NOT NULL,
    reason_code          VARCHAR(30)  NOT NULL,  -- included | candidate_absent | gate_dropped | unsupported
    split                VARCHAR(20)  NOT NULL,  -- dev | regression | holdout
    consumed_by_batch_id BIGINT,                 -- 개선안에 쓰인 배치(같은 관찰로 재제안하지 않는다)
    reference_eligible   BOOLEAN      NOT NULL DEFAULT false,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- 같은 입력 baseline/candidate 재생성 비교. 생성 결과를 먼저 동결한 뒤 판정한다.
CREATE TABLE IF NOT EXISTS ai_reply.validation_run (
    id                   BIGSERIAL    PRIMARY KEY,
    batch_run_id         BIGINT       NOT NULL,
    baseline_version_id  VARCHAR(64)  NOT NULL,
    candidate_version_id VARCHAR(64)  NOT NULL,
    suite                JSONB        NOT NULL,
    suite_sha            VARCHAR(64)  NOT NULL,
    stage                VARCHAR(30)  NOT NULL,  -- GENERATING | FROZEN | JUDGING | DONE
    frozen_outputs       JSONB,
    frozen_sha           VARCHAR(64),
    result               VARCHAR(20),            -- PASS | FAIL | INCONCLUSIVE
    metrics              JSONB,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_validation_run UNIQUE (batch_run_id, candidate_version_id, suite_sha)
);

-- 작업기 전용 DB 계정(운영)은 이 스키마만 쓰게 한다. 운영 적용 때 계정 이름을 정해 따로 실행한다.
--   CREATE ROLE haruharu_ai_worker LOGIN PASSWORD '<Secrets Manager>';
--   GRANT USAGE ON SCHEMA ai_reply TO haruharu_ai_worker;
--   GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA ai_reply TO haruharu_ai_worker;
--   GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA ai_reply TO haruharu_ai_worker;
COMMIT;
