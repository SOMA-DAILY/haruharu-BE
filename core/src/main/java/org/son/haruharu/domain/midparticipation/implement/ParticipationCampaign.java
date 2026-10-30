package org.son.haruharu.domain.midparticipation.implement;
import java.time.LocalDateTime;
import org.son.haruharu.domain.midparticipation.implement.exception.ParticipationUnavailableException;

public record ParticipationCampaign(Long id, Long roomId, int seats, int acceptedCount,
        LocalDateTime expiresAt, LocalDateTime cancelledAt, LocalDateTime createdAt) {
    public boolean openAt(LocalDateTime now) {
        return cancelledAt == null && acceptedCount < seats && now.isBefore(expiresAt);
    }
    public ParticipationCampaign accept(LocalDateTime now) {
        if (!openAt(now)) throw new ParticipationUnavailableException();
        return new ParticipationCampaign(id, roomId, seats, acceptedCount + 1, expiresAt, cancelledAt, createdAt);
    }
    public ParticipationCampaign cancel(LocalDateTime now) {
        return new ParticipationCampaign(id, roomId, seats, acceptedCount, expiresAt, now, createdAt);
    }
}
