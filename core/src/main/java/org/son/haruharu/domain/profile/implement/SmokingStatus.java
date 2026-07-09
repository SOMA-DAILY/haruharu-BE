package org.son.haruharu.domain.profile.implement;

import org.son.haruharu.common.enums.LabeledEnum;

public enum SmokingStatus implements LabeledEnum {
    NONE("안 피움"),
    SOMETIMES("가끔"),
    OFTEN("자주"),
    E_CIGARETTE("전자담배");

    private final String displayName;

    SmokingStatus(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String displayName() {
        return displayName;
    }
}
