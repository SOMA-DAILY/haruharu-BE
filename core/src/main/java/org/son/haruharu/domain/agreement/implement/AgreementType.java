package org.son.haruharu.domain.agreement.implement;

public enum AgreementType {
    SERVICE_TERMS(true),
    PRIVACY(true),
    AGE_19(true),
    MARKETING(false);

    private final boolean required;

    AgreementType(boolean required) {
        this.required = required;
    }

    public boolean isRequired() {
        return required;
    }
}
