package org.son.haruharu.domain.notification.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.notification.implement.NotificationType;
import org.son.haruharu.domain.video.business.event.VideoReactedEvent;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class VideoReactedEventListenerTest {

    @Mock PushSendService pushSendService;

    VideoReactedEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new VideoReactedEventListener(pushSendService);
    }

    @Test
    @DisplayName("반응 이벤트 수신 시 작성자에게 반응자 닉네임·반응 동사를 치환 인자로 REACTION_RECEIVED 푸시를 발송한다")
    void givenEvent_whenHandle_thenSendsReactionPushWithReactorNicknameAndVerb() {
        listener.handle(new VideoReactedEvent(2L, 100L, "노을", "CURIOUS"));

        then(pushSendService).should().sendToUsers(
                List.of(2L), NotificationType.REACTION_RECEIVED, Map.of("videoPostId", "100"), "노을", "궁금해해요");
    }

    @Test
    @DisplayName("REACTION_RECEIVED 카피는 닉네임(%1$s)·반응 동사(%2$s) 치환 지정자를 포함한다")
    void givenReactionReceivedType_whenInspectCopy_thenContainsPositionalPlaceholders() {
        assertThat(NotificationType.REACTION_RECEIVED.title()).contains("%1$s");
        assertThat(NotificationType.REACTION_RECEIVED.body()).contains("%2$s");
    }
}
