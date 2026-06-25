package org.son.haruharu.domain.profile.storage;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.son.haruharu.common.entity.BaseEntity;
import org.son.haruharu.domain.profile.implement.UserProfile;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "user_profiles",
       uniqueConstraints = @UniqueConstraint(name = "uk_user_profiles_user_id", columnNames = "user_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserProfileEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false, columnDefinition = "uuid")
    private UUID userId;

    @Column(length = 10)
    private String gender;

    private LocalDate birthday;

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

    @Column(length = 20)
    private String religion;

    public static UserProfileEntity from(UserProfile userProfile) {
        var entity = new UserProfileEntity();
        entity.id = userProfile.id();
        entity.userId = userProfile.userId();
        entity.gender = userProfile.gender();
        entity.birthday = userProfile.birthday();
        entity.location = userProfile.location();
        entity.affiliation = userProfile.affiliation();
        entity.height = userProfile.height();
        entity.mbti = userProfile.mbti();
        entity.nickname = userProfile.nickname();
        entity.drinkingStatus = userProfile.drinkingStatus();
        entity.smokingStatus = userProfile.smokingStatus();
        entity.religion = userProfile.religion();
        return entity;
    }

    public UserProfile toDomain() {
        return new UserProfile(
                id,
                userId,
                gender,
                birthday,
                location,
                affiliation,
                height,
                mbti,
                nickname,
                drinkingStatus,
                smokingStatus,
                religion
        );
    }
}
