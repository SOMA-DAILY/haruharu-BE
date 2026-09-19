package org.son.haruharu.domain.chat.business.dto;

import org.son.haruharu.domain.chat.implement.VideoReplyState;

public record VideoReplyPreview(
        Long videoPostId,
        VideoReplyState state,
        String videoUrl,
        String thumbnailUrl,
        String caption,
        String nickname) {

    public static VideoReplyPreview unavailable(Long videoPostId, VideoReplyState state) {
        return new VideoReplyPreview(videoPostId, state, null, null, null, null);
    }
}
