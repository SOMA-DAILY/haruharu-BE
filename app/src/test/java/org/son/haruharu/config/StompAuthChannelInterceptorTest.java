package org.son.haruharu.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.chat.implement.ChatRoomMember;
import org.son.haruharu.domain.chat.implement.ChatRoomMemberReader;
import org.son.haruharu.utils.JwtProvider;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class StompAuthChannelInterceptorTest {
    @Mock org.springframework.context.ApplicationEventPublisher events;
    @Mock org.son.haruharu.domain.chat.business.ChatDeliveryService delivery;

    @org.junit.jupiter.api.BeforeEach
    void activeRoom() {
        org.mockito.Mockito.lenient().when(delivery.isActive(org.mockito.ArgumentMatchers.anyLong())).thenReturn(true);
    }

    @Test
    @DisplayName("인증된 참여자가 종료 방을 구독하거나 전송하면 요청만 버리고 종료 안내를 발행한다")
    void closedRoomDoesNotRaiseProtocolError() {
        given(chatRoomMemberReader.findMembership(10L, 7L)).willReturn(Optional.of(member(10L, 7L)));
        given(delivery.isActive(10L)).willReturn(false);
        for (var command : new StompCommand[]{StompCommand.SUBSCRIBE, StompCommand.SEND}) {
            var headers = StompHeaderAccessor.create(command);
            headers.setSessionId("session");
            headers.setUser(new StompPrincipal(7L));
            headers.setDestination(command == StompCommand.SUBSCRIBE ? "/sub/chat-rooms/10/events" : "/pub/chat-rooms/10/messages");
            headers.setLeaveMutable(true);
            assertThat(interceptor().preSend(message(headers), channel)).isNull();
        }
        org.mockito.Mockito.verify(events, org.mockito.Mockito.times(2)).publishEvent(
                new org.son.haruharu.infrastructure.stomp.StompRoomClosedNotice("session", "7", 10L));
    }
    @Test @DisplayName("서버 전용 STOMP 명령은 클라이언트에서 사용할 수 없다")
    void serverCommandsCannotBypassSendAuthorization() {
        for (StompCommand command : new StompCommand[]{StompCommand.MESSAGE,StompCommand.CONNECTED,StompCommand.ERROR,StompCommand.RECEIPT}) {
            var accessor=StompHeaderAccessor.create(command);
            accessor.setDestination("/sub/chat-rooms/10");
            accessor.setUser(new StompPrincipal(7L));accessor.setLeaveMutable(true);
            assertThatThrownBy(()->interceptor().preSend(message(accessor),channel)).isInstanceOf(StompAuthorizationException.class);
        }
    }
    @Test @DisplayName("CONNECT의 별칭 STOMP도 JWT 인증을 요구한다")
    void stompAliasRequiresAuthentication() {
        var accessor=StompHeaderAccessor.create(StompCommand.STOMP);accessor.setLeaveMutable(true);
        assertThatThrownBy(()->interceptor().preSend(message(accessor),channel)).isInstanceOf(StompAuthenticationException.class);
    }

    @Test
    @DisplayName("클라이언트는 브로커 토픽에 운영 메시지나 삭제 이벤트를 직접 발행할 수 없다")
    void send_toBroker_isDenied() {
        for (String destination : new String[]{"/sub/chat-rooms/10", "/queue/errors", "/user/queue/errors", "/pub/unknown"}) {
            StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
            accessor.setDestination(destination);
            accessor.setUser(new StompPrincipal(7L));
            accessor.setLeaveMutable(true);
            assertThatThrownBy(() -> interceptor().preSend(message(accessor), channel))
                    .isInstanceOf(StompAuthorizationException.class);
        }
    }

    @Test
    @DisplayName("채팅 전송은 인증된 방 참여자만 허용한다")
    void send_toMessageEndpoint_checksMembership() {
        given(chatRoomMemberReader.findMembership(10L, 7L)).willReturn(Optional.of(member(10L, 7L)));
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/pub/chat-rooms/10/messages");
        accessor.setUser(new StompPrincipal(7L));
        accessor.setLeaveMutable(true);
        assertThatCode(() -> interceptor().preSend(message(accessor), channel)).doesNotThrowAnyException();
        accessor.setUser(null);
        assertThatThrownBy(() -> interceptor().preSend(message(accessor), channel))
                .isInstanceOf(StompAuthenticationException.class);
    }

    @Mock
    JwtProvider jwtProvider;
    @Mock
    ChatRoomMemberReader chatRoomMemberReader;

    private final MessageChannel channel = mock(MessageChannel.class);

    private StompAuthChannelInterceptor interceptor() {
        return new StompAuthChannelInterceptor(jwtProvider, chatRoomMemberReader, delivery, events);
    }

    private StompHeaderAccessor connectAccessor(String authHeader) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        if (authHeader != null) {
            accessor.setNativeHeader("Authorization", authHeader);
        }
        accessor.setLeaveMutable(true);
        return accessor;
    }

    private StompHeaderAccessor subscribeAccessor(String destination, Long userId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination(destination);
        if (userId != null) {
            accessor.setUser(new StompPrincipal(userId));
        }
        accessor.setLeaveMutable(true);
        return accessor;
    }

    private Message<byte[]> message(StompHeaderAccessor accessor) {
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private ChatRoomMember member(Long chatRoomId, Long userId) {
        return new ChatRoomMember(1L, chatRoomId, userId, LocalDateTime.now(), null, null);
    }

    // ===================== CONNECT 인증 =====================

    @Test
    @DisplayName("유효한 access 토큰으로 CONNECT 시 StompPrincipal(userId)이 세션에 설정된다")
    void connect_withValidToken_setsPrincipal() {
        given(jwtProvider.isValid("valid")).willReturn(true);
        given(jwtProvider.isAccessToken("valid")).willReturn(true);
        given(jwtProvider.extractUserId("valid")).willReturn(7L);
        StompHeaderAccessor accessor = connectAccessor("Bearer valid");

        interceptor().preSend(message(accessor), channel);

        assertThat(accessor.getUser()).isInstanceOf(StompPrincipal.class);
        assertThat(((StompPrincipal) accessor.getUser()).userId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("Authorization 헤더가 없으면 CONNECT가 거부된다")
    void connect_withoutHeader_throws() {
        StompHeaderAccessor accessor = connectAccessor(null);

        assertThatThrownBy(() -> interceptor().preSend(message(accessor), channel))
                .isInstanceOf(StompAuthenticationException.class);
    }

    @Test
    @DisplayName("유효하지 않은 토큰이면 CONNECT가 거부된다")
    void connect_withInvalidToken_throws() {
        given(jwtProvider.isValid("bad")).willReturn(false);
        StompHeaderAccessor accessor = connectAccessor("Bearer bad");

        assertThatThrownBy(() -> interceptor().preSend(message(accessor), channel))
                .isInstanceOf(StompAuthenticationException.class);
    }

    @Test
    @DisplayName("subject가 숫자가 아닌 토큰(구 UUID subject)이면 CONNECT가 거부된다")
    void connect_withNonNumericSubject_throws() {
        given(jwtProvider.isValid("legacy")).willReturn(true);
        given(jwtProvider.isAccessToken("legacy")).willReturn(true);
        given(jwtProvider.extractUserId("legacy"))
                .willThrow(new NumberFormatException("For input string: \"3fa85f64-...\""));
        StompHeaderAccessor accessor = connectAccessor("Bearer legacy");

        assertThatThrownBy(() -> interceptor().preSend(message(accessor), channel))
                .isInstanceOf(StompAuthenticationException.class);
    }

    // ===================== SUBSCRIBE 인가 =====================

    @Test
    @DisplayName("참여 중인 방 토픽 구독은 허용된다")
    void subscribe_toOwnRoom_isAllowed() {
        given(chatRoomMemberReader.findMembership(10L, 7L)).willReturn(Optional.of(member(10L, 7L)));
        StompHeaderAccessor accessor = subscribeAccessor("/sub/chat-rooms/10", 7L);

        assertThatCode(() -> interceptor().preSend(message(accessor), channel)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("메시지 변경 알림 경로도 현재 방 참여자만 정확한 경로로 구독할 수 있다")
    void givenRoomMembership_whenSubscribingToMessageEvents_thenOnlyExactOwnRoomIsAllowed() {
        given(chatRoomMemberReader.findMembership(10L, 7L)).willReturn(Optional.of(member(10L, 7L)));
        given(chatRoomMemberReader.findMembership(999L, 7L)).willReturn(Optional.empty());

        assertThatCode(() -> interceptor().preSend(message(subscribeAccessor("/sub/chat-rooms/10/events", 7L)), channel))
                .doesNotThrowAnyException();
        for (String destination : new String[]{"/sub/chat-rooms/999/events", "/sub/chat-rooms/10/*", "/sub/chat-rooms/10/events/more"}) {
            assertThatThrownBy(() -> interceptor().preSend(message(subscribeAccessor(destination, 7L)), channel))
                    .isInstanceOf(StompAuthorizationException.class);
        }
    }

    @Test
    @DisplayName("참여 중인 방의 이벤트 경로(/sub/chat-rooms/{id}/events) 구독은 허용된다(#456)")
    void subscribe_toOwnRoomEvents_isAllowed() {
        given(chatRoomMemberReader.findMembership(10L, 7L)).willReturn(Optional.of(member(10L, 7L)));
        StompHeaderAccessor accessor = subscribeAccessor("/sub/chat-rooms/10/events", 7L);

        assertThatCode(() -> interceptor().preSend(message(accessor), channel)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("참여하지 않은 방의 이벤트 경로와 그 밖의 하위 경로 구독은 거부된다(#456)")
    void subscribe_toOthersRoomEventsOrOtherSuffix_throws() {
        given(chatRoomMemberReader.findMembership(999L, 7L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> interceptor().preSend(message(subscribeAccessor("/sub/chat-rooms/999/events", 7L)), channel))
                .isInstanceOf(StompAuthorizationException.class);
        assertThatThrownBy(() -> interceptor().preSend(message(subscribeAccessor("/sub/chat-rooms/10/events/x", 7L)), channel))
                .isInstanceOf(StompAuthorizationException.class);
        assertThatThrownBy(() -> interceptor().preSend(message(subscribeAccessor("/sub/chat-rooms/*/events", 7L)), channel))
                .isInstanceOf(StompAuthorizationException.class);
    }

    @Test
    @DisplayName("참여하지 않은 방 토픽 구독은 거부된다(엿듣기 차단)")
    void subscribe_toOthersRoom_throws() {
        given(chatRoomMemberReader.findMembership(999L, 7L)).willReturn(Optional.empty());
        StompHeaderAccessor accessor = subscribeAccessor("/sub/chat-rooms/999", 7L);

        assertThatThrownBy(() -> interceptor().preSend(message(accessor), channel))
                .isInstanceOf(StompAuthorizationException.class);
    }

    @Test
    @DisplayName("개인 목적지(/user/queue/errors) 구독은 세션 스코프라 멤버십 검사 없이 통과한다")
    void subscribe_toUserDestination_isAllowed() {
        StompHeaderAccessor accessor = subscribeAccessor("/user/queue/errors", 7L);

        assertThatCode(() -> interceptor().preSend(message(accessor), channel)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("와일드카드 destination(/sub/chat-rooms/*)으로 구독 시 거부된다(전 채팅방 도청 차단)")
    void subscribe_withWildcardDestination_throws() {
        StompHeaderAccessor accessor = subscribeAccessor("/sub/chat-rooms/*", 7L);

        assertThatThrownBy(() -> interceptor().preSend(message(accessor), channel))
                .isInstanceOf(StompAuthorizationException.class);
    }

    @Test
    @DisplayName("브로커 전체 와일드카드(/sub/**) 구독은 거부된다(default-deny)")
    void subscribe_withBrokerWildcard_throws() {
        StompHeaderAccessor accessor = subscribeAccessor("/sub/**", 7L);

        assertThatThrownBy(() -> interceptor().preSend(message(accessor), channel))
                .isInstanceOf(StompAuthorizationException.class);
    }

    @Test
    @DisplayName("인증되지 않은 세션(Principal 없음)의 방 토픽 구독은 인증 예외로 거부된다")
    void subscribe_withoutPrincipal_throwsAuthentication() {
        StompHeaderAccessor accessor = subscribeAccessor("/sub/chat-rooms/10", null);

        assertThatThrownBy(() -> interceptor().preSend(message(accessor), channel))
                .isInstanceOf(StompAuthenticationException.class);
    }

    @Test
    @DisplayName("/queue 원본 목적지 직접 구독은 거부된다 — 브로커 /queue 등록(#168) 후에도 타인 개인 큐 도청 불가")
    void subscribe_toRawQueueDestination_throws() {
        StompHeaderAccessor accessor = subscribeAccessor("/queue/errors-userabc123", 7L);

        assertThatThrownBy(() -> interceptor().preSend(message(accessor), channel))
                .isInstanceOf(StompAuthorizationException.class);
    }
}
