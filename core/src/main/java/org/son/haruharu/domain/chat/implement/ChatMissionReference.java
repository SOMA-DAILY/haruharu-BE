package org.son.haruharu.domain.chat.implement;

/** 공용 방송에 포함해도 되는 미션 식별 정보. 상태·개인 응답은 미션 조회에서 제공한다. */
public record ChatMissionReference(Long missionId, Long announcementId, String announcementType,
                                   String interactionType) {
}
