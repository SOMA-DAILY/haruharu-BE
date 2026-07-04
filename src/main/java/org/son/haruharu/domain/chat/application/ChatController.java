package org.son.haruharu.domain.chat.application;

import jakarta.validation.Valid;
import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.chat.application.dto.MarkReadRequest;
import org.son.haruharu.domain.chat.business.ChatService;
import org.son.haruharu.domain.chat.business.dto.ChatRoomDetailResponse;
import org.son.haruharu.domain.chat.business.dto.ChatRoomsResponse;
import org.son.haruharu.domain.chat.business.dto.MessagePageResponse;
import org.son.haruharu.domain.chat.business.dto.PartnerProfileResponse;
import org.son.haruharu.domain.chat.business.dto.ReadResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat-rooms")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping
    public ApiResponse<ChatRoomsResponse> getMyRooms(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(chatService.getMyRooms(userId));
    }

    @GetMapping("/{chatRoomId}")
    public ApiResponse<ChatRoomDetailResponse> getRoom(@PathVariable Long chatRoomId,
                                                       Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(chatService.getRoom(chatRoomId, userId));
    }

    @GetMapping("/{chatRoomId}/partner")
    public ApiResponse<PartnerProfileResponse> getPartner(@PathVariable Long chatRoomId,
                                                          Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(chatService.getPartner(chatRoomId, userId));
    }

    @GetMapping("/{chatRoomId}/messages")
    public ApiResponse<MessagePageResponse> getMessages(@PathVariable Long chatRoomId,
                                                        @RequestParam(required = false) Long cursor,
                                                        @RequestParam(defaultValue = "30") int size,
                                                        Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(chatService.getMessages(chatRoomId, userId, cursor, size));
    }

    @PatchMapping("/{chatRoomId}/read")
    public ApiResponse<ReadResponse> markRead(@PathVariable Long chatRoomId,
                                              @Valid @RequestBody MarkReadRequest request,
                                              Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(chatService.markRead(chatRoomId, userId, request.lastReadMessageId()));
    }

    @DeleteMapping("/{chatRoomId}/members/me")
    public ApiResponse<Void> leaveRoom(@PathVariable Long chatRoomId, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        chatService.leaveRoom(chatRoomId, userId);
        return ApiResponse.ok(null);
    }
}
