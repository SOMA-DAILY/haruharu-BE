package org.son.haruharu.domain.user.business.exception;

import org.son.haruharu.common.exception.BadRequestException;

public class OnboardingIncompleteException extends BadRequestException {
    public OnboardingIncompleteException() {
        super("ONBOARDING_INCOMPLETE", "온보딩에 필요한 프로필·가치관·이미지가 모두 완료되지 않았습니다.");
    }
}
