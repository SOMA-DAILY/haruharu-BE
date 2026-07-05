package org.son.haruharu.domain.notification.business;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.notification.implement.NotificationType;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.domain.video.implement.VideoPostReader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class NewVideoNotifierTest {

    private static final LocalDate DAY1 = LocalDate.of(2026, 7, 4);

    @Mock RoomReader roomReader;
    @Mock RoomMemberReader roomMemberReader;
    @Mock VideoPostReader videoPostReader;
    @Mock PushSendService pushSendService;

    NewVideoNotifier notifier;

    @BeforeEach
    void setUp() {
        notifier = new NewVideoNotifier(roomReader, roomMemberReader, videoPostReader, pushSendService);
    }

    private Room activeRoom() {
        return new Room(7L, RoomStatus.ACTIVE, DAY1, 3, DAY1.atTime(9, 0), null);
    }

    private RoomMember member(Long memberId, Long userId) {
        return new RoomMember(memberId, 7L, userId, "MALE", null, DAY1.atTime(0, 0));
    }

    @Test
    @DisplayName("직전 슬롯에 한 명만 올렸으면 업로더를 제외한 나머지 멤버에게 발송한다")
    void givenSingleUploader_whenNotify_thenPushesToOthers() {
        // Day1 12:00 — 직전 슬롯 DAY1_SLOT1(9~12시) 마감. 멤버 3명 중 100L만 업로드.
        LocalDateTime now = DAY1.atTime(12, 0);
        given(roomReader.getActive()).willReturn(List.of(activeRoom()));
        given(roomMemberReader.getByRoomId(7L))
                .willReturn(List.of(member(100L, 1L), member(101L, 2L), member(102L, 3L)));
        given(videoPostReader.hasActiveUpload(100L, 1, 1)).willReturn(true);
        given(videoPostReader.hasActiveUpload(101L, 1, 1)).willReturn(false);
        given(videoPostReader.hasActiveUpload(102L, 1, 1)).willReturn(false);

        int notified = notifier.notifyNewVideos(now);

        assertThat(notified).isEqualTo(2);
        then(pushSendService).should().sendToUsers(
                List.of(2L, 3L), NotificationType.NEW_VIDEO_POSTED, Map.of("roomId", "7"));
    }

    @Test
    @DisplayName("여러 명이 올렸으면 업로더 본인도 다른 새 영상 대상이므로 전원에게 발송한다")
    void givenMultipleUploaders_whenNotify_thenPushesToAll() {
        LocalDateTime now = DAY1.atTime(12, 0);
        given(roomReader.getActive()).willReturn(List.of(activeRoom()));
        given(roomMemberReader.getByRoomId(7L))
                .willReturn(List.of(member(100L, 1L), member(101L, 2L)));
        given(videoPostReader.hasActiveUpload(100L, 1, 1)).willReturn(true);
        given(videoPostReader.hasActiveUpload(101L, 1, 1)).willReturn(true);

        int notified = notifier.notifyNewVideos(now);

        assertThat(notified).isEqualTo(2);
        then(pushSendService).should().sendToUsers(
                List.of(1L, 2L), NotificationType.NEW_VIDEO_POSTED, Map.of("roomId", "7"));
    }

    @Test
    @DisplayName("직전 슬롯에 아무도 올리지 않았으면 발송하지 않는다")
    void givenNoUploader_whenNotify_thenSkips() {
        LocalDateTime now = DAY1.atTime(12, 0);
        given(roomReader.getActive()).willReturn(List.of(activeRoom()));
        given(roomMemberReader.getByRoomId(7L))
                .willReturn(List.of(member(100L, 1L), member(101L, 2L)));
        given(videoPostReader.hasActiveUpload(100L, 1, 1)).willReturn(false);
        given(videoPostReader.hasActiveUpload(101L, 1, 1)).willReturn(false);

        int notified = notifier.notifyNewVideos(now);

        assertThat(notified).isZero();
        then(pushSendService).should(never()).sendToUsers(anyList(), any(), anyMap());
    }

    @Test
    @DisplayName("정시에 닫히는 직전 슬롯이 없으면 아무것도 하지 않는다")
    void givenNoEndedSlot_whenNotify_thenSkips() {
        // 13:00 — endHour==13인 슬롯 없음
        LocalDateTime now = DAY1.atTime(13, 0);
        given(roomReader.getActive()).willReturn(List.of(activeRoom()));

        int notified = notifier.notifyNewVideos(now);

        assertThat(notified).isZero();
        then(pushSendService).should(never()).sendToUsers(anyList(), eq(NotificationType.NEW_VIDEO_POSTED), anyMap());
    }
}
