package org.son.haruharu.domain.chat.business;

import java.time.Clock;
import java.time.LocalDateTime;
import org.son.haruharu.domain.block.implement.BlockReader;
import org.son.haruharu.domain.chat.business.dto.PairChatRoomResponse;
import org.son.haruharu.domain.chat.business.dto.PairChatWindowResponse;
import org.son.haruharu.domain.chat.business.exception.ChatPartnerBlockedException;
import org.son.haruharu.domain.chat.business.exception.ChatPartnerWithdrawnException;
import org.son.haruharu.domain.chat.business.exception.PairChatInvalidTargetException;
import org.son.haruharu.domain.chat.business.exception.PairChatNotOpenException;
import org.son.haruharu.domain.chat.implement.ChatRoom;
import org.son.haruharu.domain.chat.implement.ChatRoomMember;
import org.son.haruharu.domain.chat.implement.ChatRoomMemberWriter;
import org.son.haruharu.domain.chat.implement.ChatRoomReader;
import org.son.haruharu.domain.chat.implement.ChatRoomWriter;
import org.son.haruharu.domain.chat.implement.PairChatSchedule;
import org.son.haruharu.domain.room.business.exception.RoomNotFoundException;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.user.business.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영자가 정한 시간에만 열리는 1:1 대화(#540). 대화 시간 조회와 1:1 방 열기를 조율한다.
 * 메시지 전송·목록·상세의 PAIR 규칙은 ChatService 가 가진다.
 */
@Service
@Transactional(readOnly = true)
public class PairChatService {

    private final RoomReader roomReader;
    private final RoomMemberReader roomMemberReader;
    private final ChatRoomReader chatRoomReader;
    private final ChatRoomWriter chatRoomWriter;
    private final ChatRoomMemberWriter chatRoomMemberWriter;
    private final PairChatSchedule pairChatSchedule;
    private final BlockReader blockReader;
    private final UserService userService;
    private final Clock clock;

    public PairChatService(RoomReader roomReader, RoomMemberReader roomMemberReader, ChatRoomReader chatRoomReader,
                           ChatRoomWriter chatRoomWriter, ChatRoomMemberWriter chatRoomMemberWriter,
                           PairChatSchedule pairChatSchedule, BlockReader blockReader, UserService userService,
                           Clock clock) {
        this.roomReader = roomReader;
        this.roomMemberReader = roomMemberReader;
        this.chatRoomReader = chatRoomReader;
        this.chatRoomWriter = chatRoomWriter;
        this.chatRoomMemberWriter = chatRoomMemberWriter;
        this.pairChatSchedule = pairChatSchedule;
        this.blockReader = blockReader;
        this.userService = userService;
        this.clock = clock;
    }

    /** 방의 대화 시간 상태. 방 참가자만 본다. */
    public PairChatWindowResponse getWindow(Long roomId, Long userId) {
        roomReader.findById(roomId).orElseThrow(RoomNotFoundException::new);
        roomMemberReader.getByRoomIdAndUserId(roomId, userId);
        return PairChatWindowResponse.from(pairChatSchedule.windowAt(roomId, LocalDateTime.now(clock)));
    }

    /**
     * 같은 방 참가자와의 1:1 방을 연다. 같은 두 사람이면 기존 방을 돌려준다(멱등).
     * 대화 시간 중에만 열 수 있고, 나 자신·다른 방 멤버·탈퇴·차단 관계는 거절한다.
     * 원본 방 행을 잠가 같은 두 사람이 동시에 열어도 한 방으로 모은다(PAIR 생성 경로는 이것 하나다).
     */
    @Transactional
    public PairChatRoomResponse open(Long roomId, Long userId, Long targetRoomMemberId) {
        roomReader.findByIdForUpdate(roomId).orElseThrow(RoomNotFoundException::new);
        roomMemberReader.getByRoomIdAndUserId(roomId, userId);
        RoomMember target = roomMemberReader.findById(targetRoomMemberId)
                .filter(member -> member.roomId().equals(roomId))
                .filter(member -> !member.userId().equals(userId))
                .orElseThrow(PairChatInvalidTargetException::new);
        LocalDateTime now = LocalDateTime.now(clock);
        if (!pairChatSchedule.windowAt(roomId, now).isOpen()) {
            throw new PairChatNotOpenException();
        }
        Long partnerUserId = target.userId();
        if (userService.isWithdrawn(partnerUserId)) {
            throw new ChatPartnerWithdrawnException();
        }
        if (blockReader.isBlockedBetween(userId, partnerUserId)) {
            throw new ChatPartnerBlockedException();
        }
        Long existing = chatRoomReader.findPair(roomId, userId, partnerUserId).map(ChatRoom::id).orElse(null);
        if (existing != null) {
            return new PairChatRoomResponse(existing);
        }
        ChatRoom chatRoom = chatRoomWriter.save(ChatRoom.createPair(roomId));
        chatRoomMemberWriter.save(ChatRoomMember.join(chatRoom.id(), userId, now));
        chatRoomMemberWriter.save(ChatRoomMember.join(chatRoom.id(), partnerUserId, now));
        return new PairChatRoomResponse(chatRoom.id());
    }
}
