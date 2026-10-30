package org.son.haruharu.domain.midparticipation.business;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.son.haruharu.domain.midparticipation.implement.ParticipationWriter;
/** 관리 서버에 저장된 안내도 앱 서버가 처리한다. 네트워크 IO 동안 DB 잠금을 유지하지 않는다. */
@Service @RequiredArgsConstructor @Transactional(readOnly=true)
public class ParticipationNotificationClaimService {
    private final ParticipationWriter writer;
    private final Clock clock;
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public boolean claim(Long id,int version) {
        var now=LocalDateTime.now(clock);
        return writer.claimNotification(id,version,now,now.minusMinutes(1));
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void finish(Long id,int version) { writer.finishNotification(id,version); }
}
