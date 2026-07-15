package org.son.haruharu.admin.domain.account.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.admin.domain.account.business.command.CreateAdminAccountCommand;
import org.son.haruharu.admin.domain.account.business.command.UpdateAdminAccountCommand;
import org.son.haruharu.admin.domain.account.business.dto.AdminAccountResponse;
import org.son.haruharu.admin.domain.account.business.exception.AdminAccountDuplicateException;
import org.son.haruharu.admin.domain.account.implement.AdminAccount;
import org.son.haruharu.admin.domain.account.implement.AdminAccountReader;
import org.son.haruharu.admin.domain.account.implement.AdminAccountStatus;
import org.son.haruharu.admin.domain.account.implement.AdminAccountWriter;
import org.son.haruharu.admin.domain.account.implement.AdminRole;
import org.son.haruharu.admin.domain.account.storage.exception.AdminAccountNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminAccountServiceTest {

    @Mock AdminAccountReader adminAccountReader;
    @Mock AdminAccountWriter adminAccountWriter;
    @Mock PasswordEncoder passwordEncoder;

    AdminAccountService service;

    @BeforeEach
    void setUp() {
        service = new AdminAccountService(adminAccountReader, adminAccountWriter, passwordEncoder);
    }

    private AdminAccount account(Long id) {
        return new AdminAccount(id, "ops@haruharu.co.kr", "hashed", "운영자",
                AdminRole.OPERATOR, AdminAccountStatus.ACTIVE, null);
    }

    @Test
    @DisplayName("신규 이메일이면 비밀번호를 해시해 계정을 생성한다")
    void create_whenNew_hashesAndSaves() {
        given(adminAccountReader.existsByEmail("ops@haruharu.co.kr")).willReturn(false);
        given(passwordEncoder.encode("raw-pass")).willReturn("hashed");
        given(adminAccountWriter.save(any())).willReturn(account(1L));

        AdminAccountResponse result = service.create(
                new CreateAdminAccountCommand("ops@haruharu.co.kr", "raw-pass", "운영자", AdminRole.OPERATOR));

        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.email()).isEqualTo("ops@haruharu.co.kr");
        assertThat(result.role()).isEqualTo(AdminRole.OPERATOR);
        verify(passwordEncoder).encode("raw-pass");
    }

    @Test
    @DisplayName("이미 존재하는 이메일이면 AdminAccountDuplicateException(409)")
    void create_whenDuplicate_throws() {
        given(adminAccountReader.existsByEmail("ops@haruharu.co.kr")).willReturn(true);

        assertThatThrownBy(() -> service.create(
                new CreateAdminAccountCommand("ops@haruharu.co.kr", "raw-pass", "운영자", AdminRole.OPERATOR)))
                .isInstanceOf(AdminAccountDuplicateException.class);
        verify(adminAccountWriter, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    @Test
    @DisplayName("수정은 보낸 필드만 갱신하고 이메일·비밀번호는 유지한다")
    void update_partial_keepsUnchanged() {
        given(adminAccountReader.getById(1L)).willReturn(account(1L));
        given(adminAccountWriter.save(any())).willAnswer(inv -> inv.getArgument(0));

        // 상태만 INACTIVE로 변경
        AdminAccountResponse result = service.update(1L,
                new UpdateAdminAccountCommand(null, AdminAccountStatus.INACTIVE));

        assertThat(result.status()).isEqualTo(AdminAccountStatus.INACTIVE);
        assertThat(result.role()).isEqualTo(AdminRole.OPERATOR); // 유지
        assertThat(result.email()).isEqualTo("ops@haruharu.co.kr"); // 불변
    }

    @Test
    @DisplayName("없는 계정 수정 시 AdminAccountNotFoundException(404)")
    void update_whenNotFound_throws() {
        given(adminAccountReader.getById(99L)).willThrow(new AdminAccountNotFoundException());

        assertThatThrownBy(() -> service.update(99L,
                new UpdateAdminAccountCommand(AdminRole.CS, null)))
                .isInstanceOf(AdminAccountNotFoundException.class);
    }
}
