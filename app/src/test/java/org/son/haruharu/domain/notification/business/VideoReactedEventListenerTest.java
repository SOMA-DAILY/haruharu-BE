package org.son.haruharu.domain.notification.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.block.implement.BlockReader;
import org.son.haruharu.domain.notification.implement.NotificationType;
import org.son.haruharu.domain.video.business.event.VideoReactedEvent;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class VideoReactedEventListenerTest {

    @Mock PushSendService pushSendService;
    @Mock BlockReader blockReader;

    VideoReactedEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new VideoReactedEventListener(pushSendService, blockReader);
    }

    @Test
    @DisplayName("반응 이벤트 수신 시 작성자에게 반응자 닉네임·반응 동사·반응 이모지를 치환 인자로 REACTION_RECEIVED 푸시를 발송한다")
    void givenEvent_whenHandle_thenSendsReactionPushWithReactorNicknameVerbAndEmoji() {
        listener.handle(new VideoReactedEvent(2L, 5L, 100L, "노을", "HEART"));

        then(pushSendService).should().sendToUsers(
                List.of(2L), NotificationType.REACTION_RECEIVED, Map.of("videoPostId", "100"),
                "노을", "마음에 들어해요", "❤️");
    }

    @ParameterizedTest
    @CsvSource({"LIKE,👍", "HEART,❤️", "LOVE,😍", "LAUGH,😂", "WOW,😮", "FIRE,🔥", "UNKNOWN,👀"})
    @DisplayName("반응 유형별 이모지를 제목 치환 인자로 넘기고, 알 수 없는 유형이면 👀로 대체한다(#446)")
    void givenReactionType_whenHandle_thenPassesMatchingEmoji(String reactionType, String emoji) {
        listener.handle(new VideoReactedEvent(2L, 5L, 100L, "노을", reactionType));

        then(pushSendService).should().sendToUsers(
                eq(List.of(2L)), eq(NotificationType.REACTION_RECEIVED), any(), eq("노을"), any(), eq(emoji));
    }

    @Test
    @DisplayName("HEART 반응 알림 제목은 ❤️로 시작하고 본문은 반응 동사를 담는다(#446)")
    void givenHeartArgs_whenFormatCopy_thenTitleStartsWithHeart() {
        Object[] args = {"B", "마음에 들어해요", "❤️"};

        assertThat(String.format(NotificationType.REACTION_RECEIVED.title(), args)).isEqualTo("❤️ B님이 반응을 남겼어요");
        assertThat(String.format(NotificationType.REACTION_RECEIVED.body(), args)).isEqualTo("회원님의 영상을 마음에 들어해요");
    }

    @Test
    @DisplayName("작성자가 반응자를 차단했으면 REACTION_RECEIVED 푸시를 발송하지 않는다(#408)")
    void givenAuthorBlockedReactor_whenHandle_thenNoPush() {
        given(blockReader.hasBlocked(2L, 5L)).willReturn(true);

        listener.handle(new VideoReactedEvent(2L, 5L, 100L, "노을", "HEART"));

        then(pushSendService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("REACTION_RECEIVED 카피는 닉네임(%1$s)·반응 동사(%2$s)·반응 이모지(%3$s) 치환 지정자를 포함한다")
    void givenReactionReceivedType_whenInspectCopy_thenContainsPositionalPlaceholders() {
        assertThat(NotificationType.REACTION_RECEIVED.title()).contains("%1$s").contains("%3$s");
        assertThat(NotificationType.REACTION_RECEIVED.body()).contains("%2$s");
    }
}
