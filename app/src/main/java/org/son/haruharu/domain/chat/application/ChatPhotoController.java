package org.son.haruharu.domain.chat.application;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.chat.business.ChatPhotoService;
import org.son.haruharu.domain.chat.business.dto.ChatMessageBroadcast;
import org.son.haruharu.domain.chat.business.dto.ChatPhotoView;
import org.son.haruharu.domain.chat.implement.exception.InvalidChatPhotoException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/chat-rooms/{chatRoomId}")
public class ChatPhotoController {
    private final ChatPhotoService service;

    @Operation(summary = "사진 메시지 전송", description = "한 장씩 최대 10MB JPG·PNG. request_id는 사진별 UUID이며 재시도에도 같은 값을 보낸다.")
    @PostMapping(value = "/photo-messages", consumes = "multipart/form-data")
    public ApiResponse<ChatMessageBroadcast> send(@PathVariable Long chatRoomId,
            @RequestParam("request_id") String requestId, @RequestPart("file") MultipartFile file,
            Authentication authentication) {
        if (file.isEmpty() || file.getSize() > 10 * 1024 * 1024) throw new InvalidChatPhotoException();
        try {
            return ApiResponse.ok(service.send(chatRoomId, (Long) authentication.getPrincipal(), requestId,
                    file.getBytes(), file.getContentType()));
        } catch (IOException invalid) {
            throw new InvalidChatPhotoException();
        }
    }

    @Operation(summary = "사진 다시 불러오기", description = "현재 참여·열람 권한을 다시 확인하고 만료된 조회 주소를 갱신한다.")
    @GetMapping("/messages/{messageId}/photo")
    public ApiResponse<ChatPhotoView> get(@PathVariable Long chatRoomId, @PathVariable Long messageId,
            Authentication authentication) {
        return ApiResponse.ok(service.get(chatRoomId, messageId, (Long) authentication.getPrincipal()));
    }
}
