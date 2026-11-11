-- #560(이슈): 영상 답장 추천 효과 검증. 게시자 응답 라벨과 무작위 비교 그룹을 더한다. 멱등이다.
-- 558·559 SQL 을 먼저 적용한다. Hibernate 가 관리하지 않는다(엔티티 없음). 원문 텍스트는 넣지 않는다.
--
-- reply_depth_observation (Depth 와 섞지 않는 효과 라벨, 영상 작성자를 모르거나 미관측이면 NULL):
--   owner_responded         : 답장 시각부터 response_window_minutes 안에 영상 작성자가 같은 채팅방에 보낸
--                             숨겨지지 않은 메시지가 1개 이상인지(답글 연결과 무관)
--   owner_response_minutes  : 그 첫 메시지까지 걸린 분. 없으면 NULL
--   response_window_minutes : 판정에 쓴 창(분). 설정값을 행마다 남긴다
--   bystander_members       : 대조 확인용. 답장 시각에 방에 있던 멤버 중 답장자와 작성자를 뺀 수
--   bystanders_responded    : 그중 같은 창 안에 메시지를 보낸 멤버 수
-- reply_link.experiment_arm : 답장 보낸 사람의 비교 그룹(TREATMENT | HOLDOUT | NONE). 이 칸이 생기기 전 행은 NULL
-- holdout_assignment        : 시청자 단위 무작위 배정. 한 번 넣으면 바꾸지 않는다(group_key = cohort:<id> | room:<id>)
BEGIN;
SET LOCAL lock_timeout = '5s';
SET LOCAL statement_timeout = '60s';

ALTER TABLE ai_reply.reply_depth_observation ADD COLUMN IF NOT EXISTS owner_responded BOOLEAN;
ALTER TABLE ai_reply.reply_depth_observation ADD COLUMN IF NOT EXISTS owner_response_minutes NUMERIC(8, 2);
ALTER TABLE ai_reply.reply_depth_observation ADD COLUMN IF NOT EXISTS response_window_minutes INTEGER;
ALTER TABLE ai_reply.reply_depth_observation ADD COLUMN IF NOT EXISTS bystander_members INTEGER;
ALTER TABLE ai_reply.reply_depth_observation ADD COLUMN IF NOT EXISTS bystanders_responded INTEGER;

ALTER TABLE ai_reply.reply_link ADD COLUMN IF NOT EXISTS experiment_arm VARCHAR(20);

CREATE TABLE IF NOT EXISTS ai_reply.holdout_assignment (
    group_key   VARCHAR(40)  NOT NULL,
    room_id     BIGINT       NOT NULL,
    user_id     BIGINT       NOT NULL,
    arm         VARCHAR(20)  NOT NULL,   -- TREATMENT | HOLDOUT
    hash        NUMERIC(20, 18) NOT NULL, -- sha256(salt:group_key:user_id) 앞 8바이트 / 2^64, [0, 1)
    ratio       NUMERIC(4, 3) NOT NULL,  -- 배정 때 쓴 HOLDOUT 비율
    assigned_at TIMESTAMPTZ  NOT NULL,
    PRIMARY KEY (group_key, room_id, user_id),
    CONSTRAINT ck_holdout_arm CHECK (arm IN ('TREATMENT', 'HOLDOUT'))
);
CREATE INDEX IF NOT EXISTS idx_holdout_assignment_room ON ai_reply.holdout_assignment (room_id);

-- 작업기 전용 DB 계정(운영)은 읽기만 필요하다.
--   GRANT SELECT ON ai_reply.holdout_assignment TO haruharu_ai_worker;
COMMIT;
