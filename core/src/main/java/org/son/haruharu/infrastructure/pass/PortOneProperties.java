package org.son.haruharu.infrastructure.pass;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "portone")
public record PortOneProperties(String storeId, String apiSecret, String channelKey) {}
