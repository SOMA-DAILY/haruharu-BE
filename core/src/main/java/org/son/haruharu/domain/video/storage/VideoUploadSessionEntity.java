package org.son.haruharu.domain.video.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.son.haruharu.domain.video.implement.VideoUploadSession;

import java.time.LocalDateTime;
import java.util.UUID;

/** 기기 업로드 세션. 게시 완료 행도 멱등 재시도 응답을 위해 보존한다. */
@Entity
@Table(name = "video_upload_sessions", indexes = {
        @Index(name = "idx_video_upload_member_slot", columnList = "memberId, slotStart")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VideoUploadSessionEntity {

    @Id
    private UUID id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long roomId;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private LocalDateTime slotStart;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    // #482: 기존 긴 캡션 세션의 완료·재시도를 위해 저장 길이는 축소하지 않는다.
    @Column(length = 100)
    private String caption;

    private Long postId;

    @Column(nullable = false)
    private boolean cancelled;

    public static VideoUploadSessionEntity from(VideoUploadSession session) {
        VideoUploadSessionEntity entity = new VideoUploadSessionEntity();
        entity.id = session.id();
        entity.userId = session.userId();
        entity.roomId = session.roomId();
        entity.memberId = session.memberId();
        entity.slotStart = session.slotStart();
        entity.expiresAt = session.expiresAt();
        entity.caption = session.caption();
        entity.postId = session.postId();
        entity.cancelled = session.isCancelled();
        return entity;
    }

    public VideoUploadSession toDomain() {
        return new VideoUploadSession(id, userId, roomId, memberId, slotStart, expiresAt, caption, postId, cancelled);
    }
}
