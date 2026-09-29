package org.son.haruharu.domain.roomschedule.business;

import org.son.haruharu.domain.chat.implement.ChatMessage;
import org.son.haruharu.domain.chat.implement.ChatMessageReader;
import org.son.haruharu.domain.chat.implement.ChatMessageWriter;
import org.son.haruharu.domain.room.business.exception.RoomNotFoundException;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.roomschedule.business.command.ScheduleItemCommand;
import org.son.haruharu.domain.roomschedule.business.dto.RoomScheduleItemResponse;
import org.son.haruharu.domain.roomschedule.business.exception.ScheduleItemNotEditableException;
import org.son.haruharu.domain.roomschedule.implement.RevealField;
import org.son.haruharu.domain.roomschedule.implement.RoomScheduleItem;
import org.son.haruharu.domain.roomschedule.implement.RoomScheduleItemReader;
import org.son.haruharu.domain.roomschedule.implement.RoomScheduleItemWriter;
import org.son.haruharu.domain.roomschedule.implement.RoomScheduleStatus;
import org.son.haruharu.domain.roomschedule.implement.ScheduleContent;
import org.son.haruharu.common.ApiFieldError;
import org.son.haruharu.domain.roomschedule.implement.exception.InvalidScheduleItemException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 방 편성 관리(#457, F3/F4). 어드민 서버가 쓴다 — 저장만 하고 발송은 app 서버 잡(RoomScheduleDispatchService)이 한다.
 * 상태 전이는 전부 SCHEDULED 행에서만(조건부 UPDATE, 0행이면 409 SCHEDULE_ITEM_NOT_EDITABLE).
 * 시각 검증은 하지 않는다(방 시작 전·종료 후·같은 공개 항목 여러 행 전부 허용, 9/29 결정).
 */
@Service
@Transactional(readOnly = true)
public class RoomScheduleService {

    private final RoomReader roomReader;
    private final RoomScheduleItemReader itemReader;
    private final RoomScheduleItemWriter itemWriter;
    private final ChatMessageReader chatMessageReader;
    private final ChatMessageWriter chatMessageWriter;
    private final Clock clock;

    public RoomScheduleService(RoomReader roomReader, RoomScheduleItemReader itemReader,
                               RoomScheduleItemWriter itemWriter, ChatMessageReader chatMessageReader,
                               ChatMessageWriter chatMessageWriter, Clock clock) {
        this.roomReader = roomReader;
        this.itemReader = itemReader;
        this.itemWriter = itemWriter;
        this.chatMessageReader = chatMessageReader;
        this.chatMessageWriter = chatMessageWriter;
        this.clock = clock;
    }

    /**
     * 방 생성 요청의 chat_schedule[] 를 그대로 예약한다(방 생성과 같은 트랜잭션). 서버 기본값은 없다(비면 0줄).
     * 줄마다 run_at 필수, 칸 이름은 chat_schedule[i].run_at 처럼 내린다.
     */
    @Transactional
    public List<RoomScheduleItemResponse> createAll(Long roomId, List<ScheduleItemCommand> commands, String actor) {
        if (commands == null || commands.isEmpty()) {
            return List.of();
        }
        Room room = requireRoom(roomId);
        List<ApiFieldError> errors = new ArrayList<>();
        List<RoomScheduleItem> items = new ArrayList<>();
        for (int i = 0; i < commands.size(); i++) {
            ScheduleItemCommand command = commands.get(i);
            String prefix = "chat_schedule[" + i + "].";
            if (command.runAt() == null) {
                errors.add(new ApiFieldError(prefix + "run_at", "보낼 시각을 골라주세요"));
            }
            try {
                ScheduleContent content = contentOf(command, prefix);
                if (command.runAt() != null) {
                    items.add(RoomScheduleItem.scheduled(roomId, content, command.runAt(), actor));
                }
            } catch (InvalidScheduleItemException e) {
                errors.addAll(e.getFieldErrors());
            }
        }
        if (!errors.isEmpty()) {
            throw new InvalidScheduleItemException(errors);
        }
        return items.stream().map(itemWriter::save).map(saved -> RoomScheduleItemResponse.of(saved, room)).toList();
    }

    public List<RoomScheduleItemResponse> list(Long roomId) {
        Room room = requireRoom(roomId);
        List<RoomScheduleItem> items = itemReader.findByRoomId(roomId);
        Map<Long, LocalDateTime> deletedAt = messageDeletedAt(items);
        return items.stream()
                .map(item -> RoomScheduleItemResponse.of(item, room, deletedAt.get(item.sentMessageId())))
                .toList();
    }

    /** 행 추가. run_at 이 null 이면 지금(다음 틱에 나감). */
    @Transactional
    public RoomScheduleItemResponse add(Long roomId, ScheduleItemCommand command, String actor) {
        Room room = requireRoom(roomId);
        ScheduleContent content = contentOf(command, "");
        LocalDateTime runAt = command.runAt() != null ? command.runAt() : now();
        return RoomScheduleItemResponse.of(itemWriter.save(RoomScheduleItem.scheduled(roomId, content, runAt, actor)), room);
    }

    /** SCHEDULED 행 수정. 과거 시각으로 옮기면 다음 틱에 나간다(경고는 FE 만). */
    @Transactional
    public RoomScheduleItemResponse update(Long roomId, Long itemId, ScheduleItemCommand command, String actor) {
        Room room = requireRoom(roomId);
        RoomScheduleItem current = itemReader.getInRoom(roomId, itemId);
        List<String> revealFields = command.revealFields() != null
                ? command.revealFields()
                : current.revealFields().stream().map(RevealField::name).toList();
        Boolean push = command.push() != null ? command.push() : current.push();
        ScheduleContent content = ScheduleContent.validated(revealFields, command.messageContent(), push,
                command.pushTitle(), command.pushBody(), "");
        LocalDateTime runAt = command.runAt() != null ? command.runAt() : current.runAt();
        if (!itemWriter.updateIfScheduled(itemId, roomId, runAt, content, actor, now())) {
            throw new ScheduleItemNotEditableException();
        }
        return responseOf(itemReader.getInRoom(roomId, itemId), room);
    }

    /** 지금 보내기 = run_at 을 지금으로(내부 API 없음, 1분 잡이 집는다). */
    @Transactional
    public RoomScheduleItemResponse sendNow(Long roomId, Long itemId, String actor) {
        Room room = requireRoom(roomId);
        itemReader.getInRoom(roomId, itemId);
        if (!itemWriter.rescheduleIfScheduled(itemId, roomId, now(), actor, now())) {
            throw new ScheduleItemNotEditableException();
        }
        return responseOf(itemReader.getInRoom(roomId, itemId), room);
    }

    @Transactional
    public RoomScheduleItemResponse cancel(Long roomId, Long itemId, String actor) {
        Room room = requireRoom(roomId);
        itemReader.getInRoom(roomId, itemId);
        if (!itemWriter.cancelIfScheduled(itemId, roomId, actor, now())) {
            throw new ScheduleItemNotEditableException();
        }
        return responseOf(itemReader.getInRoom(roomId, itemId), room);
    }

    /**
     * 보낸 운영 글 삭제. SENT 이고 보낸 메시지가 있을 때만(아니면 409). 이미 지웠으면 그대로 200(멱등).
     * 앱 실시간 반영(MESSAGE_DELETED)은 app 잡이 방송한다.
     */
    @Transactional
    public RoomScheduleItemResponse deleteSentMessage(Long roomId, Long itemId) {
        Room room = requireRoom(roomId);
        RoomScheduleItem item = itemReader.getInRoom(roomId, itemId);
        if (item.status() != RoomScheduleStatus.SENT || item.sentMessageId() == null) {
            throw new ScheduleItemNotEditableException();
        }
        chatMessageWriter.markAdminDeleted(item.sentMessageId(), now());
        return responseOf(item, room);
    }

    private RoomScheduleItemResponse responseOf(RoomScheduleItem item, Room room) {
        return RoomScheduleItemResponse.of(item, room, messageDeletedAt(List.of(item)).get(item.sentMessageId()));
    }

    /** sent_message_id → chat_messages.admin_deleted_at (지운 것만). 목록 전체를 쿼리 1번으로. */
    private Map<Long, LocalDateTime> messageDeletedAt(List<RoomScheduleItem> items) {
        List<Long> ids = items.stream().map(RoomScheduleItem::sentMessageId).filter(Objects::nonNull).distinct().toList();
        Map<Long, LocalDateTime> result = new HashMap<>();
        if (ids.isEmpty()) {
            return result;
        }
        for (ChatMessage message : chatMessageReader.findAllByIds(ids)) {
            if (message.adminDeletedAt() != null) {
                result.put(message.id(), message.adminDeletedAt());
            }
        }
        return result;
    }

    private ScheduleContent contentOf(ScheduleItemCommand command, String prefix) {
        return ScheduleContent.validated(command.revealFields(), command.messageContent(), command.push(),
                command.pushTitle(), command.pushBody(), prefix);
    }

    private Room requireRoom(Long roomId) {
        return roomReader.findById(roomId).orElseThrow(RoomNotFoundException::new);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
