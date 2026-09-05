package org.son.haruharu.demo.business;

import org.son.haruharu.domain.auth.implement.UserCredential;
import org.son.haruharu.domain.auth.implement.UserCredentialWriter;
import org.son.haruharu.domain.profile.implement.Gender;
import org.son.haruharu.domain.profile.implement.UserProfile;
import org.son.haruharu.domain.profile.implement.UserProfileWriter;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.son.haruharu.domain.user.implement.UserWriter;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 데모 계정 최초 생성만 담당한다(#352). 반드시 별도 컴포넌트로 분리해 {@code REQUIRES_NEW} 하위 트랜잭션에서 돈다 —
 * <p>
 * 동시 최초 로그인 레이스에서 진 트랜잭션의 크레덴셜 INSERT 는 partial unique index 에 걸려 실패하는데,
 * Postgres 는 트랜잭션 내 SQL 에러 한 번에 트랜잭션 전체를 abort 시킨다. 생성이 바깥(로그인) 트랜잭션과
 * 같은 트랜잭션이면 이후 폴백 재조회까지 "transaction is aborted"로 죽는다. REQUIRES_NEW 로 떼어내면
 * 유니크 위반이 <b>이 하위 트랜잭션만</b> 롤백하고 바깥은 살아있어, 호출부가 위반을 잡고 이긴 계정을 재조회할 수 있다.
 */
@Component
public class DemoUserProvisioner {

    /** 데모 계정을 식별하는 크레덴셜 provider 종류. 소셜(KAKAO/APPLE)과 겹치지 않는 전용 값. */
    public static final String DEMO_PROVIDER_TYPE = "DEMO";
    private static final String DEMO_NICKNAME = "하루데모";
    private static final String DEMO_LOCATION = "서울";

    private final UserWriter userWriter;
    private final UserCredentialWriter userCredentialWriter;
    private final UserProfileWriter userProfileWriter;

    public DemoUserProvisioner(UserWriter userWriter, UserCredentialWriter userCredentialWriter,
                               UserProfileWriter userProfileWriter) {
        this.userWriter = userWriter;
        this.userCredentialWriter = userCredentialWriter;
        this.userProfileWriter = userProfileWriter;
    }

    /**
     * 데모 유저를 생성한다. 온보딩을 건너뛰고 바로 앱에 진입하도록 ACTIVE 로 만들고, 최소 프로필(닉네임·지역)을 함께 시드한다
     * — ACTIVE 인데 프로필이 없으면 /users/me 가 USER_PROFILE_NOT_FOUND 로 깨진다. 코인/보너스 부수효과는 붙이지 않는다.
     * 레이스에서 지면 UserCredential INSERT 가 unique 위반으로 실패하며, 이 하위 트랜잭션만 롤백된다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long create(String providerId) {
        User user = userWriter.save(new User(null, UserStatus.ACTIVE, null));
        userCredentialWriter.save(new UserCredential(null, user.id(), DEMO_PROVIDER_TYPE, providerId));
        userProfileWriter.save(new UserProfile(null, user.id(), DEMO_LOCATION, null, null, null,
                DEMO_NICKNAME, null, null, Gender.MALE));
        return user.id();
    }
}
