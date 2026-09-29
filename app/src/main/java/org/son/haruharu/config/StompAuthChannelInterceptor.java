package org.son.haruharu.config;

import org.son.haruharu.domain.chat.implement.ChatRoomMemberReader;
import org.son.haruharu.utils.JwtProvider;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * STOMP 인증·인가 인터셉터.
 * - CONNECT: JWT를 검증해 세션 주체(StompPrincipal)를 설정한다. WebSocket 메시지는 서블릿 필터 체인을
 *   타지 않으므로 HTTP용 JwtAuthenticationFilter와 별개로 여기서 인증한다.
 * - SUBSCRIBE: 채팅방 토픽(/sub/chat-rooms/{id}) 구독 시 요청자가 그 방의 참여자인지 검증한다(엿듣기 차단).
 * 인증 실패는 StompAuthenticationException, 구독 인가 실패는 StompAuthorizationException으로 구분해
 * 프레임을 거부한다(#168 — 클라이언트가 토큰 문제와 권한 문제를 구별할 수 있게).
 */
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final Pattern CHAT_ROOM_TOPIC = Pattern.compile("/sub/chat-rooms/(\\d+)(/events)?");

    private final JwtProvider jwtProvider;
    private final ChatRoomMemberReader chatRoomMemberReader;

    public StompAuthChannelInterceptor(JwtProvider jwtProvider, ChatRoomMemberReader chatRoomMemberReader) {
        this.jwtProvider = jwtProvider;
        this.chatRoomMemberReader = chatRoomMemberReader;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            accessor.setUser(authenticate(accessor));
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            authorizeSubscription(accessor);
        }
        return message;
    }

    private StompPrincipal authenticate(StompHeaderAccessor accessor) {
        String token = extractToken(accessor.getFirstNativeHeader("Authorization"));
        if (token == null || !jwtProvider.isValid(token) || !jwtProvider.isAccessToken(token)) {
            throw new StompAuthenticationException();
        }
        try {
            return new StompPrincipal(jwtProvider.extractUserId(token));
        } catch (NumberFormatException e) {
            // subject가 Long이 아님(예: 마이그레이션 이전 발급된 구 UUID subject 토큰) → 인증 거부.
            throw new StompAuthenticationException();
        }
    }

    /**
     * 구독 인가(default-deny). 개인 목적지(/user/**)는 Spring이 세션 단위로 스코핑하므로 통과.
     * 채팅방 토픽은 정확히 /sub/chat-rooms/{숫자} 또는 그 이벤트 경로 /sub/chat-rooms/{숫자}/events(#456)이면서
     * 참여자일 때만 허용하고,
     * 그 외 모든 destination(와일드카드 /sub/chat-rooms/*, /sub/** 등)은 거부한다.
     * (default-allow면 SimpleBroker의 AntPathMatcher 와일드카드 구독으로 타 방 메시지를 도청할 수 있다.)
     */
    private void authorizeSubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) {
            throw new StompAuthorizationException();
        }
        if (destination.startsWith("/user/")) {
            return;
        }
        Matcher matcher = CHAT_ROOM_TOPIC.matcher(destination);
        if (!matcher.matches()) {
            throw new StompAuthorizationException();
        }
        Long chatRoomId = parseChatRoomId(matcher.group(1));
        Long userId = currentUserId(accessor);
        boolean member = chatRoomMemberReader.findMembership(chatRoomId, userId)
                .filter(member2 -> !member2.hasLeft())
                .isPresent();
        if (!member) {
            throw new StompAuthorizationException();
        }
    }

    private Long parseChatRoomId(String raw) {
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            // (\d+)가 Long 범위를 넘는 경우 등 → 거부.
            throw new StompAuthorizationException();
        }
    }

    private Long currentUserId(StompHeaderAccessor accessor) {
        if (accessor.getUser() instanceof StompPrincipal principal) {
            return principal.userId();
        }
        throw new StompAuthenticationException();
    }

    private String extractToken(String header) {
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
