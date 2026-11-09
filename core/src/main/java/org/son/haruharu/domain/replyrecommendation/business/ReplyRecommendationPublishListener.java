package org.son.haruharu.domain.replyrecommendation.business;

import java.time.Clock;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.replyrecommendation.implement.AnalysisRequestRegistrar;
import org.son.haruharu.domain.replyrecommendation.implement.ReplyRecommendationProperties;
import org.son.haruharu.domain.video.business.event.VideoUploadedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 게시 트랜잭션 안에서 분석 요청을 넣는다(outbox, 명세 2장). 같은 트랜잭션이라 게시가 롤백되면 요청도 없다.
 * 모델 호출은 하지 않는다 — 스냅샷과 분석은 커밋 뒤 따로 진행된다.
 */
@Component
@RequiredArgsConstructor
public class ReplyRecommendationPublishListener {

    private final ReplyRecommendationProperties properties;
    private final AnalysisRequestRegistrar registrar;
    private final Clock clock;

    @EventListener
    public void onVideoUploaded(VideoUploadedEvent event) {
        if (!properties.enabledFor(event.roomId())) {
            return;
        }
        registrar.registerPublished(event.videoPostId(), event.roomMemberId(), clock.instant());
    }
}
