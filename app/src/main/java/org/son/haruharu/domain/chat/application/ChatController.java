package org.son.haruharu.domain.chat.application;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.chat.application.dto.MarkReadRequest;
import org.son.haruharu.domain.chat.application.dto.MessageReactionRequest;
import org.son.haruharu.domain.chat.business.ChatMessageReactionService;
import org.son.haruharu.domain.chat.business.ChatPartnerService;
import org.son.haruharu.domain.chat.business.ChatService;
import org.son.haruharu.domain.chat.business.dto.ChatRoomDetailResponse;
import org.son.haruharu.domain.chat.business.dto.ChatRoomsResponse;
import org.son.haruharu.domain.chat.business.dto.GroupChatRoomResponse;
import org.son.haruharu.domain.chat.business.dto.MessagePageResponse;
import org.son.haruharu.domain.chat.business.dto.MessageReactionResponse;
import org.son.haruharu.domain.chat.business.dto.MessageReactorResponse;
import org.son.haruharu.domain.chat.business.dto.PartnerProfileResponse;
import org.son.haruharu.domain.chat.business.dto.ReadResponse;
import org.son.haruharu.domain.mission.business.dto.MissionImageResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "채팅", description = "채팅방·메시지 조회 및 상태 변경 (실시간 송수신은 STOMP — 별도 문서 참고)")
@RestController
@RequestMapping("/api/v1/chat-rooms")
public class ChatController {

    private final ChatService chatService;
    private final ChatPartnerService chatPartnerService;
    private final ChatMessageReactionService chatMessageReactionService;

    public ChatController(ChatService chatService, ChatPartnerService chatPartnerService,
                          ChatMessageReactionService chatMessageReactionService) {
        this.chatService = chatService;
        this.chatPartnerService = chatPartnerService;
        this.chatMessageReactionService = chatMessageReactionService;
    }

    @Operation(summary = "내 채팅방 목록 조회",
            description = "매칭으로 생성된 내 채팅방 목록. 방마다 상대 정보·마지막 메시지·안 읽은 개수 포함. 마지막 메시지 최신순 정렬.")
    @GetMapping
    public ApiResponse<ChatRoomsResponse> getMyRooms(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(chatService.getMyRooms(userId));
    }

    @Operation(summary = "방 ID로 GROUP 채팅방 조회",
            description = "소개팅방에 연결된 GROUP 채팅방 id를 반환한다. 활성 채팅 멤버가 아니면 "
                    + "403(CHAT_NOT_MEMBER), 아직 생성되지 않았으면 404(CHAT_ROOM_NOT_FOUND).")
    @GetMapping("/group/by-room/{roomId}")
    public ApiResponse<GroupChatRoomResponse> getGroupRoomBySourceRoom(
            @Parameter(description = "소개팅방 id", example = "1") @PathVariable Long roomId,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(chatService.getGroupRoomBySourceRoom(roomId, userId));
    }

    @Operation(summary = "채팅방 상세 조회",
            description = "특정 채팅방 상세(상대 정보·생성일). 참여자가 아니면 403(CHAT_NOT_MEMBER), 없는 방이면 404(CHAT_ROOM_NOT_FOUND).")
    @GetMapping("/{chatRoomId}")
    public ApiResponse<ChatRoomDetailResponse> getRoom(@Parameter(description = "채팅방 id") @PathVariable Long chatRoomId,
                                                       Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(chatService.getRoom(chatRoomId, userId));
    }

    @Operation(summary = "상대 프로필 조회",
            description = "매칭 상대의 공개 프로필(전 항목 공개 + 1~3일차 영상 day별). 참여자만 조회 가능.")
    @GetMapping("/{chatRoomId}/partner")
    public ApiResponse<PartnerProfileResponse> getPartner(@Parameter(description = "채팅방 id") @PathVariable Long chatRoomId,
                                                          Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(chatPartnerService.getPartner(chatRoomId, userId));
    }

