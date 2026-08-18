package org.son.haruharu.demo.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.auth.implement.UserCredential;
import org.son.haruharu.domain.auth.implement.UserCredentialWriter;
import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.profile.implement.UserProfileWriter;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.son.haruharu.domain.user.implement.UserWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DemoUserProvisionerTest {

    private static final String PROVIDER_ID = "haruharu-demo";
    private static final Long DEMO_USER_ID = 7L;

    @Mock UserWriter userWriter;
    @Mock UserCredentialWriter userCredentialWriter;
    @Mock UserProfileWriter userProfileWriter;

    DemoUserProvisioner provisioner;

    @BeforeEach
    void setUp() {
        provisioner = new DemoUserProvisioner(userWriter, userCredentialWriter, userProfileWriter);
    }

    @Test
    @DisplayName("ACTIVE 유저·DEMO 크레덴셜·프로필(닉네임·지역)을 함께 생성한다")
    void whenCreate_thenSeedsActiveUserCredentialAndProfile() {
        given(userWriter.save(any())).willReturn(new User(DEMO_USER_ID, UserStatus.ACTIVE, null));

        Long userId = provisioner.create(PROVIDER_ID);

        assertThat(userId).isEqualTo(DEMO_USER_ID);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userWriter).save(userCaptor.capture());
        assertThat(userCaptor.getValue().status()).isEqualTo(UserStatus.ACTIVE);

        ArgumentCaptor<UserCredential> credCaptor = ArgumentCaptor.forClass(UserCredential.class);
        verify(userCredentialWriter).save(credCaptor.capture());
        assertThat(credCaptor.getValue().providerType()).isEqualTo("DEMO");
        assertThat(credCaptor.getValue().providerId()).isEqualTo(PROVIDER_ID);
        assertThat(credCaptor.getValue().userId()).isEqualTo(DEMO_USER_ID);

        // ACTIVE 유저는 프로필이 있어야 /users/me 가 안 깨진다
        ArgumentCaptor<UserProfile> profileCaptor = ArgumentCaptor.forClass(UserProfile.class);
        verify(userProfileWriter).save(profileCaptor.capture());
        assertThat(profileCaptor.getValue().userId()).isEqualTo(DEMO_USER_ID);
        assertThat(profileCaptor.getValue().nickname()).isNotBlank();
    }
}
