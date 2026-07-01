package org.son.haruharu.domain.video.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.son.haruharu.domain.video.implement.VideoPost;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 슬롯당 1업로드(재촬영 제외) 유니크는 partial index(WHERE deleted_at IS NULL)라
 * JPA/H2 DDL로는 생성되지 않는다. 멱등성 보장은 #40 업로드 로직(앱 레벨 중복검사)에서 처리한다.
 */
@Entity
@Table(name = "video_posts")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VideoPostEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID roomMemberId;

    @Column(nullable = false)
    private int dayNumber;

    @Column(nullable = false)
    private int slotIndex;

    @Column
    private UUID missionId;

    @Column(length = 20)
    private String caption;

    @Column(nullable = false, length = 500)
    private String videoUrl;

    @Column(length = 500)
    private String thumbnailUrl;

    @Column(nullable = false)
    private boolean isHidden;

    @Column(length = 200)
    private String hiddenReason;

    @Column
    private LocalDateTime deletedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    public static VideoPostEntity from(VideoPost post) {
        VideoPostEntity entity = new VideoPostEntity();
        entity.id = post.id();
        entity.roomMemberId = post.roomMemberId();
        entity.dayNumber = post.dayNumber();
        entity.slotIndex = post.slotIndex();
        entity.missionId = post.missionId();
        entity.caption = post.caption();
        entity.videoUrl = post.videoUrl();
        entity.thumbnailUrl = post.thumbnailUrl();
        entity.isHidden = post.isHidden();
        entity.hiddenReason = post.hiddenReason();
        entity.deletedAt = post.deletedAt();
        entity.createdAt = post.createdAt();
        return entity;
    }

    public VideoPost toDomain() {
        return new VideoPost(id, roomMemberId, dayNumber, slotIndex, missionId, caption, videoUrl,
                thumbnailUrl, isHidden, hiddenReason, deletedAt, createdAt);
    }
}
