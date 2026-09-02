package org.son.haruharu.domain.user.business.exception;

import org.son.haruharu.common.exception.BadRequestException;

public class OnboardingIncompleteException extends BadRequestException {
    public OnboardingIncompleteException() {
        super("ONBOARDING_INCOMPLETE", "온보딩에 필요한 닉네임이 등록되지 않았습니다.");
    }
}
