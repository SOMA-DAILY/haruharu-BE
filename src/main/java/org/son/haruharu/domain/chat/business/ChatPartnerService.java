package org.son.haruharu.domain.chat.business;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.son.haruharu.domain.chat.business.dto.PartnerProfileResponse;
import org.son.haruharu.domain.chat.business.dto.PartnerProfileResponse.PartnerVideo;
import org.son.haruharu.domain.chat.business.exception.ChatNotMemberException;
import org.son.haruharu.domain.chat.implement.ChatRoom;
import org.son.haruharu.domain.chat.implement.ChatRoomMemberReader;
import org.son.haruharu.domain.chat.implement.ChatRoomReader;
import org.son.haruharu.domain.room.business.MatchResultService;
import org.son.haruharu.domain.room.business.MemberProfileService;
import org.son.haruharu.domain.room.business.dto.MemberProfileResponse;
import org.son.haruharu.domain.room.business.dto.PartnerMemberRef;
import org.son.haruharu.domain.video.business.VideoService;
import org.son.haruharu.domain.video.implement.VideoPost;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 매칭 상대 프로필 카드 집계. 채팅방 멤버십(=매칭쌍) 확인 후, 상대의 프로필·가치관·나이(MemberProfileService,
 * 매칭 시 전항목 공개)와 1~3일차 영상(VideoService)을 한 응답으로 조립한다.
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

    public ChatPartnerService(ChatRoomReader chatRoomReader, ChatRoomMemberReader chatRoomMemberReader,
                              MatchResultService matchResultService, MemberProfileService memberProfileService,
                              VideoService videoService) {
        this.chatRoomReader = chatRoomReader;
        this.chatRoomMemberReader = chatRoomMemberReader;
        this.matchResultService = matchResultService;
        this.memberProfileService = memberProfileService;
        this.videoService = videoService;
    }

    public PartnerProfileResponse getPartner(Long chatRoomId, Long userId) {
        ChatRoom chatRoom = chatRoomReader.getById(chatRoomId);
        chatRoomMemberReader.findMembership(chatRoomId, userId)
                .filter(member -> !member.hasLeft())
                .orElseThrow(ChatNotMemberException::new); // 방 참여자(=매칭쌍)만

        PartnerMemberRef partner = matchResultService.resolvePartner(chatRoom.matchResultId(), userId);
        MemberProfileResponse profile =
                memberProfileService.getMemberProfile(partner.roomId(), partner.partnerMemberId(), userId);
        Map<Integer, List<PartnerVideo>> videosByDay = groupByDay(videoService.getMemberVideos(partner.partnerMemberId()));

        return new PartnerProfileResponse(partner.partnerUserId(), profile, videosByDay);
    }

    /** 상대 영상을 dayNumber 오름차순 맵으로 그룹핑(각 day 내 슬롯 순서는 조회 정렬 유지). */
    private Map<Integer, List<PartnerVideo>> groupByDay(List<VideoPost> videos) {
        return videos.stream().collect(Collectors.groupingBy(
                VideoPost::dayNumber,
                TreeMap::new,
                Collectors.mapping(v -> new PartnerVideo(
                        v.id(), v.slotIndex(), v.videoUrl(), v.thumbnailUrl(), v.caption(), v.createdAt()),
                        Collectors.toList())));
    }
}
