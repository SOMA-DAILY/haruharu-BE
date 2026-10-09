package org.son.haruharu.domain.roomschedule.business;

import org.son.haruharu.domain.chat.business.AdminChatMessageService;
import org.son.haruharu.domain.chat.implement.ChatMessage;
import org.son.haruharu.domain.chat.implement.ChatRoomMember;
import org.son.haruharu.domain.chat.implement.ChatRoomMemberReader;
import org.son.haruharu.domain.roomschedule.implement.RoomScheduleItem;
import org.son.haruharu.domain.roomschedule.implement.RoomScheduleItemReader;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.domain.roomschedule.business.exception.ScheduleDispatchRejectedException;
import org.son.haruharu.domain.roomschedule.implement.RoomScheduleItemWriter;
import org.son.haruharu.domain.roomschedule.implement.RoomScheduleStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Clock;
import java.util.List;

/**
 * 편성 발송의 트랜잭션 단위(#457). RoomScheduleDispatchService(트랜잭션 없음)가 행마다 부른다.
 * 각 메서드가 자기 트랜잭션을 연다 — claim 을 먼저 커밋해 다른 인스턴스가 같은 행을 못 집게 하고,
 * [운영 글 저장 + 브로드캐스트 이벤트 + SENT] 는 한 트랜잭션으로 묶어 커밋 후 STOMP 가 나가게 한다.
 */
@Service
public class RoomScheduleDispatchStep {

    private final RoomScheduleItemWriter itemWriter;
    private final AdminChatMessageService adminChatMessageService;
    private final ChatRoomMemberReader chatRoomMemberReader;
    private final RoomReader roomReader;
    private final RoomScheduleItemReader itemReader;
    private final Clock clock;

    public RoomScheduleDispatchStep(RoomScheduleItemWriter itemWriter, AdminChatMessageService adminChatMessageService,
                                    ChatRoomMemberReader chatRoomMemberReader, RoomReader roomReader,
                                    RoomScheduleItemReader itemReader, Clock clock) {
        this.itemWriter = itemWriter;
        this.adminChatMessageService = adminChatMessageService;
        this.chatRoomMemberReader = chatRoomMemberReader;
        this.roomReader = roomReader;
        this.itemReader = itemReader;
        this.clock = clock;
    }

    /** SCHEDULED → SENDING. true 면 이 인스턴스가 보낸다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean claim(Long itemId, LocalDateTime now) {
        return itemWriter.claim(itemId, now);
    }

    /** 운영 글 저장(있으면) + SENT. 푸시 대상(GROUP 채팅 활성 멤버)을 돌려준다. 실패하면 전부 롤백된다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<Long> send(RoomScheduleItem claimed, Long chatRoomId) {
        // 방 → 편성 → 채팅방 순서. 취소/종료가 먼저 커밋됐으면 발송하지 않는다.
        Room room = roomReader.findByIdForUpdate(claimed.roomId())
                .orElseThrow(() -> new ScheduleDispatchRejectedException("ROOM_NOT_FOUND"));
        LocalDateTime executionTime = LocalDateTime.now(clock);
        RoomStatus status = room.statusAt(executionTime);
        if (status == RoomStatus.ENDED || status == RoomStatus.CANCELLED) {
            throw new ScheduleDispatchRejectedException("ROOM_ENDED");
        }
        if (status == RoomStatus.PENDING) {
            throw new ScheduleDispatchRejectedException("ROOM_NOT_STARTED");
        }
        RoomScheduleItem item = itemReader.getForUpdate(claimed.id());
        if (item.status() != RoomScheduleStatus.SENDING || item.runAt().isAfter(executionTime)) {
            throw new ScheduleDispatchRejectedException("SCHEDULE_NOT_SENDING");
        }
        Long messageId = null;
        if (item.hasMessage()) {
            ChatMessage saved = adminChatMessageService.sendAsSystem(chatRoomId, item.messageContent(), item.id());
            messageId = saved.id();
        }
        if (!itemWriter.markSent(item.id(), messageId, executionTime)) {
            throw new IllegalStateException("편성 행이 SENDING 이 아닙니다: id=" + item.id());
        }
        return chatRoomMemberReader.findByChatRoomId(chatRoomId).stream()
                .filter(member -> !member.hasLeft())
                .map(ChatRoomMember::userId)
                .toList();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean fail(Long itemId, RoomScheduleStatus from, String reason, LocalDateTime now) {
        return itemWriter.markFailed(itemId, from, reason, now);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean failIfUnchanged(RoomScheduleItem snapshot, String reason, LocalDateTime now) {
        return itemWriter.markFailedIfUnchanged(snapshot, reason, now);
    }
}
