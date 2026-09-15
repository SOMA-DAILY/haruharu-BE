---
name: create-pr
description: Commit, push, and open a develop-targeted PR when those actions have been explicitly approved.
---

# 커밋·PR

[AGENTS.md](../../../AGENTS.md)의 승인 범위와 [Git 컨벤션](../../../docs/engineering/git-conventions.md)을 따른다.

- 현재 브랜치, 이슈, `git status`, 기준 브랜치 대비 커밋·diff와 미커밋 변경을 확인한다. 이번 작업의 변경은 승인된 커밋 대상으로 준비하고, 다른 작업의 변경은 포함하지 않는다.
- [verify-api](../verify-api/SKILL.md)의 유효한 검증 결과와 전문 리뷰 결과를 확인한다. 동일 상태의 검증을 다시 요구하지 않는다. 필요한 미실행·실패가 있으면 해결한다.
- 커밋·푸시·PR 승인이 없으면 검증된 변경과 제안 제목·본문을 준비해 한 번 확인한다. 일부 작업만 승인되었다면 그 범위를 지킨다. 승인한 제목·본문의 사소한 작성 선택마다 다시 묻지 않는다.
- [시크릿 정책](../../../docs/engineering/secret-policy.md)으로 staged 파일을 검사하고 이번 이슈의 파일만 커밋한다. 실제 시크릿·개인정보·로컬 설정을 포함하지 않는다.
- 브랜치 `{tag}/{번호}`, 커밋·PR 제목 `[#번호]; tag: 설명`, PR 대상 `develop`을 사용한다.
- `.github/pull_request_template.md`에 문제와 변경 후 행동, 관련 이슈, 실제 검증과 제한을 작성한다. 미실행 항목은 체크하지 않고 미해당은 이유를 적는다.
- PR 본문은 임시 UTF-8 파일로 작성한다. 승인 범위에서 `git push -u origin <브랜치>` 후 `gh pr create --base develop --title '<제목>' --body-file <파일>`을 실행한다.
- 훅을 우회하거나 강제 푸시하지 않는다. 커밋과 PR URL, 검증 결과를 보고한다.
