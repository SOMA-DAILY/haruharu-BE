package org.son.haruharu.domain.identity.business;

import org.son.haruharu.domain.identity.business.command.VerifyIdentityCommand;
import org.son.haruharu.domain.identity.business.dto.VerifiedIdentity;
import org.son.haruharu.domain.identity.business.exception.DuplicateCiException;
import org.son.haruharu.domain.identity.business.exception.ReRegistrationBlockedException;
import org.son.haruharu.domain.identity.business.exception.UserAlreadyVerifiedException;
import org.son.haruharu.domain.identity.implement.UserIdentity;
import org.son.haruharu.domain.identity.implement.UserIdentityReader;
import org.son.haruharu.domain.identity.implement.UserIdentityWriter;
import org.son.haruharu.infrastructure.pass.PassVerificationPort;
import org.son.haruharu.infrastructure.pass.PassVerificationResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class IdentityService {

    /** 탈퇴 후 재가입(본인인증) 차단 기간. */
    private static final int REJOIN_BLOCK_DAYS = 30;

    private final UserIdentityReader userIdentityReader;
    private final UserIdentityWriter userIdentityWriter;
    private final PassVerificationPort passVerificationPort;
    private final Clock clock;

    public IdentityService(
        UserIdentityReader userIdentityReader,
        UserIdentityWriter userIdentityWriter,
        PassVerificationPort passVerificationPort,
        Clock clock
    ) {
        this.userIdentityReader = userIdentityReader;
        this.userIdentityWriter = userIdentityWriter;
        this.passVerificationPort = passVerificationPort;
        this.clock = clock;
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
        PassVerificationResult result = passVerificationPort.verify(command.identityVerificationId());
        if (userIdentityReader.existsByCi(result.ci())) {
            throw new DuplicateCiException();
        }
        LocalDateTime blockThreshold = LocalDateTime.now(clock).minusDays(REJOIN_BLOCK_DAYS);
        if (userIdentityReader.existsWithdrawnByCiWithin(result.ci(), blockThreshold)) {
            throw new ReRegistrationBlockedException();
        }
        // 차단 기간이 지난 탈퇴 identity 잔존 행은 unique(ci_hash) 재삽입 충돌을 막기 위해 물리 파기한다.
        userIdentityWriter.purgeWithdrawnByCiBefore(result.ci(), blockThreshold);
        UserIdentity identity = new UserIdentity(
            null, userId,
            result.ci(), result.di(),
            result.phoneNumber(), result.birthday(), result.gender(),
            result.userName(), result.operator(), result.isForeigner(),
            LocalDateTime.now(clock)
        );
        userIdentityWriter.save(identity);
    }
}
