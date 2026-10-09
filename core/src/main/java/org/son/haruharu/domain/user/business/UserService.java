package org.son.haruharu.domain.user.business;

import org.son.haruharu.domain.auth.business.AuthService;
import org.son.haruharu.domain.ticket.business.TicketService;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.util.Optional;

@Slf4j
@Service
@Transactional(readOnly = true)
public class UserService {

    private final UserReader userReader;
    private final UserWriter userWriter;
    private final ProfileService profileService;
    private final IdentityService identityService;
    private final ValueService valueService;
    private final TicketService ticketService;
    private final AuthService authService;
    private final Clock clock;

    public UserService(
        UserReader userReader,
        UserWriter userWriter,
        ProfileService profileService,
        IdentityService identityService,
        ValueService valueService,
        TicketService ticketService,
        AuthService authService,
        Clock clock
    ) {
        this.userReader = userReader;
        this.userWriter = userWriter;
        this.profileService = profileService;
        this.identityService = identityService;
        this.valueService = valueService;
        this.ticketService = ticketService;
        this.authService = authService;
        this.clock = clock;
    }

    public UserProfileResponse getMyProfile(Long userId) {
        userReader.getById(userId); // 존재 검증(없으면 404) — 프로필 응답 전 유저 존재 보장.
        ProfileResponse profile = profileService.getProfile(userId);
        Optional<VerifiedIdentity> verified = identityService.findVerified(userId);
        // 성별: 본인인증(PASS)이 있으면 그 검증된 값이 우선, 없으면 온보딩 자기신고 프로필 gender 로 폴백(#392).
        // PASS 지연인증이라 대부분 미검증 → 프로필 gender 가 실질 소스다.
        String gender = verified.map(VerifiedIdentity::gender)
            .orElseGet(() -> profile.gender() != null ? profile.gender().name() : null);
        Integer age = verified.map(VerifiedIdentity::birthday)
            .map(birthday -> Period.between(birthday, LocalDate.now(clock)).getYears())
            .orElse(null);
        VerificationStatus status = VerificationStatus.of(verified.isPresent());
        return UserProfileResponse.from(userId, profile, gender, age, status.name());
    }

    /**
     * 탈퇴한 유저인지. 탈퇴는 users 행을 남기고 status만 DELETED로 전이하므로 status로 판정한다.
     * 탈퇴 시 프로필은 soft-delete되어 조회 자체가 불가하므로, 다른 도메인이 상대 유저를 다룰 때
     * 프로필 조회 전에 이걸로 먼저 걸러야 한다(#255).
     */
    public boolean isWithdrawn(Long userId) {
        return userReader.getById(userId).status() == UserStatus.DELETED;
    }

    /**
     * 온보딩 완료 처리. 닉네임 등록을 서버가 검증한 뒤 ONBOARDING → ACTIVE로 전이한다.
     * 이미 온보딩을 벗어난 상태면 재저장 없이 현재 라우팅 상태만 반환한다(멱등).
     */
    @Transactional
    public OnboardingStatusResponse completeOnboarding(Long userId) {
        User user = userReader.getById(userId);
        if (!user.status().canAuthenticate()) {
            throw new InactiveUserException();
        }
        if (user.status() == UserStatus.ONBOARDING) {
            if (!profileService.hasCompletedProfile(userId)) {
                throw new OnboardingIncompleteException();
            }
            userWriter.save(new User(userId, UserStatus.ACTIVE, user.createdAt()));
        }
        boolean verified = identityService.findVerified(userId).isPresent();
        String status = verified ? VerificationStatus.VERIFIED.name() : UserStatus.ACTIVE.name();
        return new OnboardingStatusResponse(status);
    }

    /**
     * 회원 탈퇴. 프로필·가치관·티켓 지갑 등 유저 관련 데이터를 soft-delete 캐스케이드한 뒤 상태를 DELETED로 전이한다.
     * soft-delete라 데이터는 물리적으로 남아 결제 보관·환불 대응이 가능하다.
     * identity는 물리삭제하지 않고 soft로 남겨 재가입 차단 근거(CI)를 30일간 보존한다(#122).
     * user_credentials도 soft-delete해 같은 OAuth 재가입 쿨다운(30일)의 기준 시각을 남긴다(#129).
     */
    @Transactional
    public void withdraw(Long userId) {
        User user = userReader.getById(userId);
        profileService.deleteProfileAndImages(userId);
        valueService.deleteAnswers(userId);
        ticketService.deleteWallet(userId);
        identityService.deleteIdentity(userId);
        authService.deleteCredentials(userId);
        userWriter.save(new User(userId, UserStatus.DELETED, user.createdAt()));
    }
}
