# Secret Policy: 시크릿 유출 방지

## 원칙

**실제 값을 가진 시크릿은 절대 git에 올리지 않는다.**
`application.yaml`은 `${VARIABLE_NAME}` 플레이스홀더 형식만 허용한다.

## 파일별 git 허용 정책

| 파일 | git 허용 | 이유 |
|------|---------|------|
| `application.yaml` | O | `${}` 플레이스홀더만 포함 |
| `application-local.yaml` | X | 로컬 실제 값 포함 가능 |
| `application-dev.yaml` | X | 개발 서버 시크릿 |
| `application-prod.yaml` | X | 운영 시크릿 |
| `.env*` | X | 환경변수 실제 값 |

## 허용/금지 패턴 예시

```yaml
# GOOD — 플레이스홀더
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
jwt:
  secret: ${JWT_SECRET}

# BAD — 하드코딩 (CRITICAL, 커밋 차단)
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/haruharu
    username: postgres
    password: secret123
```

## 커밋 전 필수 체크리스트 (CRITICAL)

- [ ] `git diff --staged --name-only` 에 `application-local`, `application-dev`, `application-prod` 파일 없음
- [ ] `application*.yaml` 파일에서 `password:`, `secret:`, `token:`, `api-key:` 뒤에 `${` 외 실제 값 없음
- [ ] `.env` 파일이 staged에 없음

위 항목 중 하나라도 위반 시 **커밋 차단** — `.githooks/pre-commit` 스크립트가 자동 감지한다.

## 훅 설치 (최초 1회)

```bash
git config core.hooksPath .githooks
chmod +x .githooks/pre-commit
```

## 유출 발생 시

1. 즉시 중단 · 팀 공유
2. 유출된 시크릿 즉시 rotate
3. `git filter-repo` 또는 BFG Repo Cleaner로 git history에서 제거