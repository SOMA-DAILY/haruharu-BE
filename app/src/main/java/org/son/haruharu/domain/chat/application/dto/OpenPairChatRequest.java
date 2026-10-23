package org.son.haruharu.domain.chat.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** 1:1 대화방 열기(#540). 상대는 같은 방의 room_member_id 로 지정한다(프로필 시트가 가진 값). */
public record OpenPairChatRequest(
        @Schema(description = "대화할 상대의 방 멤버 id", example = "12") @NotNull @Positive Long targetRoomMemberId
) {}
