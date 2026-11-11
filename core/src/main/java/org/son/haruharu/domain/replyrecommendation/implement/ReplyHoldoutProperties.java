package org.son.haruharu.domain.replyrecommendation.implement;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * 추천 효과 무작위 비교(이슈 #560). 기본 OFF(ratio 0)이고, 0 이면 배정하지 않고 지금과 같이 동작한다.
 * 켜기 전에 scripts/sql/561-reply-effect-up.sql 을 적용한다.
 *
 * @param ratio 카드를 보여주지 않는 시청자 비율(0 이상 0.9 이하). 실험할 때 0.5
 * @param salt  배정 해시에 섞는 고정 문자열. 실험 중 바꾸면 새로 들어오는 방의 배정이 달라진다(이미 넣은 배정은 그대로)
 */
@Validated
@ConfigurationProperties(prefix = "haruharu.reply-recommendation.holdout")
public record ReplyHoldoutProperties(
        @DefaultValue("0") @DecimalMin("0") @DecimalMax("0.9") double ratio,
        @DefaultValue("haruharu-holdout-v1") @NotBlank String salt
) {

    public boolean enabled() {
        return ratio > 0;
    }
}
