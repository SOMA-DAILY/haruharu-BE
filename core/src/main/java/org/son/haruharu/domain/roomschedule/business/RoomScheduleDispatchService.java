package org.son.haruharu.domain.roomschedule.business;

import lombok.extern.slf4j.Slf4j;
import org.son.haruharu.domain.chat.business.AdminChatMessageService;
import org.son.haruharu.domain.chat.implement.ChatRoom;
import org.son.haruharu.domain.chat.implement.ChatRoomReader;
import org.son.haruharu.domain.notification.business.PushSendService;
import org.son.haruharu.domain.notification.implement.NotificationType;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.domain.roomschedule.business.RoomScheduleDispatchStep.SentItem;
import org.son.haruharu.domain.roomschedule.implement.RevealField;
import org.son.haruharu.domain.roomschedule.implement.RoomScheduleItem;
import org.son.haruharu.domain.roomschedule.implement.RoomScheduleItemReader;
import org.son.haruharu.domain.roomschedule.implement.RoomScheduleStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * 방 편성 발송(#457). app 서버 1분 잡(RoomScheduleDispatchScheduler, 플래그 haruharu.chat.admin-message.enabled)이 부른다.
 * 트랜잭션은 행 단위로 RoomScheduleDispatchStep 이 연다. 푸시는 커밋 뒤 트랜잭션 밖에서 보낸다(PushSendService 가 실패를 삼킴).
 * <ol>
 *   <li>SENDING 인 채 5분 넘은 행(크래시) → FAILED(STUCK)</li>
 *   <li>status=SCHEDULED and run_at<=now 오래된 순. max(run_at, 방 시작) 이 30분 넘게 지났으면 FAILED(EXPIRED)
 *       — 플래그 OFF 동안 쌓인 행이 켜는 순간 한꺼번에 나가지 않게</li>
 *   <li>방 상태는 statusAt(now): PENDING 이면 다음 틱, ENDED/CANCELLED 면 FAILED(ROOM_ENDED). GROUP 채팅방이 아직 없으면 다음 틱</li>
 *   <li>claim(SCHEDULED→SENDING) 이긴 인스턴스만 보낸다 → 운영 글 저장 + SENT → 커밋 후 푸시</li>
 *   <li>예외는 FAILED(예외 앞 200자). 재시도 없음(운영자가 글을 다시 추가)</li>
 *   <li>운영자가 지운 운영 글을 MESSAGE_DELETED 로 방송</li>
 * </ol>
 */
@Slf4j
@Service
public class RoomScheduleDispatchService {

    static final int BATCH_SIZE = 100;
    static final int EXPIRE_MINUTES = 30;
    static final int STUCK_MINUTES = 5;
    static final String DEFAULT_PUSH_TITLE = "📢 하루 공지";
    static final String DEFAULT_PUSH_BODY = "방 채팅에 새 공지가 올라왔어요";

    private final RoomScheduleItemReader itemReader;
    private final RoomScheduleDispatchStep step;
    private final RoomReader roomReader;
    private final ChatRoomReader chatRoomReader;
    private final AdminChatMessageService adminChatMessageService;
    private final PushSendService pushSendService;
    private final Clock clock;

    public RoomScheduleDispatchService(RoomScheduleItemReader itemReader, RoomScheduleDispatchStep step,
                                       RoomReader roomReader, ChatRoomReader chatRoomReader,
                                       AdminChatMessageService adminChatMessageService,
                                       PushSendService pushSendService, Clock clock) {
        this.itemReader = itemReader;
        this.step = step;
        this.roomReader = roomReader;
        this.chatRoomReader = chatRoomReader;
        this.adminChatMessageService = adminChatMessageService;
        this.pushSendService = pushSendService;
        this.clock = clock;
    }

    /** 한 틱. 보낸 행 수를 돌려준다. */
    public int dispatchDue() {
        LocalDateTime now = LocalDateTime.now(clock);
        for (RoomScheduleItem stuck : itemReader.findSendingSince(now.minusMinutes(STUCK_MINUTES), BATCH_SIZE)) {
            step.fail(stuck.id(), RoomScheduleStatus.SENDING, "STUCK", now);
        }
        int sent = 0;
        for (RoomScheduleItem item : itemReader.findDue(now, BATCH_SIZE)) {
            try {
                if (dispatchOne(item, now)) {
                    sent++;
                }
            } catch (Exception e) {
                log.error("방 편성 발송 실패: itemId={} roomId={}", item.id(), item.roomId(), e);
            }
        }
        adminChatMessageService.broadcastPendingDeletes(BATCH_SIZE, now);
        return sent;
    }

    private boolean dispatchOne(RoomScheduleItem item, LocalDateTime now) {
        Optional<Room> found = roomReader.findById(item.roomId());
        if (found.isEmpty()) {
            step.fail(item.id(), RoomScheduleStatus.SCHEDULED, "ROOM_NOT_FOUND", now);
            return false;
        }
        Room room = found.get();
        if (effectiveAt(item, room).isBefore(now.minusMinutes(EXPIRE_MINUTES))) {
            step.fail(item.id(), RoomScheduleStatus.SCHEDULED, "EXPIRED", now);
            return false;
        }
        RoomStatus status = room.statusAt(now);
        if (status == RoomStatus.PENDING) {
            return false; // 방이 열리면 보낸다
        }
        if (status == RoomStatus.ENDED || status == RoomStatus.CANCELLED) {
            step.fail(item.id(), RoomScheduleStatus.SCHEDULED, "ROOM_ENDED", now);
            return false;
        }
        Optional<ChatRoom> chatRoom = chatRoomReader.findByRoomId(item.roomId());
        if (chatRoom.isEmpty()) {
            return false; // GROUP 채팅방은 RoomStartedEvent 뒤에 생긴다 — 다음 틱
        }
        if (!step.claim(item.id(), now)) {
            return false; // 다른 인스턴스가 집었거나 어드민이 방금 바꿈
        }
        SentItem sent;
        try {
            sent = step.send(item, chatRoom.get().id(), now);
        } catch (Exception e) {
            step.fail(item.id(), RoomScheduleStatus.SENDING, failReason(e), now);
            log.error("방 편성 발송 실패: itemId={}", item.id(), e);
            return false;
        }
        push(item, chatRoom.get().id(), sent);
        return true;
    }

    /** 방 시작 전 행은 방이 열린 시각부터 30분 유효하다. */
    private static LocalDateTime effectiveAt(RoomScheduleItem item, Room room) {
        LocalDateTime startsAt = room.startsAt();
        return startsAt != null && startsAt.isAfter(item.runAt()) ? startsAt : item.runAt();
    }

    private void push(RoomScheduleItem item, Long chatRoomId, SentItem sent) {
        if (sent.recipientUserIds().isEmpty()) {
            return;
        }
        Map<String, String> data = Map.of("chatRoomId", chatRoomId.toString(), "roomId", item.roomId().toString());
        // 운영 글 원문은 보내지 않는다. 00~08시엔 PushSendService 가 알림함에만 남긴다(quietHoursExempt=false).
        if (item.push() && item.hasMessage()) {
            pushSendService.sendToUsers(sent.recipientUserIds(), NotificationType.ADMIN_MESSAGE, data,
                    orDefault(item.pushTitle(), DEFAULT_PUSH_TITLE), orDefault(item.pushBody(), DEFAULT_PUSH_BODY));
        }
        if (item.hasReveal()) {
            pushSendService.sendToUsers(sent.recipientUserIds(), NotificationType.PROFILE_REVEAL, data,
                    RevealField.pushTitleOf(item.revealFields()), RevealField.pushBodyOf(item.revealFields()));
        }
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    static String failReason(Exception e) {
        String reason = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
        return reason.length() > 200 ? reason.substring(0, 200) : reason;
    }
}
