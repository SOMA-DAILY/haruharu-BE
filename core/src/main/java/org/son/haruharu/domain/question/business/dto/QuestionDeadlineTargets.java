package org.son.haruharu.domain.question.business.dto;

import java.util.List;

/**
 * 익명 질문 마감 임박 안내 대상. 두 목록은 서로 겹치지 않는다 — 답할 질문이 남은 사람은 질문권이
 * 남아 있어도 답변 재촉만 받는다. 한 사람에게 두 건을 보내지 않기 위한 분리다.
 */
public record QuestionDeadlineTargets(List<Long> unansweredUserIds, List<Long> unusedGrantUserIds) {
    public boolean isEmpty() {
        return unansweredUserIds.isEmpty() && unusedGrantUserIds.isEmpty();
    }
}
