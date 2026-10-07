package org.son.haruharu.domain.mission.chat.business;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.son.haruharu.common.exception.BusinessException;
import org.son.haruharu.config.ChatFeatureProperties;
import org.son.haruharu.domain.chat.business.AdminChatMessageService;
import org.son.haruharu.domain.chat.implement.ChatRoom;
import org.son.haruharu.domain.chat.implement.ChatRoomReader;
import org.son.haruharu.domain.mission.chat.implement.MissionAnnouncement;
import org.son.haruharu.domain.mission.chat.implement.MissionAnnouncementReader;
import org.son.haruharu.domain.mission.chat.implement.MissionAnnouncementStatus;
import org.son.haruharu.domain.mission.chat.implement.MissionNotification;
import org.son.haruharu.domain.mission.common.implement.RoomMission;
import org.son.haruharu.domain.mission.common.implement.RoomMissionReader;
import org.son.haruharu.domain.notification.business.PushSendService;
import org.son.haruharu.domain.notification.implement.NotificationType;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.springframework.stereotype.Service;

/**
 * 방 편성 발송(#457). app 서버 1분 잡이 부른다. 행 단위 트랜잭션은 MissionDispatchStep, 푸시는 커밋 뒤.
 * 한 틱: SENDING 5분 초과 → FAILED(STUCK) / max(run_at, 방 시작, 수정 시각) + 30분 경과 → FAILED(EXPIRED)
 * / 방 PENDING 이거나 GROUP 채팅방 없음 → 다음 틱 / 방 종료 → FAILED(ROOM_ENDED)
 * / claim 이긴 인스턴스만 운영 글 저장 + SENT + 푸시 / 예외 → FAILED, 재시도 없음 / 끝에 지운 운영 글 MESSAGE_DELETED 방송.
 */
@Slf4j
@Service
@org.springframework.transaction.annotation.Transactional(readOnly = true)
public class MissionDispatchService {

    static final String DEFAULT_PUSH_TITLE = "📢 하루 공지";
    static final String DEFAULT_PUSH_BODY = "방 채팅에 새 공지가 올라왔어요";

    private final MissionAnnouncementReader itemReader;
    private final MissionDispatchStep step;
    private final RoomMissionReader missionReader;
    private final RoomReader roomReader;
    private final ChatRoomReader chatRoomReader;
    private final AdminChatMessageService adminChatMessageService;
    private final PushSendService pushSendService;
    private final ChatFeatureProperties.AdminMessage settings;
    private final Clock clock;

    public MissionDispatchService(MissionAnnouncementReader itemReader, MissionDispatchStep step, RoomMissionReader missionReader,
                                       RoomReader roomReader, ChatRoomReader chatRoomReader,
                                       AdminChatMessageService adminChatMessageService,
                                       PushSendService pushSendService, ChatFeatureProperties chatFeatureProperties,
                                       Clock clock) {
        this.itemReader = itemReader;
        this.step = step;
        this.missionReader = missionReader;
        this.roomReader = roomReader;
        this.chatRoomReader = chatRoomReader;
        this.adminChatMessageService = adminChatMessageService;
        this.pushSendService = pushSendService;
        this.settings = chatFeatureProperties.adminMessage();
        this.clock = clock;
    }

    /** 한 틱. 보낸 행 수를 돌려준다. */
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public int dispatchDue() {
        LocalDateTime now = LocalDateTime.now(clock);
        for (MissionAnnouncement stuck : itemReader.findSendingSince(now.minusMinutes(settings.stuckMinutes()), settings.batchSize())) {
            step.failIfUnchanged(stuck, "STUCK", now);
        }
        int sent = 0;
        for (MissionAnnouncement item : itemReader.findDue(now, settings.batchSize())) {
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

    private boolean dispatchOne(MissionAnnouncement item, LocalDateTime now) {
        Optional<Room> found = roomReader.findById(item.roomId());
        if (found.isEmpty()) {
            step.failIfUnchanged(item, "ROOM_NOT_FOUND", now);
            return false;
        }
        Room room = found.orElseThrow();
        RoomMission mission = missionReader.getById(item.roomMissionId());
        if (mission.canceledAt() != null) {
            step.failIfUnchanged(item, "MISSION_CANCELED", now);
            return false;
        }
        if (now.isBefore(mission.opensAt())) return false;
        if (effectiveAt(item, room).isBefore(now.minusMinutes(settings.expireMinutes()))) {
            step.failIfUnchanged(item, "EXPIRED", now);
            return false;
        }
        RoomStatus status = room.statusAt(now);
        if (status == RoomStatus.PENDING) {
            return false; // 방이 열리면 보낸다
        }
        if (status == RoomStatus.ENDED || status == RoomStatus.CANCELLED) {
            step.failIfUnchanged(item, "ROOM_ENDED", now);
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
        MissionAnnouncement claimed = itemReader.getById(item.id());
        MissionDispatchStep.SendResult result;
        try {
            result = step.send(claimed, chatRoom.orElseThrow().id());
        } catch (Exception e) {
            step.fail(item.id(), MissionAnnouncementStatus.SENDING, failReason(e), now);
            log.error("방 편성 발송 실패: itemId={}", item.id(), e);
            return false;
        }
        push(result, chatRoom.orElseThrow().id());
        return true;
    }

    /** 만료 기준 = max(run_at, 방 시작, 행 수정 시각). 방 시작 전 행은 방이 열린 뒤, 방금 고친 행은 고친 뒤 30분 유효. */
    static LocalDateTime effectiveAt(MissionAnnouncement item, Room room) {
        LocalDateTime at = item.runAt();
        if (room.startsAt() != null && room.startsAt().isAfter(at)) {
            at = room.startsAt();
        }
        if (item.updatedAt() != null && item.updatedAt().isAfter(at)) {
            at = item.updatedAt();
        }
        return at;
    }

    private void push(MissionDispatchStep.SendResult result, Long chatRoomId) {
        MissionAnnouncement item = result.announcement();
        List<Long> recipients = result.recipients();
        if (recipients.isEmpty()) {
            return;
        }
        Map<String, String> data = Map.of("chatRoomId", chatRoomId.toString(), "roomId", item.roomId().toString());
        // 운영 글 원문은 보내지 않는다. 00~08시엔 PushSendService 가 알림함에만 남긴다(quietHoursExempt=false).
        if (item.push() && item.hasMessage()) {
            pushSendService.sendToUsers(recipients, NotificationType.ADMIN_MESSAGE, data,
                    orDefault(item.pushTitle(), DEFAULT_PUSH_TITLE), orDefault(item.pushBody(), DEFAULT_PUSH_BODY));
        }
        for (MissionNotification notification : result.notifications()) {
            pushSendService.sendToUsers(recipients, notification.type(), data, notification.title(), notification.body());
        }
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    static String failReason(Exception e) {
        // DB 예외의 상세 값이나 운영 글을 감사 필드에 복사하지 않는다.
        return e instanceof BusinessException business
                ? business.getCode() : e.getClass().getSimpleName();
    }
}
