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

@org.hibernate.annotations.SQLRestriction("member_removed_at is null")
@Entity
@Table(name = "video_reactions", uniqueConstraints = @UniqueConstraint(columnNames = {"video_post_id", "user_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VideoReactionEntity {

    /** 관리자 퇴장에 따른 비노출. 원본·일반 삭제 상태는 별도로 보존한다. */
    @Column(name = "member_removed_at", updatable=false)
    private java.time.LocalDateTime memberRemovedAt;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long videoPostId;

    @Column(nullable = false)
    private Long userId;

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
