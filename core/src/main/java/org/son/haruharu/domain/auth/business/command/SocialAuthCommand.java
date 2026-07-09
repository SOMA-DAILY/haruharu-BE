package org.son.haruharu.domain.auth.business.command;

import org.son.haruharu.domain.auth.implement.ProviderType;

public record SocialAuthCommand(ProviderType providerType, String idToken) {}
