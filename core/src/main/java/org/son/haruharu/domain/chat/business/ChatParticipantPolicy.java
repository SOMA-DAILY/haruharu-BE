package org.son.haruharu.domain.chat.business;

import org.son.haruharu.domain.block.implement.BlockReader;
import org.son.haruharu.domain.chat.business.exception.ChatNotMemberException;
import org.son.haruharu.domain.chat.business.exception.ChatPartnerBlockedException;
import org.son.haruharu.domain.chat.business.exception.ChatPartnerWithdrawnException;
import org.son.haruharu.domain.chat.implement.ChatRoom;
import org.son.haruharu.domain.chat.implement.ChatRoomMember;
import org.son.haruharu.domain.chat.implement.ChatRoomMemberReader;
import org.son.haruharu.domain.chat.implement.ChatRoomReader;
import org.son.haruharu.domain.chat.implement.ChatRoomType;
import org.son.haruharu.domain.profile.business.ProfileService;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.user.business.UserService;
import org.springframework.stereotype.Component;

/** 채팅 참여자 규칙. 표시 이름과 1:1(DIRECT·PAIR) 상대 접근 검사를 ChatService 와 반응 서비스가 같이 쓴다. */
@Component
public class ChatParticipantPolicy {

    private final ChatRoomReader chatRoomReader;
    private final ChatRoomMemberReader chatRoomMemberReader;
    private final RoomMemberReader roomMemberReader;
    private final ProfileService profileService;
    private final UserService userService;
    private final BlockReader blockReader;

    public ChatParticipantPolicy(ChatRoomReader chatRoomReader, ChatRoomMemberReader chatRoomMemberReader,
                                 RoomMemberReader roomMemberReader, ProfileService profileService,
                                 UserService userService, BlockReader blockReader) {
        this.chatRoomReader = chatRoomReader;
        this.chatRoomMemberReader = chatRoomMemberReader;
        this.roomMemberReader = roomMemberReader;
        this.profileService = profileService;
        this.userService = userService;
        this.blockReader = blockReader;
    }

    /**
     * 채팅 표시 이름. GROUP·PAIR 는 방 세션 가명(없으면 익명 플레이스홀더, 매칭 전 실명 노출 금지 #393·#540),
     * DIRECT 는 실명.
     */
    public String displayName(ChatRoom chatRoom, Long userId) {
        if (chatRoom.type() != ChatRoomType.DIRECT) {
            return roomMemberReader.findByRoomIdAndUserId(chatRoom.roomId(), userId)
                    .map(RoomMember::displayPseudonym)
                    .orElse(RoomMember.ANONYMOUS_NAME);
        }
        return profileService.getNickname(userId);
    }

    /** 1:1 방(DIRECT·PAIR)만 상대 탈퇴·차단을 검사한다(GROUP 은 상대 단수 개념이 없어 통과). */
    public void requireActiveIfOneToOne(Long chatRoomId, Long userId) {
        if (chatRoomReader.getById(chatRoomId).type() != ChatRoomType.GROUP) {
            requireActivePartner(chatRoomId, userId);
        }
    }

    /** 상대가 탈퇴(#255)·차단(#408, 양방향) 상태가 아닌지 검증하고 상대 userId 를 돌려준다. */
    public Long requireActivePartner(Long chatRoomId, Long userId) {
        Long partnerUserId = partnerUserId(chatRoomId, userId);
        if (userService.isWithdrawn(partnerUserId)) {
            throw new ChatPartnerWithdrawnException();
        }
        if (blockReader.isBlockedBetween(userId, partnerUserId)) {
            throw new ChatPartnerBlockedException();
        }
        return partnerUserId;
    }

    public Long partnerUserId(Long chatRoomId, Long userId) {
        return chatRoomMemberReader.findByChatRoomId(chatRoomId).stream()
                .map(ChatRoomMember::userId)
                .filter(id -> !id.equals(userId))
                .findFirst()
                .orElseThrow(ChatNotMemberException::new);
    }
}
