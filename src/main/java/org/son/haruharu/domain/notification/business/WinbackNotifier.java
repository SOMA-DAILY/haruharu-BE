package org.son.haruharu.domain.notification.business;

import org.son.haruharu.domain.auth.implement.RefreshTokenReader;
import org.son.haruharu.domain.notification.implement.NotificationType;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 잠수 유저 복귀 유도. 마지막 접속(refresh token 발급)이 "정확히 3일 전 하루 구간"인 유저에게만 발송한다.
 * 이 window 방식이 재발송 방지의 핵심 — 4일째부터는 구간을 벗어나 자동으로 다시 보내지 않고,
 * 재접속하면 발급 시각이 갱신되어 카운트가 리셋된다(별도 발송 이력 테이블 불필요).
 * @Transactional 없음 — 발송 IO를 트랜잭션에 묶지 않기 위함.
 */
@Service
public class WinbackNotifier {

    private static final int INACTIVE_DAYS = 3;

    private final RefreshTokenReader refreshTokenReader;
    private final PushSendService pushSendService;

    public WinbackNotifier(RefreshTokenReader refreshTokenReader, PushSendService pushSendService) {
        this.refreshTokenReader = refreshTokenReader;
        this.pushSendService = pushSendService;
    }

    /** 반환값은 발송 대상 수(모니터링 로그용). */
    public int notifyInactiveUsers(LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        LocalDateTime from = today.minusDays(INACTIVE_DAYS).atStartOfDay();
        LocalDateTime to = today.minusDays(INACTIVE_DAYS - 1L).atStartOfDay();
        List<Long> targetUserIds = refreshTokenReader.findInactiveUserIds(from, to);
        if (targetUserIds.isEmpty()) {
            return 0;
        }
        pushSendService.sendToUsers(targetUserIds, NotificationType.WINBACK, Map.of());
        return targetUserIds.size();
    }
}
