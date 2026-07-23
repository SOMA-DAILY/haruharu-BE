package org.son.haruharu.domain.video.business;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 데모 피드 플레이스홀더 자산 설정(#280). 하드코딩된 외부 URL이 접근 불가(403)로 죽어도
 * 코드 재배포 없이 env로 교체할 수 있도록 외부화한다.
 * <p>
 * videoUrl은 접근 가능한 자산으로 반드시 지정해야 한다(권장: 우리 S3 오브젝트 — 피드가 우리 버킷 URL은
 * presign 서명해 내려주므로 private이어도 재생된다). 미지정(빈 값)이면 영상이 재생되지 않는다.
 */
@ConfigurationProperties(prefix = "haruharu.demo.feed")
public record DemoFeedProperties(
        @DefaultValue("") String videoUrl,
        @DefaultValue("https://picsum.photos/seed/haruharu-demo-feed/600/800") String thumbnailUrl
) {
}
