package org.son.haruharu.demo.business;

import java.util.List;
import java.util.Optional;

import org.son.haruharu.domain.auth.implement.UserCredential;
import org.son.haruharu.domain.auth.implement.UserCredentialReader;
import org.son.haruharu.domain.auth.implement.UserCredentialWriter;
import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.profile.implement.UserProfileWriter;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.son.haruharu.domain.user.implement.UserWriter;
import org.springframework.stereotype.Component;

/**
 * 데모 방을 채우는 고정 유령 멤버들을 확보한다(#352 PR2). 데모 계정처럼 자연키({@code providerType="DEMO_GHOST"} +
 * 인덱스) 로 멱등 생성해, DB 를 밀어도 다음 방 생성이 자가 복구한다. 유령은 로그인하지 않지만 크레덴셜을 두는 이유는
 * 순전히 "이미 있나?" 를 안정적으로 판정하기 위함이다(데모 계정과 동일 메커니즘).
 * <p>
 * 동시 재생성은 호출부({@code DemoRoomService})가 데모 유저 행 비관적 락으로 직렬화하므로, 여기선 별도 레이스 방어를 두지 않는다.
 */
@Component
public class DemoGhostProvisioner {

    /** 유령 식별 provider 종류. 데모 계정(DEMO)·소셜과 겹치지 않는다. */
    public static final String GHOST_PROVIDER_TYPE = "DEMO_GHOST";
    /** 데모 방 = 3:3. 데모 계정이 MALE 한 자리라, 유령은 MALE 2 + FEMALE 3 으로 6인을 채운다. */
    private static final List<String> GHOST_GENDERS = List.of("MALE", "MALE", "FEMALE", "FEMALE", "FEMALE");

    private final UserCredentialReader userCredentialReader;
    private final UserCredentialWriter userCredentialWriter;
    private final UserWriter userWriter;
    private final UserProfileWriter userProfileWriter;

    public DemoGhostProvisioner(UserCredentialReader userCredentialReader,
                                UserCredentialWriter userCredentialWriter,
                                UserWriter userWriter,
                                UserProfileWriter userProfileWriter) {
        this.userCredentialReader = userCredentialReader;
        this.userCredentialWriter = userCredentialWriter;
        this.userWriter = userWriter;
        this.userProfileWriter = userProfileWriter;
    }

    /** 유령 5명을 확보(없으면 생성)해 userId·성별과 함께 반환한다. */
    public List<DemoGhost> ensureGhosts() {
        return java.util.stream.IntStream.range(0, GHOST_GENDERS.size())
                .mapToObj(i -> new DemoGhost(ensureGhostUserId(i), GHOST_GENDERS.get(i)))
                .toList();
    }

    private Long ensureGhostUserId(int index) {
        String providerId = "ghost-" + index;
        Optional<UserCredential> existing =
                userCredentialReader.findByProviderTypeAndProviderId(GHOST_PROVIDER_TYPE, providerId);
        if (existing.isPresent()) {
            return existing.get().userId();
        }
        User user = userWriter.save(new User(null, UserStatus.ACTIVE, null));
        userCredentialWriter.save(new UserCredential(null, user.id(), GHOST_PROVIDER_TYPE, providerId));
        userProfileWriter.save(new UserProfile(null, user.id(), "서울", null, null, null,
                "하루메이트" + (index + 1), null, null));
        return user.id();
    }
}
