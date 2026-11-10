package org.son.haruharu.config;

import org.son.haruharu.domain.replyrecommendation.implement.ReplyDepthProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ReplyDepthProperties.class)
public class ReplyDepthConfig {}
