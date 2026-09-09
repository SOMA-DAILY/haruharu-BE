package org.son.haruharu.domain.chat.application;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.son.haruharu.common.exception.BusinessException;
import org.son.haruharu.config.StompPrincipal;
import org.son.haruharu.domain.chat.application.dto.SendMessageRequest;
import org.son.haruharu.domain.chat.business.ChatService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * STOMP 메시지 진입점.
 * 발행: /pub/chat-rooms/{chatRoomId}/messages → 저장 후 /sub/chat-rooms/{chatRoomId}로 브로드캐스트.
 * senderId는 요청 body가 아니라 CONNECT 시 설정된 세션 principal(StompPrincipal)에서 확정한다(위조 방지).
 */
@Slf4j
@Controller
public class ChatStompController {

    private final ChatService chatService;

    public ChatStompController(ChatService chatService) {
        this.chatService = chatService;
    }

    @MessageMapping("/chat-rooms/{chatRoomId}/messages")
    public void send(@DestinationVariable Long chatRoomId,
                     @Payload @Valid SendMessageRequest request,
                     Principal principal) {
        Long senderId = ((StompPrincipal) principal).userId();
        // 커밋 후 리스너가 종료 상태를 확인한 뒤 브로드캐스트한다.
        chatService.sendMessage(chatRoomId, senderId, request.content());
    }

    /** 참여자 아님·방 없음 등 비즈니스 예외를 발신자 개인 큐(/user/queue/errors)로 알린다. */
    @MessageExceptionHandler(BusinessException.class)
    @SendToUser("/queue/errors")
    public ErrorPayload handleBusinessException(BusinessException e) {
        return new ErrorPayload(e.getCode(), e.getMessage());
    }

    /**
     * 그 외 예상치 못한 예외(유효성 위반·DB 오류 등) catch-all.
     * 내부 예외 메시지를 그대로 노출하지 않고 일반화된 응답만 개인 큐로 보낸다(세션 강제종료·정보노출 방지).
     */
    @MessageExceptionHandler(Exception.class)
    @SendToUser("/queue/errors")
    public ErrorPayload handleUnexpected(Exception e) {
        log.error("STOMP 메시지 처리 실패", e);
        return new ErrorPayload("CHAT_MESSAGE_FAILED", "메시지 처리에 실패했습니다.");
    }

    public record ErrorPayload(String code, String message) {}
}
