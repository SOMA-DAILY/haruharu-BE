package org.son.haruharu.domain.profile.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.son.haruharu.common.entity.BaseEntity;
import org.son.haruharu.domain.profile.implement.ProfileFieldUnlock;

import java.util.UUID;

@Entity
@Table(
        name = "profile_field_unlocks",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"viewer_room_member_id", "target_room_member_id", "field_key"})
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProfileFieldUnlockEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "viewer_room_member_id", nullable = false, columnDefinition = "uuid")
    private UUID viewerRoomMemberId;

    @Column(name = "target_room_member_id", nullable = false, columnDefinition = "uuid")
    private UUID targetRoomMemberId;

    @Column(name = "field_key", nullable = false, length = 50)
    private String fieldKey;

    public static ProfileFieldUnlockEntity from(ProfileFieldUnlock unlock) {
        var entity = new ProfileFieldUnlockEntity();
        entity.id = unlock.id();
        entity.viewerRoomMemberId = unlock.viewerRoomMemberId();
        entity.targetRoomMemberId = unlock.targetRoomMemberId();
        entity.fieldKey = unlock.fieldKey();
        return entity;
    }

    public ProfileFieldUnlock toDomain() {
        return new ProfileFieldUnlock(id, viewerRoomMemberId, targetRoomMemberId, fieldKey, createdAt);
    }
}
