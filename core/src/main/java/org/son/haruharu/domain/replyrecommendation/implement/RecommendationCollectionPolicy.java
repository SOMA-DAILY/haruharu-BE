package org.son.haruharu.domain.replyrecommendation.implement;

import java.util.Set;

import org.springframework.stereotype.Component;

/** 노출·답장 자료를 남길지, 남긴다면 출처를 무엇으로 표시할지 정한다. */
@Component
public class RecommendationCollectionPolicy {

    private final boolean realUserEnabled;
    private final Set<Long> qaUserIds;

    public RecommendationCollectionPolicy(ReplyRecommendationProperties properties) {
        this.realUserEnabled = properties.collection().realUserEnabled();
        this.qaUserIds = Set.copyOf(properties.collection().qaUserIds());
    }

    public Provenance provenanceOf(Long userId) {
        return qaUserIds.contains(userId) ? Provenance.QA : Provenance.REAL_USER;
    }

    public boolean collectable(Long userId) {
        return realUserEnabled || qaUserIds.contains(userId);
    }
}
