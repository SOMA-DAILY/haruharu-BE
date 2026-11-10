package org.son.haruharu.domain.video.storage;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.son.haruharu.domain.video.implement.VideoCaptionRevision;

@Entity
@Table(name = "video_caption_revisions",
        indexes = @Index(name = "idx_video_caption_revisions_post", columnList = "video_post_id, changed_at"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VideoCaptionRevisionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "video_post_id", nullable = false)
    private Long videoPostId;

    @Column(name = "previous_caption", length = 100)
    private String previousCaption;

    @Column(name = "new_caption", length = 100)
    private String newCaption;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    public static VideoCaptionRevisionEntity from(VideoCaptionRevision revision) {
        VideoCaptionRevisionEntity entity = new VideoCaptionRevisionEntity();
        entity.id = revision.id();
        entity.videoPostId = revision.videoPostId();
        entity.previousCaption = revision.previousCaption();
        entity.newCaption = revision.newCaption();
        entity.changedAt = revision.changedAt();
        return entity;
    }

    public VideoCaptionRevision toDomain() {
        return new VideoCaptionRevision(id, videoPostId, previousCaption, newCaption, changedAt);
    }
}
