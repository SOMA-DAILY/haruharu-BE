package org.son.haruharu.domain.agreement.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.agreement.business.command.SaveAgreementsCommand;
import org.son.haruharu.domain.agreement.business.dto.MarketingConsentResponse;
import org.son.haruharu.domain.agreement.business.exception.RequiredAgreementMissingException;
import org.son.haruharu.domain.agreement.implement.AgreementType;
import org.son.haruharu.domain.agreement.implement.UserAgreement;
import org.son.haruharu.domain.agreement.implement.UserAgreementReader;
import org.son.haruharu.domain.agreement.implement.UserAgreementWriter;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class AgreementServiceTest {

    @Mock UserAgreementReader userAgreementReader;
    @Mock UserAgreementWriter userAgreementWriter;

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final Clock clock = Clock.fixed(
            ZonedDateTime.of(2026, 7, 7, 12, 0, 0, 0, SEOUL).toInstant(), SEOUL);

    AgreementService agreementService;

    @BeforeEach
    void setUp() {
        agreementService = new AgreementService(userAgreementReader, userAgreementWriter, clock);
    }

    private static SaveAgreementsCommand.Item item(AgreementType type, boolean agreed) {
        return new SaveAgreementsCommand.Item(type, "2026-07-05", agreed);
    }

    @Test
    @DisplayName("필수 약관 전부 동의 + 마케팅 동의 시 모든 약관이 이력으로 저장된다")
    void givenAllRequiredAgreed_whenSave_thenPersistsAll() {
        SaveAgreementsCommand command = new SaveAgreementsCommand(List.of(
            item(AgreementType.SERVICE_TERMS, true),
            item(AgreementType.PRIVACY, true),
            item(AgreementType.AGE_19, true),
            item(AgreementType.MARKETING, true)
        ));

        agreementService.saveAgreements(1L, command);

        then(userAgreementWriter).should(times(4)).save(any());
    }

    @Test
    @DisplayName("필수 약관이 누락되면 REQUIRED_AGREEMENT_MISSING 예외가 발생하고 저장되지 않는다")
    void givenMissingRequired_whenSave_thenThrows() {
        SaveAgreementsCommand command = new SaveAgreementsCommand(List.of(
            item(AgreementType.SERVICE_TERMS, true),
            item(AgreementType.PRIVACY, true)
            // AGE_19 누락
        ));

        assertThatThrownBy(() -> agreementService.saveAgreements(1L, command))
            .isInstanceOf(RequiredAgreementMissingException.class);
        then(userAgreementWriter).should(never()).save(any());
    }

    @Test
    @DisplayName("필수 약관을 agreed=false로 보내면 예외가 발생한다")
    void givenRequiredNotAgreed_whenSave_thenThrows() {
        SaveAgreementsCommand command = new SaveAgreementsCommand(List.of(
            item(AgreementType.SERVICE_TERMS, true),
            item(AgreementType.PRIVACY, true),
            item(AgreementType.AGE_19, false)
        ));

        assertThatThrownBy(() -> agreementService.saveAgreements(1L, command))
            .isInstanceOf(RequiredAgreementMissingException.class);
        then(userAgreementWriter).should(never()).save(any());
    }

    @Test
    @DisplayName("마케팅 최신 동의가 있으면 그 값을, 없으면 false를 반환한다")
    void givenMarketingConsent_whenGet_thenReturnsLatest() {
        given(userAgreementReader.findLatestByType(1L, AgreementType.MARKETING))
            .willReturn(Optional.of(new UserAgreement(9L, 1L, AgreementType.MARKETING, "2026-07-05", true, LocalDateTime.now(clock))));
        given(userAgreementReader.findLatestByType(2L, AgreementType.MARKETING))
            .willReturn(Optional.empty());

        MarketingConsentResponse consented = agreementService.getMarketingConsent(1L);
        MarketingConsentResponse none = agreementService.getMarketingConsent(2L);

        assertThat(consented.agreed()).isTrue();
        assertThat(consented.version()).isEqualTo("2026-07-05");
        assertThat(none.agreed()).isFalse();
        assertThat(none.version()).isNull();
    }
}
