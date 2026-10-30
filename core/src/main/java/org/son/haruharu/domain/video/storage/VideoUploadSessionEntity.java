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
@org.hibernate.annotations.SQLRestriction("member_removed_at is null")
@Entity
@Table(name = "video_upload_sessions", indexes = {
        @Index(name = "idx_video_upload_member_slot", columnList = "memberId, slotStart")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VideoUploadSessionEntity {

    /** 관리자 퇴장에 따른 비노출. 원본·일반 삭제 상태는 별도로 보존한다. */
    @Column(name = "member_removed_at", updatable=false)
    private java.time.LocalDateTime memberRemovedAt;

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
