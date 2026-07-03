package org.son.haruharu.domain.identity.business;

import org.son.haruharu.domain.identity.business.command.VerifyIdentityCommand;
import org.son.haruharu.domain.identity.business.dto.VerifiedIdentity;
import org.son.haruharu.domain.identity.business.exception.UserAlreadyVerifiedException;
import org.son.haruharu.domain.identity.implement.UserIdentity;
import org.son.haruharu.domain.identity.implement.UserIdentityReader;
import org.son.haruharu.domain.identity.implement.UserIdentityWriter;
import org.son.haruharu.infrastructure.pass.PassVerificationPort;
import org.son.haruharu.infrastructure.pass.PassVerificationResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
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

    public boolean isVerified(Long userId) {
        return userIdentityReader.existsByUserId(userId);
    }

    /** 본인인증된 유저의 성별·생년월일을 반환한다. 미인증(인증 정보 없음)이면 empty. */
    public Optional<VerifiedIdentity> findVerified(Long userId) {
        return userIdentityReader.findByUserId(userId)
                .map(i -> new VerifiedIdentity(i.gender(), i.birthday()));
    }

    @Transactional
    public void deleteIdentity(Long userId) {
        userIdentityWriter.deleteByUserId(userId);
    }

    @Transactional
    public void verifyIdentity(Long userId, VerifyIdentityCommand command) {
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
