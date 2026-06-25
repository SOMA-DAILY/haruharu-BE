package org.son.haruharu.domain.identity.implement;

public enum VerificationStatus {
    VERIFIED,
    UNVERIFIED;

    public static VerificationStatus of(boolean verified) {
        return verified ? VERIFIED : UNVERIFIED;
    }
}
