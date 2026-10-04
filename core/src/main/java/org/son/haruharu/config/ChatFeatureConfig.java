package org.son.haruharu.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ChatFeatureProperties.class)
public class ChatFeatureConfig {}
