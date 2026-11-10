-- #559: 영상 답장 Depth 라벨(DialogRPT-depth 추가 학습·평가용). 실제 전송된 영상 답장(ai_reply.reply_link)마다
-- 관찰 기간이 끝난 뒤 앱 서버가 답글 연결(chat_messages.reply_to_message_id)을 따라 Depth·Width 를 한 번 계산해 넣는다.
-- AI 작업기는 서비스 표를 읽지 못하므로 이 표만 읽는다. 멱등이다(여러 번 실행해도 결과가 같다).
--
-- 558-reply-recommendations-up.sql 을 먼저 적용한다(ai_reply 스키마). Hibernate 가 관리하지 않는다(엔티티 없음).
-- 원문 텍스트는 넣지 않는다. 시각은 timestamptz(UTC)다.
--
-- depth  : 답장에서 시작한 최장 답글 경로 길이(답장 r → 상대 → 나 → 상대 = 3). 답글이 없으면 0.
-- width  : 답장에 직접 달린 답글 수.
-- status : OBSERVED(depth·width 확정) | UNOBSERVED(연결이 끊기거나 불명확, depth·width 는 NULL — 0 과 다르다).
-- 보조 지표(Depth·Width 와 섞지 않는다, 영상 작성자를 모르면 NULL):
-- owner_replied          : 관찰 기간 안에 영상 작성자가 이 답장의 답글 트리 안에서 한 번이라도 답했는지. 미관측이면 NULL.
-- followup_message_count : 관찰 기간 안에 같은 채팅방에서 답장한 사람과 영상 작성자가 보낸 메시지 수
--                          (답글 연결과 무관, 답장 자체·숨겨진 메시지 제외). 답장 메시지를 못 찾으면 NULL.
-- cohort_id : 답장 채팅방(chat_rooms.room_id)의 3일 방 기수(rooms.cohort_id). 학습 분할(최신 기수 = test)에 쓴다.
--             답장 메시지를 못 찾았거나 기수 없는 방이면 NULL. 수집 시점 값을 그대로 남긴다(FK 없음).
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';

CREATE TABLE IF NOT EXISTS ai_reply.reply_depth_observation (
    message_id        BIGINT       PRIMARY KEY,            -- 실제 영상 답장 = ai_reply.reply_link.message_id
    video_id          BIGINT       NOT NULL,               -- 공통 맥락 C 의 키(C 는 영상 게시 시점에 고정)
    viewer_id         BIGINT       NOT NULL,
    room_id           BIGINT,                              -- 답장 메시지를 찾지 못하면 NULL
    source_set_id     VARCHAR(36),                         -- 유효한 추천 출처가 있을 때만(추천에서 나온 답장)
    source_card_id    VARCHAR(64),
    width             INTEGER,
    depth             INTEGER,
    status            VARCHAR(20)  NOT NULL,
    unobserved_reason VARCHAR(40),                         -- REPLY_UNAVAILABLE | CHAIN_BROKEN | DEPTH_LIMIT | LABEL_FAILED
    window_start      TIMESTAMPTZ  NOT NULL,
    window_end        TIMESTAMPTZ  NOT NULL,
    observed_at       TIMESTAMPTZ  NOT NULL,
    provenance        VARCHAR(20)  NOT NULL,               -- 노출(episode) 출처 그대로(REAL_USER | QA | SYNTHETIC), 노출 없으면 수집 정책
    owner_replied          BOOLEAN,
    followup_message_count INTEGER,
    cohort_id         BIGINT,
    CONSTRAINT ck_reply_depth_status CHECK (
        (status = 'OBSERVED' AND depth IS NOT NULL AND depth >= 0 AND width IS NOT NULL AND width >= 0
            AND unobserved_reason IS NULL)
        OR (status = 'UNOBSERVED' AND depth IS NULL AND width IS NULL AND unobserved_reason IS NOT NULL)),
    CONSTRAINT ck_reply_depth_window CHECK (window_end > window_start),
    CONSTRAINT ck_reply_depth_owner_replied CHECK (status = 'OBSERVED' OR owner_replied IS NULL),
    CONSTRAINT ck_reply_depth_followup CHECK (followup_message_count IS NULL OR followup_message_count >= 0)
);
-- 보조 지표 이전에 이 파일을 적용한 DB 에도 칸을 더한다(제약은 새로 만든 표에만 있다).
ALTER TABLE ai_reply.reply_depth_observation ADD COLUMN IF NOT EXISTS owner_replied BOOLEAN;
ALTER TABLE ai_reply.reply_depth_observation ADD COLUMN IF NOT EXISTS followup_message_count INTEGER;
-- 기수 칸 이전에 이 파일을 적용한 DB 에도 칸을 더한다(이미 있는 행은 NULL = 기수 모름, 학습 쌍에서 빠진다).
ALTER TABLE ai_reply.reply_depth_observation ADD COLUMN IF NOT EXISTS cohort_id BIGINT;
-- 비교 데이터셋은 같은 C(같은 영상)의 실제 답장끼리 묶는다.
CREATE INDEX IF NOT EXISTS idx_reply_depth_observation_video ON ai_reply.reply_depth_observation (video_id, status);

-- 작업기 전용 DB 계정(운영)에는 558 과 같은 권한을 이 표에도 준다(읽기만 필요).
--   GRANT SELECT ON ai_reply.reply_depth_observation TO haruharu_ai_worker;
COMMIT;
