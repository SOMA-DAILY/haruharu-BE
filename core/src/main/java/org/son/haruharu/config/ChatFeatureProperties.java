package org.son.haruharu.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 채팅 P0 기능 플래그와 운영 전용 시스템 유저(#455). 플래그는 기본 OFF 로 배포하고 앱 배포 뒤 켠다.
 * <ul>
 *   <li>admin-message.enabled: 방 편성 발송 잡(운영 글, ADMIN_MESSAGE / PROFILE_REVEAL 푸시)</li>
 *   <li>reply-push.enabled: 답장 받은 사람에게 CHAT 대신 CHAT_REPLY 푸시</li>
 *   <li>system-user-id: 운영 글 sender_id 로 쓰는 시스템 유저(닉네임 '하루'). 채팅방 멤버가 아니다</li>
 * </ul>
 * app 서버만 바인딩한다(ChatFeatureConfig). admin 서버는 발송하지 않는다.
 */
@ConfigurationProperties(prefix = "haruharu.chat")
public record ChatFeatureProperties(
        @DefaultValue AdminMessage adminMessage,
        @DefaultValue ReplyPush replyPush,
        Long systemUserId
) {
    public record AdminMessage(@DefaultValue("false") boolean enabled) {}

    public record ReplyPush(@DefaultValue("false") boolean enabled) {}

    public boolean replyPushEnabled() {
        return replyPush != null && replyPush.enabled();
    }
}
