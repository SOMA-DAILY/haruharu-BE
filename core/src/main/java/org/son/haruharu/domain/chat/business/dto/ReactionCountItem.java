package org.son.haruharu.domain.chat.business.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.son.haruharu.domain.chat.implement.MessageReactionCount;
import org.son.haruharu.domain.video.implement.ReactionType;

import java.util.Comparator;
import java.util.List;

/** 메시지 반응 타입별 개수(#456). 많은 순, 같으면 ReactionType 순서. */
public record ReactionCountItem(
        @Schema(description = "반응 종류", example = "LAUGH") String type,
        @Schema(description = "개수", example = "3") long count
) {
    public static List<ReactionCountItem> sorted(List<MessageReactionCount> rows) {
        return rows.stream()
                .map(r -> new ReactionCountItem(r.reactionType(), r.count()))
                .sorted(Comparator.comparingLong(ReactionCountItem::count).reversed()
                        .thenComparingInt(item -> order(item.type())))
                .toList();
    }

    private static int order(String type) {
        ReactionType parsed = ReactionType.parse(type);
        return parsed == null ? Integer.MAX_VALUE : parsed.ordinal();
    }
}
