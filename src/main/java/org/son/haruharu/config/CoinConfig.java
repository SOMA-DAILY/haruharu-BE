package org.son.haruharu.config;

import org.son.haruharu.domain.coin.business.CoinProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(CoinProperties.class)
public class CoinConfig {
}
