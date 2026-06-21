---
description: GitHub 이슈 생성. 기능 요청(feat), 버그 수정(fix), 기술 작업(chore/refactor) 타입을 감지해 .github/ISSUE_TEMPLATE/ 형식에 맞게 생성. 새 작업 시작 전 호출.
when_to_use: 사용자가 "이슈 생성해줘", "이슈 만들어줘", "이슈 파줘", "이슈 먼저" 등 GitHub 이슈 생성을 요청할 때. 구현 요청에 이슈가 없을 때 자동 제안.
argument-hint: "[이슈 설명]"
allowed-tools: Bash(gh issue create *) Bash(gh issue list *) Bash(gh label list *) Bash(cat *)
---

## 언제 쓰나?

- 이슈 없이 구현 요청이 들어왔을 때 (항상 코드 전에 이슈 먼저)
- 새 작업 시작 전 사용자가 직접 `/create-issue` 입력 시
- **이 스킬 완료 후 → `/bdd-implement #{생성된 번호}`**

## 실행 절차

### Step 1. 타입 감지

사용자 설명(`$ARGUMENTS`)을 분석해 타입을 결정한다:
- `fix` → "오류", "에러", "버그", "안됨", "실패", "500", "400" 포함 시
- `feat` → "기능", "추가", "구현", "API", "만들어", "개발" 포함 시
- `chore` or `refactor` → "리팩토링", "설정", "분리", "정리", "의존성" 포함 시

타입이 불명확하면 사용자에게 확인.

### Step 2. 이슈 제목 생성

`.claude/rules/git-conventions.md` 형식 준수:
```
{tag}: {설명}
```
예) `feat: 카카오 로그인 API 구현`

사용자 확인 후 진행.

### Step 3. 이슈 본문 작성

타입별로 `.github/ISSUE_TEMPLATE/` 템플릿 구조를 따른다.

**feat 타입 본문:**
```
## ✨ 기능 설명
{설명}

## 📌 배경 및 목적
{배경}

## 📝 작업 내용
- [ ] 
- [ ] 

## 🔌 API 명세 (선택)

## 💬 특이사항 (선택)
```

**fix 타입 본문:**
```
## 🐛 버그 설명
{설명}

## 🔁 재현 방법
1. 

## ✅ 기대 결과 / ❌ 실제 결과
기대: 
실제: 

## 📝 추가 정보 (선택)
```

**chore/refactor 타입 본문:**
```
## 📌 작업 내용
{설명}

## 🔄 AS-IS / TO-BE
AS-IS: 
TO-BE: 

## 💬 특이사항 (선택)
```

사용자에게 본문 초안을 보여주고 수정 여부 확인.

### Step 4. GitHub 이슈 생성

```bash
gh issue create \
  --title "{tag}: {설명}" \
  --label "{label}" \
  --body "{작성된 본문}"
```

label 매핑: `feat` → `Feature`, `fix` → `Hotfix`, `chore/refactor` → `Refactor`

### Step 5. 완료 보고

생성된 이슈 URL과 번호 출력.
→ "이제 `/bdd-implement #{번호}` 로 구현을 시작하세요."
