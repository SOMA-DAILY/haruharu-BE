package org.son.haruharu.domain.chat.business.dto;

import java.time.LocalDateTime;

public record ChatRoomDetailResponse(
        Long chatRoomId,
        PartnerBrief partner,
        LocalDateTime createdAt
) {}
