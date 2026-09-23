package org.son.haruharu.domain.video.storage;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.UUID;
import org.son.haruharu.domain.video.implement.VideoUploadSession;
@Entity
@Table(name="video_upload_sessions", indexes=@Index(name="idx_video_upload_member_slot",columnList="memberId,slotStart"))
@NoArgsConstructor(access=AccessLevel.PROTECTED)
public class VideoUploadSessionEntity {
    @Id private UUID id;
    @Column(nullable=false) private Long userId;
    @Column(nullable=false) private Long roomId;
    @Column(nullable=false) private Long memberId;
    @Column(nullable=false) private LocalDateTime slotStart;
    @Column(nullable=false) private LocalDateTime expiresAt;
    @Column(length=20) private String caption;
    private Long postId;
    @Column(nullable=false) private boolean cancelled;
    public static VideoUploadSessionEntity from(VideoUploadSession s) {
        var e=new VideoUploadSessionEntity();
        e.id=s.id();e.userId=s.userId();e.roomId=s.roomId();e.memberId=s.memberId();e.slotStart=s.slotStart();
        e.expiresAt=s.expiresAt();e.caption=s.caption();e.postId=s.postId();e.cancelled=s.isCancelled();return e;
    }
    public VideoUploadSession toDomain() { return new VideoUploadSession(id,userId,roomId,memberId,slotStart,expiresAt,caption,postId,cancelled); }
}