    @Operation(summary = "메시지 목록 조회",
            description = "과거 메시지를 최신순 커서 페이지네이션으로 반환한다. 삭제된 메시지는 제외.")
    @GetMapping("/{chatRoomId}/messages")
    public ApiResponse<MessagePageResponse> getMessages(@Parameter(description = "채팅방 id") @PathVariable Long chatRoomId,
                                                        @Parameter(description = "이전 페이지 마지막 메시지 id(커서). 첫 페이지는 생략", example = "1024")
                                                        @RequestParam(required = false) Long cursor,
                                                        @Parameter(description = "페이지 크기(기본 30, 최대 100)", example = "30")
                                                        @RequestParam(defaultValue = "30") int size,
                                                        Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(chatService.getMessages(chatRoomId, userId, cursor, size));
    }

    @Operation(summary = "메시지 반응 토글",
            description = "없으면 추가, 같은 종류면 취소, 다른 종류면 교체(한 사람당 메시지 1개). 내 메시지는 "
                    + "403(CHAT_REACTION_OWN_MESSAGE), 채팅방 멤버가 아니면 403(CHAT_NOT_MEMBER), 다른 방 메시지면 "
                    + "404(CHAT_MESSAGE_NOT_FOUND). 바뀐 개수는 /sub/chat-rooms/{id}/events 로 REACTION_UPDATED. 푸시 없음.")
    @PutMapping("/{chatRoomId}/messages/{messageId}/reaction")
    public ApiResponse<MessageReactionResponse> toggleReaction(
            @Parameter(description = "채팅방 id") @PathVariable Long chatRoomId,
            @Parameter(description = "메시지 id") @PathVariable Long messageId,
            @Valid @RequestBody MessageReactionRequest request,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(chatMessageReactionService.toggle(userId, chatRoomId, messageId, request.type()));
    }

    @Operation(summary = "메시지에 반응한 사람",
            description = "메시지 주인만 조회할 수 있다(아니면 403 CHAT_REACTION_OWNER_ONLY). 이름은 GROUP 가명 / DIRECT 실명.")
    @GetMapping("/{chatRoomId}/messages/{messageId}/reactions")
    public ApiResponse<List<MessageReactorResponse>> getReactors(
            @Parameter(description = "채팅방 id") @PathVariable Long chatRoomId,
            @Parameter(description = "메시지 id") @PathVariable Long messageId,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(chatMessageReactionService.getReactors(userId, chatRoomId, messageId));
    }

    @Operation(summary = "운영자 미션 카드 참고 사진",
            description = "미션 카드 메시지의 사진 서명 URL 을 새로 발급한다(#474). 채팅방 멤버가 아니면 403(CHAT_NOT_MEMBER), "
                    + "카드가 아니거나 사진이 없거나 운영자가 지웠으면 404(CHAT_MISSION_IMAGE_NOT_FOUND).")
    @GetMapping("/{chatRoomId}/messages/{messageId}/mission-image")
    public ApiResponse<MissionImageResponse> getMissionImage(
            @Parameter(description = "채팅방 id") @PathVariable Long chatRoomId,
            @Parameter(description = "메시지 id") @PathVariable Long messageId,
            Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(chatService.getMissionImage(chatRoomId, userId, messageId));
    }

    @Operation(summary = "메시지 읽음 처리",
            description = "읽음 커서(last_read_message_id)를 갱신한다. 커서는 뒤로만 이동(역행 방지). 갱신 후 안 읽은 개수를 반환.")
    @PatchMapping("/{chatRoomId}/read")
    public ApiResponse<ReadResponse> markRead(@Parameter(description = "채팅방 id") @PathVariable Long chatRoomId,
                                              @Valid @RequestBody MarkReadRequest request,
                                              Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(chatService.markRead(chatRoomId, userId, request.lastReadMessageId()));
    }

    @Operation(summary = "채팅방 나가기",
            description = "DIRECT는 한 명이 나가면 양쪽 참여 종료와 방 소프트 삭제를 처리한다. 기존 참여자의 재요청은 성공한다. GROUP은 요청자만 퇴장한다.")
    @DeleteMapping("/{chatRoomId}/members/me")
    public ApiResponse<Void> leaveRoom(@Parameter(description = "채팅방 id") @PathVariable Long chatRoomId, Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        chatService.leaveRoom(chatRoomId, userId);
        return ApiResponse.ok(null);
    }
}
