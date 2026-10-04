package org.son.haruharu.domain.notification.business;

import lombok.extern.slf4j.Slf4j;
import org.son.haruharu.domain.notification.implement.NotificationType;
import org.son.haruharu.domain.question.business.QuestionService;
import org.son.haruharu.domain.question.business.dto.QuestionDeadlineTargets;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/**
 * 익명 질문 마감 2시간 전, 아직 답하지 않았거나 질문권을 쓰지 않은 참가자에게 마감 임박 푸시를 보낸다.
 * 마감은 방마다 다른 final_selection_opens_at 이라 매분 스캔하며 그 시각의 2시간 전인 방만 보낸다.
 * 어드민이 초 단위까지 입력할 수 있어 비교 전에 분 단위로 자른다.
 * <p>한계: 이 타입은 알림함에 남기지 않으므로(마감 후에는 안내가 거짓이 된다), 마감 2시간 전이
 * 야간 푸시 차단 구간(QuietHoursProperties, 기본 00:00~08:00)에 걸리는 방에서는 안내가 푸시로도
 * 알림함으로도 남지 않고 사라진다. 즉 마감이 02:00~10:00 인 방은 이 알림을 받지 못한다.
 * SlotDeadlineNotifier 와 같은 이유로 의도적으로 @Transactional 이 없다 — 트랜잭션을 열면 FCM 발송
 * IO가 묶이고, 발송 중 무효 토큰 삭제(REQUIRED 전파)가 readOnly 트랜잭션에 조인되어 실패한다.
 */
@Slf4j
@Service
public class QuestionDeadlineNotifier {

    private static final int DEADLINE_LEAD_HOURS = 2;

    private final RoomReader roomReader;
    private final QuestionService questionService;
    private final PushSendService pushSendService;

    public QuestionDeadlineNotifier(RoomReader roomReader, QuestionService questionService,
                                    PushSendService pushSendService) {
        this.roomReader = roomReader;
        this.questionService = questionService;
        this.pushSendService = pushSendService;
    }

    /**
     * 반환값은 푸시 대상 사용자 수(모니터링 로그용).
     *
     * <p>계약: 한 건이라도 발송한 뒤에는 예외를 밖으로 내보내지 않는다. 방 단위로 예외를 삼키므로
     * 밖으로 나가는 실패는 발송을 시작하기 전인 방 목록 조회뿐이다. 호출자(QuestionDeadlineScheduler)는
     * 이 계약에 기대어 실패한 분을 그대로 다시 시도한다 — 이 계약이 깨지면 중복 발송이 된다.
     */
    public int notifyClosingQuestions(LocalDateTime now) {
        int notified = 0;
        for (Room room : roomReader.getReconcilable()) {
            try {
                notified += notifyRoom(room, now);
            } catch (Exception exception) {
                // 방 하나의 실패가 나머지 방의 안내를 막지 않는다. 식별자 노출을 피해 종류만 남긴다.
                log.warn("익명 질문 마감 임박 알림 방 처리 실패: {}", exception.getClass().getSimpleName());
            }
        }
        return notified;
    }

    private int notifyRoom(Room room, LocalDateTime now) {
        LocalDateTime closesAt = room.finalSelectionOpensAt();
        if (room.startsAt() == null || closesAt == null || room.statusAt(now) != RoomStatus.ACTIVE) {
            return 0;
        }
        if (!closesAt.minusHours(DEADLINE_LEAD_HOURS).truncatedTo(ChronoUnit.MINUTES).equals(now)) {
            return 0;
        }
        QuestionDeadlineTargets targets = questionService.deadlineTargets(room.id());
        if (targets.isEmpty()) {
            return 0;
        }
        Map<String, String> payload = Map.of("room_id", room.id().toString());
        send(targets.unansweredUserIds(), NotificationType.QUESTION_ANSWER_DEADLINE_SOON, payload);
        send(targets.unusedGrantUserIds(), NotificationType.QUESTION_SEND_DEADLINE_SOON, payload);
        return targets.unansweredUserIds().size() + targets.unusedGrantUserIds().size();
    }

    private void send(List<Long> userIds, NotificationType type, Map<String, String> payload) {
        if (!userIds.isEmpty()) {
            pushSendService.sendToUsers(userIds, type, payload);
        }
    }
}
