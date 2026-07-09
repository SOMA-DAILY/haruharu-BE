package org.son.haruharu.config;

import org.son.haruharu.domain.matching.business.MatchingProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(MatchingProperties.class)
public class MatchingConfig {}
