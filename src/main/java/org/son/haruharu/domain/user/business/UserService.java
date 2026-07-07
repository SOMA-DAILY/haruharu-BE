package org.son.haruharu.domain.user.business;

import org.son.haruharu.domain.identity.business.IdentityService;
import org.son.haruharu.domain.identity.business.dto.VerifiedIdentity;
import org.son.haruharu.domain.identity.implement.VerificationStatus;
import org.son.haruharu.domain.profile.business.ProfileService;
import org.son.haruharu.domain.profile.business.dto.ProfileResponse;
import org.son.haruharu.domain.user.business.dto.OnboardingStatusResponse;
import org.son.haruharu.domain.user.business.dto.UserProfileResponse;
import org.son.haruharu.domain.user.business.exception.InactiveUserException;
import org.son.haruharu.domain.user.business.exception.OnboardingIncompleteException;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.implement.UserReader;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.son.haruharu.domain.user.implement.UserWriter;
import org.son.haruharu.domain.value.business.ValueService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserReader userReader;
    private final UserWriter userWriter;
    private final ProfileService profileService;
    private final IdentityService identityService;
    private final ValueService valueService;
    private final Clock clock;

    public UserService(
        UserReader userReader,
        UserWriter userWriter,
        ProfileService profileService,
        IdentityService identityService,
        ValueService valueService,
        Clock clock
    ) {
        this.userReader = userReader;
        this.userWriter = userWriter;
        this.profileService = profileService;
        this.identityService = identityService;
        this.valueService = valueService;
        this.clock = clock;
    }

    public UserProfileResponse getMyProfile(Long userId) {
        ProfileResponse profile = profileService.getProfile(userId);
        Optional<VerifiedIdentity> verified = identityService.findVerified(userId);
        String gender = verified.map(VerifiedIdentity::gender).orElse(null);
        Integer age = verified.map(VerifiedIdentity::birthday)
            .map(birthday -> Period.between(birthday, LocalDate.now(clock)).getYears())
            .orElse(null);
        VerificationStatus status = VerificationStatus.of(verified.isPresent());
        return UserProfileResponse.from(userId, profile, gender, age, status.name());
    }

    /**
     * 온보딩 완료 처리. 프로필·가치관·이미지 완료를 서버가 검증한 뒤 ONBOARDING → ACTIVE로 전이한다.
     * 이미 온보딩을 벗어난 상태면 재저장 없이 현재 라우팅 상태만 반환한다(멱등).
     */
    @Transactional
    public OnboardingStatusResponse completeOnboarding(Long userId) {
        User user = userReader.getById(userId);
        if (!user.status().canAuthenticate()) {
            throw new InactiveUserException();
        }
        if (user.status() == UserStatus.ONBOARDING) {
            if (!profileService.hasCompletedProfile(userId) || !valueService.hasAnswered(userId)) {
                throw new OnboardingIncompleteException();
            }
            userWriter.save(new User(userId, UserStatus.ACTIVE, user.createdAt()));
        }
        boolean verified = identityService.findVerified(userId).isPresent();
        String status = verified ? VerificationStatus.VERIFIED.name() : UserStatus.ACTIVE.name();
        return new OnboardingStatusResponse(status);
    }

    @Transactional
    public void withdraw(Long userId) {
        profileService.deleteAllImages(userId);
        identityService.deleteIdentity(userId);
        User user = userReader.getById(userId);
        userWriter.save(new User(userId, UserStatus.DELETED, user.createdAt()));
    }
}
