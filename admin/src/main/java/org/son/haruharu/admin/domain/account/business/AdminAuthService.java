package org.son.haruharu.admin.domain.account.business;

import org.son.haruharu.admin.domain.account.business.command.AdminLoginCommand;
import org.son.haruharu.admin.domain.account.business.dto.AdminMeResponse;
import org.son.haruharu.admin.domain.account.business.dto.AdminTokenResponse;
import org.son.haruharu.admin.domain.account.business.exception.AdminLoginFailedException;
import org.son.haruharu.admin.domain.account.implement.AdminAccount;
import org.son.haruharu.admin.domain.account.implement.AdminAccountReader;
import org.son.haruharu.admin.domain.account.implement.AdminAccountWriter;
import org.son.haruharu.admin.security.AdminJwtProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/** 어드민 인증 유스케이스(로그인·내 정보·로그아웃). */
@Service
@Transactional(readOnly = true)
public class AdminAuthService {

    private final AdminAccountReader adminAccountReader;
    private final AdminAccountWriter adminAccountWriter;
    private final PasswordEncoder passwordEncoder;
    private final AdminJwtProvider adminJwtProvider;
    private final Clock clock;
    /** 계정 미존재 시에도 BCrypt를 수행해 로그인 소요시간을 동일하게 맞추는 더미 해시(계정 열거 타이밍 방어). */
    private final String dummyPasswordHash;

    public AdminAuthService(AdminAccountReader adminAccountReader, AdminAccountWriter adminAccountWriter,
                            PasswordEncoder passwordEncoder, AdminJwtProvider adminJwtProvider, Clock clock) {
        this.adminAccountReader = adminAccountReader;
        this.adminAccountWriter = adminAccountWriter;
        this.passwordEncoder = passwordEncoder;
        this.adminJwtProvider = adminJwtProvider;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode("__admin_login_timing_guard__");
    }

    /**
     * 이메일·비밀번호 검증 후 role claim이 담긴 토큰 쌍을 발급한다. 실패는 계정 존재 여부를 노출하지 않는 단일 예외로.
     * 계정이 없어도 더미 해시로 BCrypt를 항상 수행해 타이밍으로 이메일 존재를 추론하지 못하게 한다.
     */
    @Transactional
    public AdminTokenResponse login(AdminLoginCommand command) {
        Optional<AdminAccount> found = adminAccountReader.findByEmail(command.email());
        String passwordHash = found.map(AdminAccount::passwordHash).orElse(dummyPasswordHash);
        boolean passwordMatches = passwordEncoder.matches(command.password(), passwordHash);

        AdminAccount account = found
                .filter(AdminAccount::isActive)
                .filter(a -> passwordMatches)
                .orElseThrow(AdminLoginFailedException::new);
        adminAccountWriter.save(account.withLastLoginAt(LocalDateTime.now(clock)));

        String accessToken = adminJwtProvider.generateAccessToken(account.id(), account.role());
        String refreshToken = adminJwtProvider.generateRefreshToken(account.id(), account.role());
        return new AdminTokenResponse(accessToken, refreshToken);
    }

    public AdminMeResponse getMe(Long adminId) {
        return AdminMeResponse.from(adminAccountReader.getById(adminId));
    }

    /**
     * 로그아웃. 현재 어드민 토큰은 stateless(서버 보관 없음)라 클라이언트가 토큰을 폐기하면 된다.
     * refresh 토큰 서버 무효화(admin_refresh_tokens)는 후속 이슈에서 추가한다.
     */
    public void logout(Long adminId) {
        // no-op (stateless). 자리 표시: 후속 이슈에서 refresh 토큰 회수 추가.
    }
}
