# 관리자 방 활동 조회 (#434)

기존 관리자 Bearer 인증으로 조회한다. 활동 목록·요약·최종 선택 API는 SUPER·OPERATOR만 접근하며 CS는 403을 반환한다.
활동은 조회 전용이며 현재 DB에 남아 있는 데이터를 보여준다. 삭제·반응 교체 이력의 감사 로그는 아니다.

## API

- `GET /api/v1/admin/rooms/{roomId}/activities`
- `GET /api/v1/admin/rooms/{roomId}/activity/summary`
- `GET /api/v1/admin/rooms/{roomId}/selections`: 관리자 전용 방 전체 최종 선택표. 미제출/기권 포함 전체 활성 멤버를 반환한다. 상대는 같은 방 활성 멤버만 표시하며 mutual(상호 선택)과 matched(저장된 매칭 성사 결과)를 구분한다. 일반 유저 결과 공개 시각 제한 API는 변경하지 않는다.

공통 선택 쿼리: `day`(1~방 total_days), `userId`(양수).
목록 선택 쿼리: `type`(VIDEO/CHAT/REACTION/QUESTION), `page`(기본 0), `size`(기본 24, 1~100), `order`(asc/desc, 기본 desc).
QUESTION은 질문 한 건에 현재 답변을 포함한다. 별도 ANSWER 활동·집계는 없다.
응답은 `{isSuccess,data,error}` 공통 래퍼, 필드는 snake_case, 목록은 기존 `PageResponse` 계약이다.
존재하지 않는 방은 404, 잘못된 필터는 400이다.

일차는 신규 방의 starts_at부터 24시간 단위, 구형 방은 기존 09시 경계를 따른다. 기간 밖 활동의 day는 null이다.
영상의 일차 필터·day는 slot_start 기준(없으면 created_at 대체)이며 VIDEO 목록은 슬롯 시각으로 정렬한다. video.slot_start를 반환해 관리자가 앱과 같은 실제 슬롯으로 묶어볼 수 있다.
질문의 일차·정렬은 created_at 기준이다. answer/answered_at은 현재 답변 상태이며 과거 시점 스냅샷이 아니다.
반응 시간은 해당 행 최초 생성 시각이다. 반응 교체는 기존 도메인에서 시각을 갱신하지 않는다.

## 범위와 집계

- 채팅은 해당 방 GROUP만 포함한다. 매칭 후 DIRECT 대화는 제외한다.
- 관리자 전용: 익명질문 actor는 발신자, target은 수신자다.
- 발신자·수신자는 해당 방 가명과 사용자 ID로 표시한다. 가명이 없으면 가명 없음으로 표시하며 프로필 닉네임으로 대체하지 않는다. 일반 사용자 API는 변경하지 않는다.
- 참여자 필터는 질문 발신·수신 모두 포함한다. 참여자별 받은 질문 집계는 유지한다.
- 영상/반응은 삭제 영상 및 삭제된 영상 작성 방 멤버를 제외한다. 숨김 영상은 hidden=true로 구분한다.
- Native SQL은 각 테이블의 soft-delete 조건을 명시하며 UNION 결과를 DB에서 정렬·페이지 처리한다.
- 요약은 페이지/종류와 무관하게 선택한 day/userId의 전체 활동을 집계한다. 반응은 발신·수신 모두 userId 필터에 대응한다.
- questions는 해당 기간에 생성된 질문 수이며 답변 유무와 무관하게 질문당 한 번 집계한다.

## 배포 설정

admin에도 app과 같은 `AWS_S3_BUCKET`, `AWS_REGION` 및 S3 읽기 가능한 런타임 IAM 자격증명이 필요하다.
공유 S3StorageAdapter로 영상·썸네일의 GET URL을 단기 서명한다. 기본 유효시간은 1시간이며 `AWS_S3_VIDEO_GET_EXPIRY`, `AWS_S3_IMAGE_GET_EXPIRY`로 조절한다.
클라이언트는 URL을 영구 저장하지 않고 만료 시 목록을 새로 조회한다. 실제 키는 설정 파일이나 프런트 번들에 넣지 않는다.

스키마 변경은 없다. 기존 video_posts, video_reactions, chat_rooms, chat_messages, anonymous_questions를 조회한다.

## 검증 (2026-09-17)

- `./gradlew test jacocoTestReport` 통과: admin 122, app 944, core 106, 실패/스킵 0.
- 새 PostgreSQL 통합 테스트: 방 격리, 일차 경계, 삭제 영상/채팅/질문 제외, 직접채팅 제외,
  관리자 방 가명 표시·프로필 닉네임 비사용·발신자 필터·질문/답변 중복 제거, 집계·안정적 페이지 정렬·401·일반 사용자 403·404·잘못된 필터 400.
- 미디어 포트 단위 테스트: 영상/이미지 서명 호출과 서명 결과 응답 전달 확인.
- 별도 로컬 PostgreSQL + `:admin:bootRun`으로 실제 로그인·요약·활동 HTTP 요청 및 프런트 연결 검증 통과.
- 저장소 Java 리뷰 기준의 별도 읽기 전용 리뷰 통과. 비밀값 파일 변경 없음.

운영 DB·S3·배포 설정은 변경하지 않았으며, 실제 운영 S3 권한 검증은 배포 환경에서 필요하다.

## 관리자 참여자 대표사진 (#534)

`GET /api/v1/admin/rooms/{roomId}`의 `members[].profile_image_url`은 해당 참여자의 대표사진 원본 접근 URL이며 사진이 없으면 null이다. 기존 필드는 유지한다. 대표사진 선택은 공유 `UserImageReader.getRepresentativeImageUrls`의 일괄 조회/정렬을 사용하고, URL은 기존 `S3StoragePort.createPresignedImageGetUrl`로 발급한다. 관리자 인증이 필요하며 일반 앱의 방 참여 인가·사진 공개/블러 규칙은 변경하지 않는다. DB 스키마 변경은 없다.

사진 응답 추가는 ECS 배포의 `target=admin`으로 관리자 서버만 개발→운영 순서로 배포한다. 기존 전체 배포 기본값은 `target=all`이며, 관리자만 선택하면 일반 앱·Airflow 배포와 대기 단계를 건너뛴다. 배포 이미지는 검증된 고정 커밋 SHA 태그를 사용한다.
