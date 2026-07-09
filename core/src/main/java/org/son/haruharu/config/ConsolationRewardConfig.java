package org.son.haruharu.config;

import org.son.haruharu.domain.room.business.ConsolationRewardProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ConsolationRewardProperties.class)
public class ConsolationRewardConfig {
}
