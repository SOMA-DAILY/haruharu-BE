package org.son.haruharu.config;

import org.son.haruharu.domain.video.business.DemoFeedProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DemoFeedProperties.class)
public class DemoFeedConfig {
}
