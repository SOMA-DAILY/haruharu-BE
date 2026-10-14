package org.son.haruharu.admin.domain.user.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.son.haruharu.admin.domain.user.implement.UserRoomHistory.Outcome;
import org.son.haruharu.domain.room.implement.RoomStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 유저 방 참여 이력 한 건(#508). 최종 선택·활동이 담겨 방 선택·활동 조회와 같은 권한(SUPER·OPERATOR)으로만 준다. */
public record AdminUserRoomHistoryResponse(
        @Schema(description = "방 id", example = "62") Long roomId,
        @Schema(description = "방 상태(시각 파생)", example = "ENDED") RoomStatus roomStatus,
        @Schema(description = "예정일(구 모델 방 호환)", example = "2026-10-01") LocalDate scheduledDate,
        @Schema(description = "진행 일수", example = "3") int totalDays,
        @Schema(description = "시작 시각(구 모델 방은 실제 시작 시각, 없으면 null)", nullable = true) LocalDateTime startsAt,
        @Schema(description = "종료 시각(구 모델 방은 실제 종료 시각, 없으면 null)", nullable = true) LocalDateTime endsAt,
        @Schema(description = "입장 시각") LocalDateTime joinedAt,
        @Schema(description = "방 안의 가명(없으면 null)", example = "하늘", nullable = true) String pseudonym,
        @Schema(description = "방 입장 성별(MALE·FEMALE, 미부여면 null)", example = "FEMALE", nullable = true) String gender,
        @Schema(description = "최종 선택 제출 여부(기권 포함)") boolean selectionSubmitted,
        @Schema(description = "내가 고른 상대(미제출·기권이면 null)", nullable = true) Person selected,
        @Schema(description = "나를 고른 같은 방 멤버 수", example = "2") int receivedCount,
        @Schema(description = "결과. 선택이 열리기 전·취소 방에서 낸 게 없으면 null",
                example = "MATCHED", nullable = true) Outcome outcome,
        @Schema(description = "올린 영상 수(삭제 제외)", example = "4") long videoCount,
        @Schema(description = "그룹 채팅에 보낸 메시지 수", example = "12") long chatCount
) {
    public record Person(
            @Schema(description = "유저 id", example = "201") long userId,
            @Schema(description = "방 안의 가명", example = "바다") String name
    ) {}
}
