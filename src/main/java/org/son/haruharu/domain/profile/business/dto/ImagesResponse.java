package org.son.haruharu.domain.profile.business.dto;

import java.util.List;

public record ImagesResponse(List<String> imageUrls) {

    public static ImagesResponse from(List<String> imageUrls) {
        return new ImagesResponse(imageUrls);
    }
}
