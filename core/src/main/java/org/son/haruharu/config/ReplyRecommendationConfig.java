package org.son.haruharu.config;

import org.son.haruharu.domain.replyrecommendation.implement.ReplyHoldoutProperties;
import org.son.haruharu.domain.replyrecommendation.implement.ReplyRecommendationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({ReplyRecommendationProperties.class, ReplyHoldoutProperties.class})
public class ReplyRecommendationConfig {}
