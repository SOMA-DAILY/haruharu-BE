package org.son.haruharu.domain.value.business.dto;

public record SavedAnswersResponse(int savedCount) {

    public static SavedAnswersResponse from(int savedCount) {
        return new SavedAnswersResponse(savedCount);
    }
}
