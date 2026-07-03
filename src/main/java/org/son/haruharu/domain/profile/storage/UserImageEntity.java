package org.son.haruharu.domain.profile.storage;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.son.haruharu.common.entity.BaseEntity;
import org.son.haruharu.domain.profile.implement.UserImage;

import java.util.UUID;

@Entity
@Table(name = "user_images")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserImageEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 512)
    private String imageUrl;

    @Column(nullable = false)
    private int sortedOrder;

    public static UserImageEntity from(UserImage userImage) {
        var entity = new UserImageEntity();
        entity.id = userImage.id();
        entity.userId = userImage.userId();
        entity.imageUrl = userImage.imageUrl();
        entity.sortedOrder = userImage.sortedOrder();
        return entity;
    }

    public UserImage toDomain() {
        return new UserImage(id, userId, imageUrl, sortedOrder);
    }
}
