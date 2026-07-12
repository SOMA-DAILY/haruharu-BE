package org.son.haruharu.domain.profile.implement;

public enum SmokingStatus {
    NONE("안 피움"),
    SOMETIMES("가끔"),
    OFTEN("자주"),
    E_CIGARETTE("전자담배");

    private final String displayName;

    SmokingStatus(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
