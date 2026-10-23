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
import org.son.haruharu.domain.chat.implement.ChatRoomMemberReader;
import org.son.haruharu.domain.chat.implement.ChatRoomMemberWriter;
import org.son.haruharu.domain.chat.implement.ChatRoomPair;
import org.son.haruharu.domain.chat.implement.ChatRoomPairReader;
import org.son.haruharu.domain.chat.implement.ChatRoomPairWriter;
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
    private final ChatRoomWriter chatRoomWriter;
    private final ChatRoomMemberReader chatRoomMemberReader;
    private final ChatRoomMemberWriter chatRoomMemberWriter;
    private final ChatRoomPairReader chatRoomPairReader;
    private final ChatRoomPairWriter chatRoomPairWriter;
    private final PairChatSchedule pairChatSchedule;
    private final BlockReader blockReader;
    private final UserService userService;
    private final Clock clock;

    public PairChatService(RoomReader roomReader, RoomMemberReader roomMemberReader, ChatRoomWriter chatRoomWriter,
                           ChatRoomMemberReader chatRoomMemberReader, ChatRoomMemberWriter chatRoomMemberWriter,
                           ChatRoomPairReader chatRoomPairReader, ChatRoomPairWriter chatRoomPairWriter,
                           PairChatSchedule pairChatSchedule, BlockReader blockReader, UserService userService,
                           Clock clock) {
        this.roomReader = roomReader;
        this.roomMemberReader = roomMemberReader;
        this.chatRoomWriter = chatRoomWriter;
        this.chatRoomMemberReader = chatRoomMemberReader;
        this.chatRoomMemberWriter = chatRoomMemberWriter;
        this.chatRoomPairReader = chatRoomPairReader;
        this.chatRoomPairWriter = chatRoomPairWriter;
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
     * 원본 방 행을 잠가 같은 두 사람이 동시에 열어도 한 방으로 모은다.
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
        ChatRoomPair existing = chatRoomPairReader.findByRoomIdAndUsers(roomId, userId, partnerUserId).orElse(null);
        if (existing != null) {
            ensureMember(existing.chatRoomId(), userId, now);
            ensureMember(existing.chatRoomId(), partnerUserId, now);
            return new PairChatRoomResponse(existing.chatRoomId());
        }
        ChatRoom chatRoom = chatRoomWriter.save(ChatRoom.createPair());
        chatRoomPairWriter.save(ChatRoomPair.of(chatRoom.id(), roomId, userId, partnerUserId, now));
        chatRoomMemberWriter.save(ChatRoomMember.join(chatRoom.id(), userId, now));
        chatRoomMemberWriter.save(ChatRoomMember.join(chatRoom.id(), partnerUserId, now));
        return new PairChatRoomResponse(chatRoom.id());
    }

    /** 쌍 행은 있는데 멤버 행이 없는 경우(부분 실패 복구)만 채운다. 나간 기록은 되살리지 않는다(PAIR 는 나가기가 없다). */
    private void ensureMember(Long chatRoomId, Long userId, LocalDateTime now) {
        if (chatRoomMemberReader.findMembership(chatRoomId, userId).isEmpty()) {
            chatRoomMemberWriter.save(ChatRoomMember.join(chatRoomId, userId, now));
        }
    }
}
