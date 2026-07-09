package org.son.haruharu.domain.agreement.application.dto;

import org.son.haruharu.domain.agreement.business.command.SaveAgreementsCommand;
import org.son.haruharu.domain.agreement.implement.AgreementType;

import java.util.List;

public record SaveAgreementsRequest(List<Item> agreements) {

    public record Item(AgreementType type, String version, boolean agreed) {}

    public SaveAgreementsCommand toCommand() {
        List<SaveAgreementsCommand.Item> items = agreements.stream()
            .map(i -> new SaveAgreementsCommand.Item(i.type(), i.version(), i.agreed()))
            .toList();
        return new SaveAgreementsCommand(items);
    }
}
