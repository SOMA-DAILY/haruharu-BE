package org.son.haruharu.domain.notification.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.block.implement.BlockReader;
import org.son.haruharu.domain.notification.implement.NotificationType;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.video.business.event.VideoUploadedEvent;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class VideoUploadedEventListenerTest {

    private static final Long ROOM_ID = 7L;

    @Mock RoomMemberReader roomMemberReader;
    @Mock PushSendService pushSendService;
    @Mock BlockReader blockReader;

    VideoUploadedEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new VideoUploadedEventListener(roomMemberReader, pushSendService, blockReader);
    }

    private RoomMember member(Long memberId, Long userId, String pseudonym) {
        return new RoomMember(memberId, ROOM_ID, userId, "MALE", pseudonym,
                LocalDate.of(1999, 1, 1), LocalDateTime.now());
    }

    @Test
    @DisplayName("영상 업로드 커밋 시 같은 방 다른 멤버에게 업로더 가명을 치환 인자로 VIDEO_UPLOADED 푸시를 발송한다")
    void givenUpload_whenHandle_thenSendsToOtherMembersWithPseudonym() {
        given(roomMemberReader.getByRoomId(ROOM_ID)).willReturn(List.of(
                member(10L, 1L, "노을"), member(11L, 2L, "구름"), member(12L, 3L, "바다")));
        given(blockReader.getBlockerUserIds(1L)).willReturn(Set.of());

        listener.handle(new VideoUploadedEvent(100L, ROOM_ID, 10L, LocalDateTime.now()));

        then(pushSendService).should().sendToUsers(
                List.of(2L, 3L), NotificationType.VIDEO_UPLOADED, Map.of("roomId", "7"), "노을");
    }

    @Test
    @DisplayName("업로더를 차단한 수신자는 제외한다(#408) — 전원 차단이면 발송하지 않는다")
    void givenBlockers_whenHandle_thenExcludesThem() {
        given(roomMemberReader.getByRoomId(ROOM_ID)).willReturn(List.of(
                member(10L, 1L, "노을"), member(11L, 2L, "구름"), member(12L, 3L, "바다")));
        given(blockReader.getBlockerUserIds(1L)).willReturn(Set.of(2L));

        listener.handle(new VideoUploadedEvent(100L, ROOM_ID, 10L, LocalDateTime.now()));

        then(pushSendService).should().sendToUsers(
                List.of(3L), NotificationType.VIDEO_UPLOADED, Map.of("roomId", "7"), "노을");
    }

    @Test
    @DisplayName("방에 업로더 혼자면 발송하지 않는다")
    void givenUploaderAlone_whenHandle_thenNoPush() {
        given(roomMemberReader.getByRoomId(ROOM_ID)).willReturn(List.of(member(10L, 1L, "노을")));
        given(blockReader.getBlockerUserIds(1L)).willReturn(Set.of());

        listener.handle(new VideoUploadedEvent(100L, ROOM_ID, 10L, LocalDateTime.now()));

        then(pushSendService).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("이벤트의 roomMemberId가 방 멤버에 없으면(정합성 붕괴) 발송하지 않는다")
    void givenUnknownUploader_whenHandle_thenNoPush() {
        given(roomMemberReader.getByRoomId(ROOM_ID)).willReturn(List.of(member(11L, 2L, "구름")));

        listener.handle(new VideoUploadedEvent(100L, ROOM_ID, 10L, LocalDateTime.now()));

        then(pushSendService).shouldHaveNoInteractions();
    }
}
