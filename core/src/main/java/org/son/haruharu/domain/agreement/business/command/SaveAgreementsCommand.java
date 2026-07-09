package org.son.haruharu.domain.agreement.business.command;

import org.son.haruharu.domain.agreement.implement.AgreementType;

import java.util.List;

public record SaveAgreementsCommand(List<Item> agreements) {
    public record Item(AgreementType type, String version, boolean agreed) {}
}
