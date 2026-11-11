package org.son.haruharu.domain.replyrecommendation.implement;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * 영상 답장 Depth 라벨 수집(#559) 설정. 기본 OFF 다. 켜기 전에 scripts/sql/559-reply-depth-up.sql 을 적용한다.
 *
 * @param observationHours 답장 시각부터 이 시간 안의 답글만 센다. 모든 답장에 같은 기간을 쓴다.
 * @param batchSize        한 번 실행에서 라벨을 만들 답장 수
 * @param maxDepth         답글 경로 길이 상한. 넘으면 미관측(DEPTH_LIMIT)으로 남긴다.
 * @param maxAttempts      한 답장이 이만큼 연속 실패하면 미관측(LABEL_FAILED)으로 닫아 뒤 답장을 막지 않는다.
 *                         수치 설정은 모두 1 이상이어야 하고, 0 이하이면 앱이 시작되지 않는다.
 */
@Validated
@ConfigurationProperties(prefix = "haruharu.reply-recommendation.depth")
public record ReplyDepthProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("24") @Positive int observationHours,
        @DefaultValue("100") @Positive int batchSize,
        @DefaultValue("200") @Positive int maxDepth,
        @DefaultValue("3") @Positive int maxAttempts,
        @DefaultValue("30") @Positive int responseWindowMinutes
) {

    /** 설정 바인딩은 이 생성자(모든 칸)로 한다. */
    @ConstructorBinding
    public ReplyDepthProperties {
    }

    /** 효과 라벨 창 기본값(30분) 모양(이슈 #560 이전 호출부). */
    public ReplyDepthProperties(boolean enabled, int observationHours, int batchSize, int maxDepth, int maxAttempts) {
        this(enabled, observationHours, batchSize, maxDepth, maxAttempts, 30);
    }

    /** 라벨은 관찰 기간이 끝난 뒤 한 번 만든다. 창이 관찰 기간보다 길면 창 끝까지의 말을 다 볼 수 없다. */
    @AssertTrue(message = "response-window-minutes 는 observation-hours 안이어야 한다")
    public boolean isResponseWindowWithinObservation() {
        return responseWindowMinutes <= observationHours * 60L;
    }
}
