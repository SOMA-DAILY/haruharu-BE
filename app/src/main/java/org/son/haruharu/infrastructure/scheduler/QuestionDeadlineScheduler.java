package org.son.haruharu.infrastructure.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.son.haruharu.domain.notification.business.QuestionDeadlineNotifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 매분 익명 질문 마감 임박 알림을 스캔한다. 슬롯과 달리 마감(final_selection_opens_at)은 운영진이
 * 방마다 직접 입력해 정시가 아닐 수 있어, 매시가 아니라 매분 돌며 마감 2시간 전인 방만 보낸다.
 *
 * <p>분 단위 크론이라 틱 하나가 늦어지면 어떤 분은 아무도 처리하지 않고 지나갈 수 있다. 이 기능은
 * 방마다 발송 기회가 한 번뿐이고 재시도가 없어서, 마지막으로 처리한 분을 들고 있다가 빠진 분을
 * 채워 넣는다. 같은 분을 두 번 처리하지 않으므로 크론이 경계 직전에 발화해도 중복 발송되지 않는다.
 *
 * <p>한계: 이 진행 상태는 인스턴스 메모리에만 있다. 재시작 구간의 분은 건너뛰고, 여러 인스턴스로
 * 띄우면 인스턴스마다 발송한다(기존 알림 스케줄러들과 같은 수준). 완전한 멱등성은 발송 기록이
 * 필요하며 운영 스키마 변경을 동반한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QuestionDeadlineScheduler {

    /** 긴 정지 뒤 한 번에 밀어내지 않도록 채워 넣는 분 수를 제한한다. */
    static final int MAX_CATCH_UP_MINUTES = 10;

    private final QuestionDeadlineNotifier questionDeadlineNotifier;
    private final Clock clock;

    private volatile LocalDateTime lastProcessed;

    @Scheduled(cron = "0 * * * * *", zone = "Asia/Seoul")
    public void notifyClosingQuestions() {
        int notified = 0;
        try {
            for (LocalDateTime minute : pendingMinutes(lastProcessed, currentMinute())) {
                try {
                    notified += questionDeadlineNotifier.notifyClosingQuestions(minute);
                } catch (Exception e) {
                    // notifier 는 방마다 예외를 삼키므로 여기까지 오는 실패는 발송을 시작하기도 전인
                    // 방 목록 조회 단계다. 아무에게도 보내지 않은 분이라 전진시키지 않고 다음 틱에
                    // 다시 시도한다. 계속 실패하면 MAX_CATCH_UP_MINUTES 를 넘어가며 자연히 버려진다.
                    log.error("익명 질문 마감 임박 알림 실패: minute={}", minute, e);
                    break;
                }
                lastProcessed = minute;
            }
        } catch (Exception e) {
            log.error("익명 질문 마감 임박 알림 대상 분 계산 실패", e);
        }
        // 매분 도는 잡이라 보낼 때만 남긴다.
        if (notified > 0) {
            log.info("익명 질문 마감 임박 알림 완료: {}명 대상", notified);
        }
    }

    private LocalDateTime currentMinute() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES);
    }

    /**
     * 아직 처리하지 않은 분들. 기동 직후에는 지나간 시각을 알 수 없으므로 현재 분만 본다. 크론이
     * 경계 직전에 발화해 이미 처리한 분이 다시 나오면 빈 목록이 되어 중복 발송을 막는다.
     */
    static java.util.List<LocalDateTime> pendingMinutes(LocalDateTime lastProcessed, LocalDateTime current) {
        if (lastProcessed == null || !lastProcessed.isBefore(current)) {
            return lastProcessed == null ? java.util.List.of(current) : java.util.List.of();
        }
        long missing = ChronoUnit.MINUTES.between(lastProcessed, current);
        LocalDateTime from = missing > MAX_CATCH_UP_MINUTES
                ? current.minusMinutes(MAX_CATCH_UP_MINUTES - 1L)
                : lastProcessed.plusMinutes(1);
        java.util.List<LocalDateTime> minutes = new java.util.ArrayList<>();
        for (LocalDateTime minute = from; !minute.isAfter(current); minute = minute.plusMinutes(1)) {
            minutes.add(minute);
        }
        return minutes;
    }
}
