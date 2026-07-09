package org.son.haruharu.domain.profile.implement;

import org.son.haruharu.common.enums.LabeledEnum;

public enum DrinkingStatus implements LabeledEnum {
    NONE("안 마심"),
    SOMETIMES("가끔"),
    OFTEN("자주");

    private final String displayName;

    DrinkingStatus(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String displayName() {
        return displayName;
    }
}
