# 미성년자·아동 안전 신고

기존 사용자 신고와 익명 질문 신고의 공통 `ReportReason`에 `CHILD_SAFETY`를 추가한다(#550).
앱 노출 문구는 `미성년자·아동 안전`이고, 기존 신고 화면에서 상세 설명과 함께 접수한다.
일반 문의나 대상 없는 신고, 새 운영자 기능은 이 변경에 포함되지 않는다.

## API 계약

- `POST /api/v1/reports`: `reported_user_id`, `reason`, 선택 `detail`을 보낸다. 성공 응답의 `data.report_id`와
  `RECEIVED` 저장 상태는 그대로다. 대상 필수·자기 자신 신고 금지·신고자/대상/신고 범위 중복 규칙을 유지한다.
- 기존 익명 질문/답변 신고도 같은 사유 enum을 사용하며, 작성자 식별은 서버가 처리한다.
- 허용 사유: `CHILD_SAFETY`, `INAPPROPRIATE_MEDIA`, `ABUSIVE_LANGUAGE`, `FAKE_PROFILE`, `SPAM_AD`, `OTHER`.
- 이 사유는 미성년자 이용 의심뿐 아니라 아동 성적 학대·착취 등 안전 우려를 전달하는 데 사용한다.

## 배포 순서와 이관

Hibernate의 기존 enum CHECK는 Java enum 추가만으로 넓어지지 않는다. 운영 app은 `ddl-auto=validate`이므로
아래 순서로 명시적으로 반영한다.

1. 읽기 전용 [사전 점검 SQL](../../scripts/sql/550-child-safety-report-preflight.sql)로 실제 `reports.reason`의
   CHECK 정의·컬럼·사유별 개수를 확인한다. 상세 내용·참여자 식별자는 조회하지 않는다.
2. [up SQL](../../scripts/sql/550-child-safety-report-up.sql)을 적용한다. 5초 잠금·30초 문장 제한과 하나의 트랜잭션을
   사용한다. 생성된 이름/사용자 지정 이름의 사유 값 CHECK 식에 새 사유만 OR로 추가하고, 다른 허용 값·길이 제약·
   필수 대상·유일 제약은 보존한다. 값 CHECK가 없는 기존 표에는 현재 허용 목록 CHECK를 추가한다.
   알 수 없는 값 CHECK나 새 사유를 막는 추가 제약을 발견하면 전체 이관을 실패시켜 수동 확인하도록 한다.
3. 검증 SQL을 실행하고 새 enum을 이해하는 app/admin을 배포한다. 기존 앱의 다섯 사유는 계속 받는다.
4. 새 앱 제출 빌드에서 신고 진입→새 사유 선택→상세 내용 작성→접수 완료와 실제 저장을 확인하고 심사에 제출한다.

운영 DB에 검증용 신고를 생성하지 않는다. 동작 검증은 로컬 전용 데이터로 하고 운영에서는 선언과 메타데이터만 확인한다.

## 롤백

새 앱의 출시를 중단하거나 이전 앱으로 되돌려도 추가된 DB 허용 값은 보존해도 된다.
이미 `CHILD_SAFETY` 신고가 있다면 기존 서버로 되돌리면 enum 역직렬화가 실패할 수 있으므로 호환 서버를 유지한다.
[down SQL](../../scripts/sql/550-child-safety-report-down.sql)은 해당 신고가 하나라도 있으면 실패하며, 접수 데이터를
삭제하거나 다른 사유로 바꾸지 않는다. 신고가 없으면 새 값만 금지하는 CHECK를 추가해 기존/미래 사유 허용 식을 보존한다.
up을 다시 실행하면 이 롤백 금지 CHECK를 제거한다.
