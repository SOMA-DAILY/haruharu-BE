package org.son.haruharu.domain.payment.implement;

/** 인앱결제 스토어. RevenueCat의 store 필드와 매핑된다. */
public enum IapStore {
    APP_STORE,
    PLAY_STORE;

    /** RevenueCat store 문자열을 매핑한다. 우리가 처리하지 않는 스토어면 null. */
    public static IapStore from(String value) {
        if (value == null) {
            return null;
        }
        for (IapStore store : values()) {
            if (store.name().equals(value)) {
                return store;
            }
        }
        return null;
    }
}
