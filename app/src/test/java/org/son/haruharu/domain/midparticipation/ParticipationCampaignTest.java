package org.son.haruharu.domain.midparticipation;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.midparticipation.implement.ParticipationCampaign;
import org.son.haruharu.domain.midparticipation.implement.ParticipationInvitation;
import org.son.haruharu.domain.midparticipation.implement.exception.ParticipationUnavailableException;
import static org.assertj.core.api.Assertions.*;

class ParticipationCampaignTest {
    private final LocalDateTime now = LocalDateTime.of(2026, 10, 28, 18, 0);

    @Test void closesExactlyAtDeadline() {
        var campaign = new ParticipationCampaign(1L, 2L, 1, 0, now, null, now.minusHours(1));
        assertThat(campaign.openAt(now.minusNanos(1))).isTrue();
        assertThat(campaign.openAt(now)).isFalse();
    }

    @Test void lastSeatClosesCampaignAndFurtherAcceptanceFails() {
        var campaign = new ParticipationCampaign(1L, 2L, 1, 0, now.plusHours(1), null, now);
        var full = campaign.accept(now);
        assertThat(full.acceptedCount()).isEqualTo(1);
        assertThat(full.openAt(now)).isFalse();
        assertThatThrownBy(() -> full.accept(now)).isInstanceOf(ParticipationUnavailableException.class);
    }

    @Test void resendCannotReviveDeclinedOrAcceptedInvitation() {
        var invitation = ParticipationInvitation.offer(2L, 3L, now);
        assertThat(invitation.resend(now).deliveryVersion()).isEqualTo(2);
        assertThatThrownBy(() -> invitation.decline(now).resend(now))
                .isInstanceOf(ParticipationUnavailableException.class);
        assertThatThrownBy(() -> invitation.accept(4L, now).resend(now))
                .isInstanceOf(ParticipationUnavailableException.class);
    }

    @Test void readingOneDeliveryDoesNotReadFutureReminders() {
        var invitation = ParticipationInvitation.offer(2L, 3L, now).seen(1);
        assertThat(invitation.popupPending()).isFalse();
        assertThat(invitation.resend(now).popupPending()).isTrue();
        assertThat(invitation.resend(now).seen(1).popupPending()).isTrue();
    }
}
