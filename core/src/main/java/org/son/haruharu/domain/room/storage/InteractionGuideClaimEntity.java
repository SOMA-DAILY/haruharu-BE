package org.son.haruharu.domain.room.storage;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.son.haruharu.domain.room.implement.InteractionGuideClaim;
import org.son.haruharu.domain.room.implement.InteractionGuideKind;

@Entity
@Table(name = "room_interaction_guide_claims", uniqueConstraints = {
        @UniqueConstraint(name = "uk_interaction_guide_member_kind", columnNames = {"room_member_id", "kind"}),
        @UniqueConstraint(name = "uk_interaction_guide_member_session", columnNames = {"room_member_id", "viewer_session_id"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InteractionGuideClaimEntity {
    @Id @Column(length = 36) private String id;
    @Column(nullable = false) private Long roomMemberId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private InteractionGuideKind kind;
    @Column(nullable = false) private Long videoPostId;
    @Column(nullable = false, length = 100) private String viewerSessionId;
    @Column(nullable = false) private LocalDateTime claimedAt;

    public static InteractionGuideClaimEntity from(InteractionGuideClaim value) {
        var entity = new InteractionGuideClaimEntity();
        entity.id = value.id();
        entity.roomMemberId = value.roomMemberId();
        entity.kind = value.kind();
        entity.videoPostId = value.videoPostId();
        entity.viewerSessionId = value.viewerSessionId();
        entity.claimedAt = value.claimedAt();
        return entity;
    }
    public InteractionGuideClaim toDomain() {
        return new InteractionGuideClaim(id, roomMemberId, kind, videoPostId, viewerSessionId, claimedAt);
    }
}
