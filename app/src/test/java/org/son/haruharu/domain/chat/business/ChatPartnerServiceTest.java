package org.son.haruharu.domain.chat.business;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.chat.business.dto.PartnerProfileResponse;
import org.son.haruharu.domain.chat.business.exception.ChatNotMemberException;
import org.son.haruharu.domain.chat.business.exception.ChatPartnerWithdrawnException;
import org.son.haruharu.domain.chat.implement.ChatRoom;
import org.son.haruharu.domain.chat.implement.ChatRoomType;
import org.son.haruharu.domain.chat.implement.ChatRoomMember;
import org.son.haruharu.domain.chat.implement.ChatRoomMemberReader;
import org.son.haruharu.domain.chat.implement.ChatRoomReader;
import org.son.haruharu.domain.room.business.MatchResultService;
import org.son.haruharu.domain.room.business.MemberProfileService;
import org.son.haruharu.domain.room.business.dto.MemberProfileResponse;
import org.son.haruharu.domain.room.business.dto.PartnerMemberRef;
import org.son.haruharu.domain.user.business.UserService;
import org.son.haruharu.domain.video.business.VideoService;
import org.son.haruharu.domain.video.implement.VideoPost;
import org.son.haruharu.infrastructure.s3.S3StoragePort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ChatPartnerServiceTest {

    @Mock ChatRoomReader chatRoomReader;
    @Mock ChatRoomMemberReader chatRoomMemberReader;
    @Mock MatchResultService matchResultService;
    @Mock MemberProfileService memberProfileService;
    @Mock VideoService videoService;
    @Mock UserService userService;
    @Mock S3StoragePort s3StoragePort;

    ChatPartnerService service;

    private static final Long CHAT_ROOM = 77L;
    private static final Long ME = 1L;
    private static final Long MATCH = 500L;
    private static final Long ROOM = 10L;
    private static final Long PARTNER_MEMBER = 30L;
    private static final Long PARTNER_USER = 2L;

    @BeforeEach
    void setUp() {
        service = new ChatPartnerService(chatRoomReader, chatRoomMemberReader,
                matchResultService, memberProfileService, videoService, userService, s3StoragePort);
        // 비공개 영상 URL은 presigned GET URL로 서명해 응답한다(#162). 서명 적용 관찰용 접두사.
        lenient().when(s3StoragePort.createPresignedGetUrl(any()))
                .thenAnswer(inv -> inv.getArgument(0) == null ? null : "signed:" + inv.getArgument(0));
    }

    private ChatRoomMember membership(Long userId, LocalDateTime leftAt) {
        return new ChatRoomMember(CHAT_ROOM * 100, CHAT_ROOM, userId, LocalDateTime.now(), leftAt, null);
    }

    private VideoPost video(Long id, int day, int slot) {
        // slotStart는 day·slot로부터 유일하게 파생(조회 정렬 확인용, slot_start 충돌 방지)
        return new VideoPost(id, PARTNER_MEMBER, LocalDateTime.of(2026, 6, 23 + day, 8 + slot, 0), null, "cap" + id,
                "https://cdn/v" + id + ".mp4", "https://cdn/t" + id + ".jpg", false, null, null,
                LocalDateTime.of(2026, 6, 24, 10, 0));
    }

    @Test
    @DisplayName("매칭 상대 카드는 프로필 + 영상(조회 순서 유지)을 slot_start 리스트로 조립해 반환한다")
    void givenMatchedPartner_whenGetPartner_thenAssemblesProfileAndVideos() {
        given(chatRoomReader.getById(CHAT_ROOM)).willReturn(new ChatRoom(CHAT_ROOM, ChatRoomType.DIRECT, null, MATCH, LocalDateTime.now()));
        given(chatRoomMemberReader.findMembership(CHAT_ROOM, ME)).willReturn(Optional.of(membership(ME, null)));
        given(matchResultService.resolvePartner(MATCH, ME))
                .willReturn(new PartnerMemberRef(ROOM, PARTNER_MEMBER, PARTNER_USER));
        MemberProfileResponse profile = new MemberProfileResponse(
                PARTNER_MEMBER, "이나경", "FEMALE", "https://cdn/rep.jpg", true, List.of(), List.of());
        given(memberProfileService.getMemberProfile(ROOM, PARTNER_MEMBER, ME)).willReturn(profile);
        // getMemberVideos는 slot_start 오름차순으로 반환(스토리지 정렬). 서비스는 그 순서를 그대로 리스트로 노출한다.
        given(videoService.getMemberVideos(PARTNER_MEMBER)).willReturn(List.of(
                video(11L, 1, 1), video(12L, 1, 2), video(20L, 2, 1), video(30L, 3, 3)));

        PartnerProfileResponse result = service.getPartner(CHAT_ROOM, ME);

        assertThat(result.userId()).isEqualTo(PARTNER_USER);
        assertThat(result.profile()).isSameAs(profile);
        assertThat(result.videos()).extracting(PartnerProfileResponse.PartnerVideo::videoPostId)
                .containsExactly(11L, 12L, 20L, 30L);
        assertThat(result.videos().get(0).videoUrl()).isEqualTo("signed:https://cdn/v11.mp4");
        assertThat(result.videos().get(0).slotStart()).isEqualTo(LocalDateTime.of(2026, 6, 24, 9, 0));
        assertThat(result.videos().get(3).slotStart()).isEqualTo(LocalDateTime.of(2026, 6, 26, 11, 0));
    }

    @Test
    @DisplayName("영상이 없는 상대는 videos가 빈 리스트로 반환된다")
    void givenNoVideos_whenGetPartner_thenEmptyList() {
        given(chatRoomReader.getById(CHAT_ROOM)).willReturn(new ChatRoom(CHAT_ROOM, ChatRoomType.DIRECT, null, MATCH, LocalDateTime.now()));
        given(chatRoomMemberReader.findMembership(CHAT_ROOM, ME)).willReturn(Optional.of(membership(ME, null)));
        given(matchResultService.resolvePartner(MATCH, ME))
                .willReturn(new PartnerMemberRef(ROOM, PARTNER_MEMBER, PARTNER_USER));
        given(memberProfileService.getMemberProfile(ROOM, PARTNER_MEMBER, ME)).willReturn(
                new MemberProfileResponse(PARTNER_MEMBER, "이나경", "FEMALE", null, true, List.of(), List.of()));
        given(videoService.getMemberVideos(PARTNER_MEMBER)).willReturn(List.of());

        PartnerProfileResponse result = service.getPartner(CHAT_ROOM, ME);

        assertThat(result.videos()).isEmpty();
    }

    @Test
    @DisplayName("채팅방 참여자가 아니면 ChatNotMemberException(403)이 발생하고 상대 해석·조회를 하지 않는다")
    void givenNotMember_whenGetPartner_thenThrows() {
        given(chatRoomReader.getById(CHAT_ROOM)).willReturn(new ChatRoom(CHAT_ROOM, ChatRoomType.DIRECT, null, MATCH, LocalDateTime.now()));
        given(chatRoomMemberReader.findMembership(CHAT_ROOM, ME)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPartner(CHAT_ROOM, ME))
                .isInstanceOf(ChatNotMemberException.class);
        then(matchResultService).should(never()).resolvePartner(anyLong(), anyLong());
        then(videoService).should(never()).getMemberVideos(any());
    }

    @Test
    @DisplayName("방을 나간 멤버가 조회 시 ChatNotMemberException(403)이 발생한다")
    void givenLeftMember_whenGetPartner_thenThrows() {
        given(chatRoomReader.getById(CHAT_ROOM)).willReturn(new ChatRoom(CHAT_ROOM, ChatRoomType.DIRECT, null, MATCH, LocalDateTime.now()));
        given(chatRoomMemberReader.findMembership(CHAT_ROOM, ME))
                .willReturn(Optional.of(membership(ME, LocalDateTime.now())));

        assertThatThrownBy(() -> service.getPartner(CHAT_ROOM, ME))
                .isInstanceOf(ChatNotMemberException.class);
    }

    @Test
    @DisplayName("상대가 탈퇴했으면 프로필 카드를 조회할 수 없다")
    void givenWithdrawnPartner_whenGetPartner_thenThrowsForbidden() {
        given(chatRoomReader.getById(CHAT_ROOM)).willReturn(new ChatRoom(CHAT_ROOM, ChatRoomType.DIRECT, null, MATCH, LocalDateTime.now()));
        given(chatRoomMemberReader.findMembership(CHAT_ROOM, ME))
                .willReturn(Optional.of(membership(ME, null)));
        given(matchResultService.resolvePartner(MATCH, ME))
                .willReturn(new PartnerMemberRef(ROOM, PARTNER_MEMBER, PARTNER_USER));
        given(userService.isWithdrawn(PARTNER_USER)).willReturn(true);

        assertThatThrownBy(() -> service.getPartner(CHAT_ROOM, ME))
                .isInstanceOf(ChatPartnerWithdrawnException.class);
        then(memberProfileService).should(never()).getMemberProfile(anyLong(), anyLong(), anyLong());
        then(videoService).should(never()).getMemberVideos(any());
    }
}
