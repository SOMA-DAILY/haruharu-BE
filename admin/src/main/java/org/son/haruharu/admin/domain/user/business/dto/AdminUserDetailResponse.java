package org.son.haruharu.admin.domain.user.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.son.haruharu.domain.payment.implement.IapStore;
import org.son.haruharu.domain.payment.implement.IapPurchaseStatus;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.domain.user.implement.UserStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 유저 상세 — 프로필·티켓 잔액·결제이력·참여이력. 본인인증(identity)·제재는 별도 이슈. */
public record AdminUserDetailResponse(
        @Schema(description = "유저 id", example = "901") Long userId,
        @Schema(description = "상태", example = "ACTIVE") UserStatus status,
        @Schema(description = "가입일") LocalDateTime createdAt,
        @Schema(description = "프로필(없으면 null)", nullable = true) ProfileInfo profile,
        @Schema(description = "티켓 잔액") TicketInfo ticket,
        @Schema(description = "결제 이력(최신순)") List<PaymentInfo> payments,
        @Schema(description = "방 참여 이력(최신순)") List<ParticipationInfo> participations
) {
    public record ProfileInfo(
            @Schema(description = "닉네임", example = "여울") String nickname,
            @Schema(description = "지역", example = "서울") String region,
            @Schema(description = "MBTI", example = "INFP") String mbti,
            @Schema(description = "소속", example = "직장인") String affiliation,
            @Schema(description = "키(cm)", example = "168") Integer height
    ) {}

    public record TicketInfo(
            @Schema(description = "잔액", example = "1") long balance
    ) {}

    public record PaymentInfo(
            @Schema(description = "결제 id", example = "1") Long purchaseId,
            @Schema(description = "스토어 상품 id", example = "ticket_1") String storeProductId,
            @Schema(description = "지급 티켓", example = "1") long ticketAmount,
            @Schema(description = "스토어", example = "APP_STORE") IapStore store,
            @Schema(description = "상태", example = "OWNED") IapPurchaseStatus status,
            @Schema(description = "결제 시각") LocalDateTime purchasedAt,
            @Schema(description = "환불 시각(없으면 null)", nullable = true) LocalDateTime refundedAt
    ) {}

    public record ParticipationInfo(
            @Schema(description = "방 id", example = "1") Long roomId,
            @Schema(description = "방 상태", example = "ENDED") RoomStatus roomStatus,
            @Schema(description = "예정일", example = "2026-07-09") LocalDate scheduledDate,
            @Schema(description = "입장 시각") LocalDateTime joinedAt
    ) {}
}
