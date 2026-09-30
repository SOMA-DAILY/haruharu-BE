package org.son.haruharu.domain.video.application.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record VideoUploadAssetPresignRequest(
        @Schema(description = "업로드할 파일의 Content-Type. 생략하면 viewer·feed는 video/mp4, thumbnail은 image/jpeg. "
                + "viewer만 video/quicktime을 추가로 허용한다", nullable = true)
        String contentType
) {}
