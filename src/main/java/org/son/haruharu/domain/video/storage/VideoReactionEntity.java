package org.son.haruharu.domain.video.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.son.haruharu.domain.video.implement.VideoReaction;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "video_reactions", uniqueConstraints = @UniqueConstraint(columnNames = {"video_post_id", "user_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VideoReactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID videoPostId;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 20)
    private String reactionType;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public static VideoReactionEntity from(VideoReaction reaction) {
        VideoReactionEntity entity = new VideoReactionEntity();
        entity.id = reaction.id();
        entity.videoPostId = reaction.videoPostId();
        entity.userId = reaction.userId();
        entity.reactionType = reaction.reactionType();
        entity.createdAt = reaction.createdAt();
        return entity;
    }

    public VideoReaction toDomain() {
        return new VideoReaction(id, videoPostId, userId, reactionType, createdAt);
    }
}
