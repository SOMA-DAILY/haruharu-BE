# Haruharu Backend

## Tech Stack
Java 21 · Spring Boot 4.1.0 · Gradle Groovy DSL · PostgreSQL · JPA (Hibernate)
OAuth2 (Kakao/Apple) · JWT · Spring Security · PASS 지연인증
Base package: `org.son.haruharu`

---

## 제품 방향 (2026-08-13 피벗)

**현재 MVP = 운영진 초대코드 모델.** 기존 자동매칭(22:00 배치·남3여3)·IAP 코인·PASS 필수 실명인증 모델은 폐기/보류했다. 새 루프:

운영진이 신청폼으로 모집 → 방 생성 + 초대코드 발급 → 관리자가 설정한 참가자가 코드로 입장 → 1시간 슬롯 영상(해당 슬롯 시간대에만 기본 노출, 과거 조회 유지)·방 전체 그룹채팅·주기별 미션 → Day3 최종선택(어드민 지정 시각) → 상호 성사 시 1:1 채팅.

- **제거 완료(#451):** 기존 대기열 자동매칭. 큐·FIFO·배치·API·개발 실행·매칭 유도 알림·운영 통계 제거. 최종 상호 선택과 1:1 채팅은 유지한다.
- **폐기/별도 정리 대상:** coin · payment(IAP) · daily_upload_reward
- **보류(비활성 잔존):** identity(PASS → 지연인증) · value(가치관)
- **개편:** room(초대코드·어드민 스케줄·status) · video(슬롯 시간창) · chat(그룹채팅) · mission(활성화)
- **확정 설계 상세:** 노션 "새 MVP 재설계 — 초대코드 모델" + 메모리 `mvp-pivot-invite-code`

## 인증 아키텍처

```
OAuth2 (Kakao/Apple) → 내부 JWT 발급 → Response Body (access_token, refresh_token)
                                       ↓
                             Spring Security Filter
                                       ↓
                          PASS 지연인증 (보류 — 성별/실명 필요 시점에만 트리거)
```

- AccessToken: 단기 만료, Response Body 반환 → 클라이언트가 Authorization 헤더로 전송
- RefreshToken: 장기 만료, Response Body 반환 + DB 저장 (재사용 탐지)
- 온보딩: **닉네임만** 수집 (성별 미수집 — 필요 시 PASS 지연인증). 옛 프로필 필드(지역·MBTI 등)는 nullable 잔존
- `room_members.gender`는 운영진이 방 구성 시 부여 (admin)
