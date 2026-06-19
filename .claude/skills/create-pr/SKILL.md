---
description: PR 컨벤션 검증 + pull_request_template.md 기반 PR 생성. 기능 구현 완료 후 호출. develop 브랜치 대상 PR 자동 생성.
argument-hint: "[#이슈번호]"
disable-model-invocation: true
allowed-tools: Bash(git status) Bash(git log *) Bash(git diff *) Bash(git push *) Bash(gh pr create *) Bash(gh pr view *) Bash(./gradlew test)
---

## 언제 쓰나?

- `/bdd-implement` 완료 후 PR 생성 시
- 사용자가 직접 `/create-pr #이슈번호` 입력 시
- 전제: `./gradlew test` 통과 + 실제 API 검증 완료 상태

## 실행 절차

### Step 1. 현재 상태 확인

```bash
git status
git log --oneline origin/develop..HEAD
git diff origin/develop...HEAD --name-only
```

미완료 변경사항 있으면 사용자에게 알리고 중단.

### Step 2. 브랜치명 검증

`.claude/rules/git-conventions.md` 브랜치 형식 확인:
```
{tag}/{이슈번호}  예) feat/42, fix/123
```

형식 불일치 시 경고 출력.

### Step 3. PR 제목 생성

`.claude/rules/git-conventions.md` PR 제목 형식:
```
[#이슈번호]; {tag}: {설명}
```

커밋 히스토리와 변경 파일을 분석해 제목 제안 후 사용자 확인.

### Step 4. PR 본문 작성

`.github/pull_request_template.md` 형식 기반으로 자동 채운다:

```markdown
## 📌 Related Issue

closes #{이슈번호}

---

## #️⃣ 요약 설명

{커밋 히스토리 기반 1-2문장 요약}

---

## 📝 작업 내용

{변경 파일/커밋 분석 기반 체크리스트}
- [ ] 
- [ ] 

---

## 👍 동작 확인

- [ ] `./gradlew test` 전체 통과
- [ ] 서버 실행 후 실제 API 동작 확인
- [ ] 기존 기능 회귀 없음

---

## 💬 리뷰 요구사항 (선택)

```

사용자에게 본문 확인 요청.

### Step 5. PR 생성

원격 브랜치가 없으면 먼저 push:
```bash
git push -u origin {현재브랜치}
```

PR 생성:
```bash
gh pr create \
  --title "[#{번호}]; {tag}: {설명}" \
  --body "{작성된 본문}" \
  --base develop
```

### Step 6. 완료 보고

생성된 PR URL 출력.
네이밍 컨벤션 위반 사항 있으면 함께 보고.
