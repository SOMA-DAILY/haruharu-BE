package org.son.haruharu.config;

import org.son.haruharu.domain.room.business.UploadRewardProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(UploadRewardProperties.class)
public class UploadRewardConfig {}
