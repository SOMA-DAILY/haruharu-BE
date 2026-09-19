package org.son.haruharu.domain.chat.business.dto;

public record VideoReplyPreview(
        Long videoPostId,
        String state,
        String videoUrl,
        String thumbnailUrl,
        String caption,
        String nickname) {}
