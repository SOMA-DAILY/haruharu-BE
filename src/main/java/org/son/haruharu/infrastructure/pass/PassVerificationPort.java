package org.son.haruharu.infrastructure.pass;

public interface PassVerificationPort {

    PassVerificationResult verify(String passToken);
}
