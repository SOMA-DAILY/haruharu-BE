package org.son.haruharu.demo.business;

import java.time.LocalDateTime;
import java.util.List;

import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.video.implement.SlotClock;
import org.son.haruharu.domain.video.implement.VideoPost;
import org.son.haruharu.domain.video.implement.VideoPostWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 데모 방의 피드를 채운다(#352 PR3). 방 생성 시 <b>과거 슬롯</b>에 데모+유령 전원의 영상을 now 기준으로 심어,
 * 리뷰어가 과거 조회로 활동 영상을 볼 수 있게 한다. 현재 슬롯은 비워 둬 리뷰어가 직접 촬영(CTA)할 수 있게 한다.
 * <p>
 * 영상 파일은 구 데모와 동일하게 고정 S3 자산({@code DEMO_FEED_VIDEO_URL}) 하나를 재사용한다 — DB 행은 로그인 시점에
 * 새로 만들되 파일은 미리 올려둔 것을 가리킨다. URL 이 비어 있으면(미설정) 영상 없이 촬영 CTA 만 노출된다.
 * <p>
 * 공개 가능한 슬롯을 보여주기 위해 데모 방 멤버들의 영상을 <b>전원</b> 심는다.
 * {@code VideoService.upload} 는 자기 prefix URL 만 허용(validateOwnUrl)하므로 고정 외부 URL 은 Writer 로 직접 저장한다.
 */
@Component
public class DemoVideoSeeder {

    private static final int PAST_SLOT_COUNT = 2;
    private static final String CAPTION = "하루하루 데모";

    private final VideoPostWriter videoPostWriter;
    private final String videoUrl;
    private final String thumbnailUrl;

    public DemoVideoSeeder(VideoPostWriter videoPostWriter,
                           @Value("${demo.feed.video-url:}") String videoUrl,
                           @Value("${demo.feed.thumbnail-url:https://picsum.photos/seed/haruharu-demo-feed/600/800}")
                           String thumbnailUrl) {
        this.videoPostWriter = videoPostWriter;
        this.videoUrl = videoUrl;
        this.thumbnailUrl = thumbnailUrl;
    }

    /** 직전 두 과거 슬롯(현재 슬롯 제외)에 전 멤버의 영상을 심는다. URL 미설정이면 아무것도 하지 않는다. */
    public void seed(List<RoomMember> members, LocalDateTime startsAt, LocalDateTime now) {
        if (!StringUtils.hasText(videoUrl)) {
            return; // 자산 미설정 → 영상 없이 촬영 CTA 만
        }
        LocalDateTime currentSlot = SlotClock.currentSlotStart(startsAt, now);
        List<LocalDateTime> pastSlots = SlotClock.pastSlotStarts(startsAt, currentSlot, PAST_SLOT_COUNT, null);

        for (LocalDateTime slot : pastSlots) {
            for (RoomMember member : members) {
                videoPostWriter.save(VideoPost.create(
                        member.id(), slot, null, CAPTION, videoUrl, thumbnailUrl, now));
            }
        }
    }
}
