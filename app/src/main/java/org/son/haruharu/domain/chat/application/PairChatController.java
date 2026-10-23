package org.son.haruharu.domain.chat.application;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.chat.application.dto.OpenPairChatRequest;
import org.son.haruharu.domain.chat.business.PairChatService;
import org.son.haruharu.domain.chat.business.dto.PairChatRoomResponse;
import org.son.haruharu.domain.chat.business.dto.PairChatWindowResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "1:1 대화 시간", description = "운영자가 정한 시간에만 열리는 같은 방 참가자끼리의 1:1 대화(#540)")
@RestController
@RequestMapping("/api/v1/rooms/{roomId}")
public class PairChatController {

    private final PairChatService pairChatService;

    public PairChatController(PairChatService pairChatService) {
        this.pairChatService = pairChatService;
    }

    @Operation(summary = "1:1 대화 시간 상태",
            description = "OPEN=지금 열림(이번 시간의 시작·끝), SCHEDULED=다음 시간 있음(그 시작·끝·일차), NONE=남은 시간 없음. 방 참가자만.")
    @GetMapping("/pair-chat-window")
    public ApiResponse<PairChatWindowResponse> getWindow(@Parameter(description = "소개팅방 id") @PathVariable Long roomId,
                                                         Authentication authentication) {
        return ApiResponse.ok(pairChatService.getWindow(roomId, (Long) authentication.getPrincipal()));
    }

    @Operation(summary = "1:1 대화방 열기",
            description = "같은 방 참가자와의 1:1(PAIR) 채팅방을 연다. 같은 두 사람이면 기존 방을 돌려준다. 대화 시간 중에만 가능"
                    + "(아니면 409 PAIR_CHAT_NOT_OPEN). 나 자신·다른 방 멤버는 400 PAIR_CHAT_INVALID_TARGET, 차단 관계는 403.")
    @PostMapping("/pair-chats")
    public ApiResponse<PairChatRoomResponse> open(@Parameter(description = "소개팅방 id") @PathVariable Long roomId,
                                                  @Valid @RequestBody OpenPairChatRequest request,
                                                  Authentication authentication) {
        return ApiResponse.ok(pairChatService.open(roomId, (Long) authentication.getPrincipal(), request.targetRoomMemberId()));
    }
}
