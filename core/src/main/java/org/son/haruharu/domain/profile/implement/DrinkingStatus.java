package org.son.haruharu.domain.profile.implement;

public enum DrinkingStatus {
    NONE("안 마심"),
    SOMETIMES("가끔"),
    OFTEN("자주");

    private final String displayName;

    DrinkingStatus(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
