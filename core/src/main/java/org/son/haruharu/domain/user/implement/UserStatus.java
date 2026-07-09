package org.son.haruharu.domain.user.implement;

public enum UserStatus {
    ONBOARDING, ACTIVE, INACTIVE, BANNED, DELETED;

    /** 로그인/토큰갱신을 허용하는 상태. 온보딩 중인 신규 유저도 온보딩을 진행하려면 로그인할 수 있어야 한다. */
    public boolean canAuthenticate() {
        return this == ONBOARDING || this == ACTIVE;
    }
}
