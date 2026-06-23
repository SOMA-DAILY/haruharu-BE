package org.son.haruharu.domain.identity.business;

import org.son.haruharu.domain.identity.business.command.VerifyIdentityCommand;
import org.son.haruharu.domain.identity.business.exception.UserAlreadyVerifiedException;
import org.son.haruharu.domain.identity.implement.UserIdentity;
import org.son.haruharu.domain.identity.implement.UserIdentityReader;
import org.son.haruharu.domain.identity.implement.UserIdentityWriter;
import org.son.haruharu.infrastructure.pass.PassVerificationPort;
import org.son.haruharu.infrastructure.pass.PassVerificationResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class IdentityService {

    private final UserIdentityReader userIdentityReader;
    private final UserIdentityWriter userIdentityWriter;
    private final PassVerificationPort passVerificationPort;

    public IdentityService(
        UserIdentityReader userIdentityReader,
        UserIdentityWriter userIdentityWriter,
        PassVerificationPort passVerificationPort
    ) {
        this.userIdentityReader = userIdentityReader;
        this.userIdentityWriter = userIdentityWriter;
        this.passVerificationPort = passVerificationPort;
    }

    public boolean isVerified(UUID userId) {
        return userIdentityReader.existsByUserId(userId);
    }

    @Transactional
    public void deleteIdentity(UUID userId) {
        userIdentityWriter.deleteByUserId(userId);
    }

    @Transactional
    public void verifyIdentity(UUID userId, VerifyIdentityCommand command) {
        if (userIdentityReader.existsByUserId(userId)) {
            throw new UserAlreadyVerifiedException();
        }
        PassVerificationResult result = passVerificationPort.verify(command.passToken());
        UserIdentity identity = new UserIdentity(
            UUID.randomUUID(), userId,
            result.ci(), result.di(),
            result.phoneNumber(), result.birthday(), result.gender(),
            result.userName(), result.operator(), result.isForeigner(),
            LocalDateTime.now()
        );
        userIdentityWriter.save(identity);
    }
}
