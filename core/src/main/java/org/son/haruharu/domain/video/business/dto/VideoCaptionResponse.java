package org.son.haruharu.domain.video.business.dto;

import org.son.haruharu.domain.video.implement.VideoPost;

public record VideoCaptionResponse(Long videoPostId, String caption) {
    public static VideoCaptionResponse from(VideoPost post) {
        return new VideoCaptionResponse(post.id(), post.caption());
    }
}
