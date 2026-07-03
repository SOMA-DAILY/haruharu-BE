package org.son.haruharu.domain.profile.storage;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.son.haruharu.common.entity.BaseEntity;
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

    @Column(length = 20)
    private String drinkingStatus;

    @Column(length = 20)
    private String smokingStatus;

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
                smokingStatus
        );
    }
}
