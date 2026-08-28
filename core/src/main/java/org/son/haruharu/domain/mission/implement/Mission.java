package org.son.haruharu.domain.mission.implement;

/** 논리 미션. 문구와 추천 메타데이터는 불변 {@link MissionRevision}에 보관한다. */
public record Mission(
        Long id,
        Long activeRevisionId,
        boolean active
) {
    public static Mission create() {
        return new Mission(null, null, true);
    }

    public Mission activateRevision(Long revisionId) {
        if (revisionId == null) {
            throw new IllegalArgumentException("revisionId must not be null");
        }
        return new Mission(id, revisionId, active);
    }

    public Mission changeActive(boolean nextActive) {
        return new Mission(id, activeRevisionId, nextActive);
    }
}
