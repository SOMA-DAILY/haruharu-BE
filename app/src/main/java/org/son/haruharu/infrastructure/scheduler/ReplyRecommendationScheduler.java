package org.son.haruharu.infrastructure.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.son.haruharu.domain.replyrecommendation.business.ReplyRecommendationMaintenanceService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 영상 답장 추천(#558) 앱 서버 쪽 주기 작업. 스냅샷 만들기는 기능 스위치가 켜졌을 때만 일하고, 정리(삭제·무효화·보관 기간)는
 * 스위치를 꺼도 계속 돈다 — 껐다고 원문 정리가 멈추면 안 된다. ai_reply 스키마가 없으면 모두 아무것도 하지 않는다.
 * 행 단위로 잡아(SKIP LOCKED) 서버가 여러 대여도 한 번만 처리한다. 모델 호출은 AI 작업기가 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReplyRecommendationScheduler {

    private final ReplyRecommendationMaintenanceService maintenance;

    /** 게시·추천 요청으로 생긴 분석 요청을 입력 스냅샷으로 만든다. 개인화 다음 단계도 진행한다. */
    @Scheduled(fixedDelayString = "${haruharu.reply-recommendation.snapshot.sweep-delay-ms:3000}",
            initialDelayString = "${haruharu.reply-recommendation.snapshot.initial-delay-ms:5000}")
    public void prepare() {
        try {
            maintenance.prepareSnapshots();
            maintenance.advancePersonalRequests();
        } catch (Exception e) {
            log.error("답장 추천 스냅샷 잡 실패", e);
        }
    }

    /** 24시간 답장 없던 노출 닫기, 삭제·숨김 영상과 지워진 답장 정리. 매 10분 50초(다른 잡과 다른 초). */
    @Scheduled(cron = "${haruharu.reply-recommendation.maintenance-cron:50 */10 * * * *}", zone = "Asia/Seoul")
    public void maintain() {
        try {
            maintenance.maintain();
        } catch (Exception e) {
            log.error("답장 추천 정리 잡 실패", e);
        }
    }

    /** 보관 기간이 지난 원문·파생 자료 삭제. 매일 04:35. */
    @Scheduled(cron = "${haruharu.reply-recommendation.purge-cron:0 35 4 * * *}", zone = "Asia/Seoul")
    public void purge() {
        try {
            maintenance.purgeExpiredSnapshots();
        } catch (Exception e) {
            log.error("답장 추천 원문 정리 잡 실패", e);
        }
    }
}
