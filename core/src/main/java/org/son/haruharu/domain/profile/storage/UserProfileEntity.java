package org.son.haruharu.domain.profile.storage;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.son.haruharu.common.entity.BaseEntity;
import org.son.haruharu.domain.profile.implement.DrinkingStatus;
import org.son.haruharu.domain.profile.implement.Gender;
import org.son.haruharu.domain.profile.implement.SmokingStatus;
import org.son.haruharu.domain.profile.implement.UserProfile;

@Entity
@Table(name = "user_profiles",
       uniqueConstraints = @UniqueConstraint(name = "uk_user_profiles_user_id", columnNames = "user_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserProfileEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(length = 100)
    private String location;

    @Column(length = 100)
    private String affiliation;

    private Integer height;

    @Column(length = 4)
    private String mbti;

    @Column(length = 20)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private DrinkingStatus drinkingStatus;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private SmokingStatus smokingStatus;

    // 온보딩 자기신고 성별(#392). 검증된 성별의 단일 출처는 identity(user_identities.gender)이며 이 값은 별개다.
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Gender gender;

    // 방 참가 전 온보딩 프로필(#455). 전부 null 허용.
    private Integer birthYear;

    @Column(length = 15)
    private String department;

    @Column(length = 40)
    private String intoNow;

    public static UserProfileEntity from(UserProfile userProfile) {
        var entity = new UserProfileEntity();
        entity.id = userProfile.id();
        entity.userId = userProfile.userId();
        entity.location = userProfile.location();
        entity.affiliation = userProfile.affiliation();
        entity.height = userProfile.height();
        entity.mbti = userProfile.mbti();
        entity.nickname = userProfile.nickname();
        entity.drinkingStatus = userProfile.drinkingStatus();
        entity.smokingStatus = userProfile.smokingStatus();
        entity.gender = userProfile.gender();
        entity.birthYear = userProfile.birthYear();
        entity.department = userProfile.department();
        entity.intoNow = userProfile.intoNow();
        return entity;
    }

    public UserProfile toDomain() {
        return new UserProfile(
                id,
                userId,
                location,
                affiliation,
                height,
                mbti,
                nickname,
                drinkingStatus,
                smokingStatus,
                gender,
                birthYear,
                department,
                intoNow
        );
    }
}
