package org.son.haruharu.domain.roomschedule.business;

import lombok.extern.slf4j.Slf4j;
import org.son.haruharu.config.ChatFeatureProperties;
import org.son.haruharu.domain.chat.business.AdminChatMessageService;
import org.son.haruharu.domain.chat.implement.ChatRoom;
import org.son.haruharu.domain.chat.implement.ChatRoomReader;
import org.son.haruharu.domain.notification.business.PushSendService;
import org.son.haruharu.domain.notification.implement.NotificationType;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.domain.roomschedule.implement.RevealField;
import org.son.haruharu.domain.roomschedule.implement.RoomScheduleItem;
import org.son.haruharu.domain.roomschedule.implement.RoomScheduleItemReader;
import org.son.haruharu.domain.roomschedule.implement.RoomScheduleStatus;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;

/**
 * 방 편성 발송(#457). app 서버 1분 잡이 부른다. 행 단위 트랜잭션은 RoomScheduleDispatchStep, 푸시는 커밋 뒤.
 * 한 틱: SENDING 5분 초과 → FAILED(STUCK) / max(run_at, 방 시작, 수정 시각) + 30분 경과 → FAILED(EXPIRED)
 * / 방 PENDING 이거나 GROUP 채팅방 없음 → 다음 틱 / 방 종료 → FAILED(ROOM_ENDED)
 * / claim 이긴 인스턴스만 운영 글 저장 + SENT + 푸시 / 예외 → FAILED, 재시도 없음 / 끝에 지운 운영 글 MESSAGE_DELETED 방송.
 */
@Slf4j
@Service
public class RoomScheduleDispatchService {

    static final String DEFAULT_PUSH_TITLE = "📢 하루 공지";
    static final String DEFAULT_PUSH_BODY = "방 채팅에 새 공지가 올라왔어요";

    private final RoomScheduleItemReader itemReader;
    private final RoomScheduleDispatchStep step;
    private final RoomReader roomReader;
    private final ChatRoomReader chatRoomReader;
    private final AdminChatMessageService adminChatMessageService;
    private final PushSendService pushSendService;
    private final ChatFeatureProperties.AdminMessage settings;
    private final Clock clock;

    public RoomScheduleDispatchService(RoomScheduleItemReader itemReader, RoomScheduleDispatchStep step,
                                       RoomReader roomReader, ChatRoomReader chatRoomReader,
                                       AdminChatMessageService adminChatMessageService,
                                       PushSendService pushSendService, ChatFeatureProperties chatFeatureProperties,
                                       Clock clock) {
        this.itemReader = itemReader;
        this.step = step;
        this.roomReader = roomReader;
        this.chatRoomReader = chatRoomReader;
        this.adminChatMessageService = adminChatMessageService;
        this.pushSendService = pushSendService;
        this.settings = chatFeatureProperties.adminMessage();
        this.clock = clock;
    }

    /** 한 틱. 보낸 행 수를 돌려준다. */
    public int dispatchDue() {
        LocalDateTime now = LocalDateTime.now(clock);
        for (RoomScheduleItem stuck : itemReader.findSendingSince(now.minusMinutes(settings.stuckMinutes()), settings.batchSize())) {
            step.fail(stuck.id(), RoomScheduleStatus.SENDING, "STUCK", now);
        }
        int sent = 0;
        for (RoomScheduleItem item : itemReader.findDue(now, settings.batchSize())) {
            try {
                if (dispatchOne(item, now)) {
                    sent++;
                }
            } catch (Exception e) {
                log.error("방 편성 발송 실패: itemId={} roomId={}", item.id(), item.roomId(), e);
            }
        }
        adminChatMessageService.broadcastPendingDeletes(settings.batchSize(), now);
        return sent;
    }

    private boolean dispatchOne(RoomScheduleItem item, LocalDateTime now) {
        Optional<Room> found = roomReader.findById(item.roomId());
        if (found.isEmpty()) {
            step.fail(item.id(), RoomScheduleStatus.SCHEDULED, "ROOM_NOT_FOUND", now);
            return false;
        }
        Room room = found.get();
        if (effectiveAt(item, room).isBefore(now.minusMinutes(settings.expireMinutes()))) {
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
            return false; // 다른 인스턴스가 집었거나 어드민이 방금 취소·시각 변경
        }
        // 조회 스냅샷 대신 claim 뒤의 행으로 보낸다(조회와 claim 사이 어드민 PATCH 반영).
        RoomScheduleItem claimed = itemReader.findById(item.id()).orElse(item);
        List<Long> recipients;
        try {
            recipients = step.send(claimed, chatRoom.get().id(), now);
        } catch (Exception e) {
            step.fail(item.id(), RoomScheduleStatus.SENDING, failReason(e), now);
            log.error("방 편성 발송 실패: itemId={}", item.id(), e);
            return false;
        }
        push(claimed, chatRoom.get().id(), recipients);
        return true;
    }

    /** 만료 기준 = max(run_at, 방 시작, 행 수정 시각). 방 시작 전 행은 방이 열린 뒤, 방금 고친 행은 고친 뒤 30분 유효. */
    static LocalDateTime effectiveAt(RoomScheduleItem item, Room room) {
        LocalDateTime at = item.runAt();
        if (room.startsAt() != null && room.startsAt().isAfter(at)) {
            at = room.startsAt();
        }
        if (item.updatedAt() != null && item.updatedAt().isAfter(at)) {
            at = item.updatedAt();
        }
        return at;
    }

    private void push(RoomScheduleItem item, Long chatRoomId, List<Long> recipients) {
        if (recipients.isEmpty()) {
            return;
        }
        Map<String, String> data = Map.of("chatRoomId", chatRoomId.toString(), "roomId", item.roomId().toString());
        // 운영 글 원문은 보내지 않는다. 00~08시엔 PushSendService 가 알림함에만 남긴다(quietHoursExempt=false).
        if (item.push() && item.hasMessage()) {
            pushSendService.sendToUsers(recipients, NotificationType.ADMIN_MESSAGE, data,
                    orDefault(item.pushTitle(), DEFAULT_PUSH_TITLE), orDefault(item.pushBody(), DEFAULT_PUSH_BODY));
        }
        // 같은 항목이 더 이른 행에서 이미 공개됐으면 그 항목은 공개 알림에서 뺀다(같은 항목 여러 행은 허용이라 푸시만 막음).
        List<RevealField> newlyRevealed = newlyRevealed(item);
        if (!newlyRevealed.isEmpty()) {
            pushSendService.sendToUsers(recipients, NotificationType.PROFILE_REVEAL, data,
                    RevealField.pushTitleOf(newlyRevealed), RevealField.pushBodyOf(newlyRevealed));
        }
    }

    private List<RevealField> newlyRevealed(RoomScheduleItem item) {
        if (!item.hasReveal()) {
            return List.of();
        }
        Set<RevealField> already = itemReader.findRevealedBefore(item);
        return item.revealFields().stream().filter(field -> !already.contains(field)).toList();
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    static String failReason(Exception e) {
        String reason = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
        return reason.length() > 200 ? reason.substring(0, 200) : reason;
    }
}
