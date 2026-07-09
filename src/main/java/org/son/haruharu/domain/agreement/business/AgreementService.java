package org.son.haruharu.domain.agreement.business;

import org.son.haruharu.domain.agreement.business.command.SaveAgreementsCommand;
import org.son.haruharu.domain.agreement.business.dto.MarketingConsentResponse;
import org.son.haruharu.domain.agreement.business.exception.RequiredAgreementMissingException;
import org.son.haruharu.domain.agreement.implement.AgreementType;
import org.son.haruharu.domain.agreement.implement.UserAgreement;
import org.son.haruharu.domain.agreement.implement.UserAgreementReader;
import org.son.haruharu.domain.agreement.implement.UserAgreementWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class AgreementService {

    private final UserAgreementReader userAgreementReader;
    private final UserAgreementWriter userAgreementWriter;
    private final Clock clock;

    public AgreementService(
        UserAgreementReader userAgreementReader,
        UserAgreementWriter userAgreementWriter,
        Clock clock
    ) {
        this.userAgreementReader = userAgreementReader;
        this.userAgreementWriter = userAgreementWriter;
        this.clock = clock;
    }

    @Transactional
    public void saveAgreements(Long userId, SaveAgreementsCommand command) {
        // 동의한 약관 종류 집합. 필수 약관은 모두 agreed=true여야 온보딩을 진행할 수 있다(법적 유효성).
        Set<AgreementType> agreedTypes = EnumSet.noneOf(AgreementType.class);
        command.agreements().stream()
            .filter(SaveAgreementsCommand.Item::agreed)
            .forEach(item -> agreedTypes.add(item.type()));
        for (AgreementType type : AgreementType.values()) {
            if (type.isRequired() && !agreedTypes.contains(type)) {
                throw new RequiredAgreementMissingException();
            }
        }

        // 동의 이력은 append-only로 보관한다(동의 시각·버전 감사). 재동의 시 새 행을 남긴다.
        LocalDateTime now = LocalDateTime.now(clock);
        for (SaveAgreementsCommand.Item item : command.agreements()) {
            userAgreementWriter.save(new UserAgreement(null, userId, item.type(), item.version(), item.agreed(), now));
        }
    }

    public MarketingConsentResponse getMarketingConsent(Long userId) {
        return MarketingConsentResponse.from(userAgreementReader.findLatestByType(userId, AgreementType.MARKETING));
    }
}
