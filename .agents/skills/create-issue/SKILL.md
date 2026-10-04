---
name: create-issue
description: Create a GitHub issue for an agreed repository change using the repository templates.
---

# 이슈 생성

[작업 승인](../../../AGENTS.md)과 [Git 컨벤션](../../../docs/engineering/git-conventions.md)을 따른다.

- 요청 범위에 맞는 `.github/ISSUE_TEMPLATE/` 템플릿을 읽고 제목·본문을 작성한다. feat는 기대 행동, fix는 재현·기대 결과, chore는 작업 내용과 완료 기준을 포함한다.
- 계획과 이슈 생성이 이미 승인되었으면 제목·본문을 다시 승인받지 않는다. 요구사항을 바꾸는 불확실성이 있을 때만 질문한다.
- 실제 저장소에 존재하는 라벨을 확인한다. 기본 매핑은 feat → Feature, fix → Hotfix, chore/refactor → Refactor이며, 존재하지 않는 템플릿 라벨을 무조건 전달하지 않는다.
- 본문을 임시 UTF-8 파일로 작성하고 `gh issue create --repo SOMA-DAILY/haruharu-BE --title '태그: 설명' --body-file <파일> --label <라벨>`로 생성한다.
- 생성된 번호·URL을 보고한다. 구현도 승인되어 있으면 [bdd-implement](../bdd-implement/SKILL.md)로 바로 이어간다. 이슈만 요청한 경우 여기서 마친다.
