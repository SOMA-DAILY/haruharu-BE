package org.son.haruharu.config;

import org.son.haruharu.domain.notification.business.QuietHoursProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(QuietHoursProperties.class)
public class NotificationConfig {}
