package org.son.haruharu.admin.domain.room.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.son.haruharu.domain.room.implement.RoomStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 방 상세 + 참여자 목록. */
public record AdminRoomDetailResponse(
        @Schema(description = "방 id", example = "1") Long roomId,
        @Schema(description = "방 상태", example = "ACTIVE") RoomStatus status,
        @Schema(description = "예정일", example = "2026-07-09") LocalDate scheduledDate,
        @Schema(description = "총 일수", example = "3") int totalDays,
        @Schema(description = "현재 일차(활성 방만, 그 외 0)", example = "2") int currentDay,
        @Schema(description = "시작 시각(미시작이면 null)", nullable = true) LocalDateTime startedAt,
        @Schema(description = "종료 시각(미종료면 null)", nullable = true) LocalDateTime endedAt,
        @Schema(description = "참여자 수", example = "6") int memberCount,
        @Schema(description = "여성 수", example = "3") int femaleCount,
        @Schema(description = "남성 수", example = "3") int maleCount,
        @Schema(description = "참여자 목록") List<MemberView> members
) {
    public record MemberView(
            @Schema(description = "방 멤버 id", example = "10") Long memberId,
            @Schema(description = "유저 id", example = "901") Long userId,
            @Schema(description = "성별", example = "FEMALE") String gender,
            @Schema(description = "입장 시각") LocalDateTime joinedAt,
            @Schema(description = "닉네임(실명, 프로필 없으면 null)", example = "여울", nullable = true) String nickname,
            @Schema(description = "방 세션 가명(미배정 시 null, #393)", example = "유진", nullable = true) String pseudonym,
            @Schema(description = "나이(생년월일 없으면 null)", example = "24", nullable = true) Integer age,
            @Schema(description = "지역(프로필 없으면 null)", example = "서울", nullable = true) String region
    ) {}
}
