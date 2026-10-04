# Git Conventions

## 브랜치 전략 (Git Flow 변형)

```
main        ← 운영 배포 (보호 브랜치)
develop     ← 개발/스테이징 서버 (기본 브랜치)
feat/123    ← develop에서 분기
fix/456     ← develop에서 분기
hotfix/789  ← main에서 직접 분기 (긴급 수정만)
```

## 커밋 메시지 형식

```
[#이슈번호]; <tag>: <설명>
```

예시:
```
[#42]; feat: 카카오 OAuth 로그인 API 추가
[#43]; fix: 만료된 토큰 갱신 로직 null 체크 누락 수정
[#44]; refactor: AuthService → TokenService 분리
[#45]; test: 로그인 플로우 통합 테스트 추가
```

## 허용 태그

| 태그 | 용도 |
|------|------|
| `feat` | 새 기능 |
| `fix` | 버그 수정 |
| `refactor` | 리팩토링 (기능 변경 없음) |
| `test` | 테스트 코드 |
| `docs` | 문서 |
| `chore` | 빌드/설정/의존성 |
| `style` | 포맷팅 |
| `perf` | 성능 개선 |
| `ci` | CI 설정 |
| `revert` | 커밋 되돌리기 |

## PR 제목 형식

```
[#이슈번호]; <tag>: <설명>
```

## 이슈 제목 형식

```
<tag>: <설명>
```

## GitHub Actions 검증

`.github/naming-convention.yml` 에서 규칙 관리.
- 이슈 제목: 강제 (enforce)
- PR 제목/브랜치명: 강제
- 커밋 메시지: 경고만 (enforce_commits: false)
