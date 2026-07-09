package org.son.haruharu.infrastructure.oauth;

import org.son.haruharu.domain.auth.business.exception.InvalidIdTokenException;
import org.son.haruharu.domain.auth.implement.ProviderType;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AppleOAuthProvider implements OAuthProvider {

    private final NimbusJwtDecoder jwtDecoder;

    public AppleOAuthProvider(OAuthProperties properties) {
        OAuthProperties.ProviderConfig config = properties.getProvider("apple");
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(config.publicKeyUri()).build();
        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(config.issuer());
        OAuth2TokenValidator<Jwt> audienceValidator = new JwtClaimValidator<>(
            JwtClaimNames.AUD,
            aud -> aud instanceof List<?> list && list.contains(config.clientId())
        );
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerValidator, audienceValidator));
        this.jwtDecoder = decoder;
    }

    @Override
    public ProviderType supportedType() {
        return ProviderType.APPLE;
    }

    @Override
    public OAuthUserInfo verify(String idToken) {
        try {
            Jwt jwt = jwtDecoder.decode(idToken);
            return new OAuthUserInfo(jwt.getSubject(), ProviderType.APPLE.name());
        } catch (JwtException e) {
            throw new InvalidIdTokenException();
        }
    }
}
