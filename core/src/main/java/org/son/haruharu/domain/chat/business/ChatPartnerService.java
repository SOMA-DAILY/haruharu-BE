package org.son.haruharu.domain.chat.business;

import java.util.List;

import org.son.haruharu.domain.chat.business.dto.PartnerProfileResponse;
import org.son.haruharu.domain.chat.business.dto.PartnerProfileResponse.PartnerVideo;
import org.son.haruharu.domain.chat.business.exception.ChatNotMemberException;
import org.son.haruharu.domain.chat.business.exception.ChatPartnerBlockedException;
import org.son.haruharu.domain.chat.business.exception.ChatPartnerWithdrawnException;
import org.son.haruharu.domain.block.implement.BlockReader;
import org.son.haruharu.domain.chat.implement.ChatRoom;
import org.son.haruharu.domain.chat.implement.ChatRoomMemberReader;
import org.son.haruharu.domain.chat.implement.ChatRoomReader;
import org.son.haruharu.domain.chat.implement.ChatRoomType;
import org.son.haruharu.domain.chat.storage.exception.ChatRoomNotFoundException;
import org.son.haruharu.domain.room.business.MatchResultService;
import org.son.haruharu.domain.room.business.MemberProfileService;
import org.son.haruharu.domain.room.business.dto.MemberProfileResponse;
import org.son.haruharu.domain.room.business.dto.PartnerMemberRef;
import org.son.haruharu.domain.user.business.UserService;
import org.son.haruharu.domain.video.business.VideoService;
import org.son.haruharu.domain.video.implement.VideoPost;
import org.son.haruharu.infrastructure.s3.S3StoragePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 매칭 상대 프로필 카드 집계. 채팅방 멤버십(=매칭쌍) 확인 후, 상대의 프로필·가치관·나이(MemberProfileService,
 * 매칭 시 전항목 공개)와 상대 영상 목록(VideoService, slot_start 오름차순)을 한 응답으로 조립한다.
 * 도메인 간 조율은 각 도메인의 Service를 통해서만 한다(storage 직접 참조 금지).
 */
@Service
@Transactional(readOnly = true)
public class ChatPartnerService {

    private final ChatRoomReader chatRoomReader;
    private final ChatRoomMemberReader chatRoomMemberReader;
    private final MatchResultService matchResultService;
    private final MemberProfileService memberProfileService;
    private final VideoService videoService;
    private final UserService userService;
    private final BlockReader blockReader;
    private final S3StoragePort s3StoragePort;

    public ChatPartnerService(ChatRoomReader chatRoomReader, ChatRoomMemberReader chatRoomMemberReader,
                              MatchResultService matchResultService, MemberProfileService memberProfileService,
                              VideoService videoService, UserService userService, BlockReader blockReader,
                              S3StoragePort s3StoragePort) {
        this.chatRoomReader = chatRoomReader;
        this.chatRoomMemberReader = chatRoomMemberReader;
        this.matchResultService = matchResultService;
        this.memberProfileService = memberProfileService;
        this.videoService = videoService;
        this.userService = userService;
        this.blockReader = blockReader;
        this.s3StoragePort = s3StoragePort;
    }

    public PartnerProfileResponse getPartner(Long chatRoomId, Long userId) {
        ChatRoom chatRoom = chatRoomReader.getById(chatRoomId);
        chatRoomMemberReader.findMembership(chatRoomId, userId)
                .filter(member -> !member.hasLeft())
                .orElseThrow(ChatNotMemberException::new); // 방 참여자(=매칭쌍)만
        if (chatRoom.type() != ChatRoomType.DIRECT) {
            throw new ChatRoomNotFoundException(); // 프로필 카드는 매칭 상대 전용. GROUP·PAIR(#540)에는 매칭 결과가 없다
        }

        PartnerMemberRef partner = matchResultService.resolvePartner(chatRoom.matchResultId(), userId);
        if (userService.isWithdrawn(partner.partnerUserId())) {
            throw new ChatPartnerWithdrawnException(); // 탈퇴 상대의 프로필 카드는 노출하지 않는다(#255)
        }
        if (blockReader.isBlockedBetween(userId, partner.partnerUserId())) {
            throw new ChatPartnerBlockedException(); // 차단 관계의 프로필 카드도 양방향으로 노출하지 않는다(#408)
        }
        MemberProfileResponse profile =
                memberProfileService.getMemberProfile(partner.roomId(), partner.partnerMemberId(), userId);
        List<PartnerVideo> videos = toPartnerVideos(videoService.getMemberVideos(partner.partnerMemberId()));

        return new PartnerProfileResponse(partner.partnerUserId(), profile, videos);
    }

    /**
     * 상대 영상을 slot_start 오름차순 리스트로 변환(getMemberVideos의 조회 정렬 유지).
     * 비공개 영상은 재생 가능한 presigned GET URL로 변환한다(#162).
     */
    private List<PartnerVideo> toPartnerVideos(List<VideoPost> videos) {
        return videos.stream()
                .map(v -> new PartnerVideo(
                        v.id(), v.slotStart(),
                        s3StoragePort.createPresignedGetUrl(v.videoUrl()),
                        s3StoragePort.createPresignedGetUrl(v.thumbnailUrl()),
                        v.caption(), v.createdAt()))
                .toList();
    }
}
