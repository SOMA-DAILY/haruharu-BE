package org.son.haruharu.domain.chat.business;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.block.implement.BlockReader;
import org.son.haruharu.domain.chat.business.event.VideoReplySentEvent;
import org.son.haruharu.domain.chat.business.exception.ChatNotMemberException;
import org.son.haruharu.domain.chat.business.exception.VideoReplyUnavailableException;
import org.son.haruharu.domain.chat.implement.*;
import org.son.haruharu.domain.chat.implement.exception.VideoReplyInvalidException;
import org.son.haruharu.domain.room.implement.*;
import org.son.haruharu.domain.user.implement.User;
import org.son.haruharu.domain.user.implement.UserReader;
import org.son.haruharu.domain.user.implement.UserStatus;
import org.son.haruharu.domain.video.implement.*;
import org.son.haruharu.infrastructure.s3.S3StoragePort;
import org.springframework.context.ApplicationEventPublisher;

import java.time.*;
import java.util.*;

@ExtendWith(MockitoExtension.class)
class VideoReplyServiceTest {
    @Mock ChatRoomReader rooms;
    @Mock ChatRoomMemberReader members;
    @Mock ChatMessageReader messages;
    @Mock ChatMessageWriter writer;
    @Mock RoomMemberReader roomMembers;
    @Mock RoomReader sourceRooms;
    @Mock VideoPostReader videos;
    @Mock BlockReader blocks;
    @Mock UserReader users;
    @Mock S3StoragePort storage;
    @Mock ApplicationEventPublisher events;
    @Mock Clock clock;
    VideoReplyService service;
    final LocalDateTime now = LocalDateTime.of(2026, 9, 19, 12, 0);
    final String key = "a377e82b-40f3-40aa-af4e-83cf5b32060a";
    VideoPost post =
            new VideoPost(
                    30L,
                    20L,
                    now.minusHours(1),
                    null,
                    "caption",
                    "video",
                    "thumb",
                    false,
                    null,
                    null,
                    now);

    @BeforeEach
    void setUp() {
        // 멤버십 조회는 실제 Reader 규칙(나간 멤버 제외)을 그대로 쓴다.
        lenient()
                .when(members.findActiveMembership(anyLong(), anyLong()))
                .thenCallRealMethod();
        var targets = new VideoReplyTargetReader(videos, roomMembers, members, blocks, users);
        service =
                new VideoReplyService(
                        rooms,
                        members,
                        messages,
                        writer,
                        roomMembers,
                        sourceRooms,
                        targets,
                        storage,
                        events,
                        clock);
    }

    void setupRoom() {
        lenient()
                .when(rooms.getByRoomId(1L))
                .thenReturn(new ChatRoom(10L, ChatRoomType.GROUP, 1L, null, now));
        lenient()
                .when(members.findMembership(10L, 2L))
                .thenReturn(Optional.of(ChatRoomMember.join(10L, 2L, now)));
    }

    void setupTarget() {
        lenient().when(videos.getById(30L)).thenReturn(post);
        lenient()
                .when(roomMembers.getById(20L))
                .thenReturn(new RoomMember(20L, 1L, 3L, "MALE", "도윤", null, now));
        lenient()
                .when(users.getById(3L))
                .thenReturn(new User(3L, UserStatus.ACTIVE, now));
        lenient()
                .when(members.findMembership(10L, 3L))
                .thenReturn(Optional.of(ChatRoomMember.join(10L, 3L, now)));
    }

    void setupSend() {
        setupRoom();
        setupTarget();
        lenient().when(clock.instant()).thenReturn(now.toInstant(ZoneOffset.UTC));
        lenient().when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        lenient()
                .when(sourceRooms.findById(1L))
                .thenReturn(
                        Optional.of(
                                new Room(
                                        1L,
                                        RoomStatus.ACTIVE,
                                        now.toLocalDate(),
                                        3,
                                        now.minusDays(1),
                                        null)));
        lenient()
                .when(roomMembers.getByRoomIdAndUserId(1L, 2L))
                .thenReturn(new RoomMember(21L, 1L, 2L, "MALE", "민준", null, now));
        lenient()
                .when(writer.save(any()))
                .thenAnswer(
                        inv -> {
                            ChatMessage m = inv.getArgument(0);
                            return new ChatMessage(
                                    50L,
                                    m.chatRoomId(),
                                    m.senderId(),
                                    m.content(),
                                    now,
                                    m.videoPostId(),
                                    m.clientRequestId());
                        });
    }

    @Test
    @DisplayName("본인 영상에는 답장할 수 없다")
    void send_rejectsOwnVideo() {
        setupSend();
        when(roomMembers.getById(20L))
                .thenReturn(new RoomMember(20L, 1L, 2L, "MALE", "민준", null, now));
        when(users.getById(2L)).thenReturn(new User(2L, UserStatus.ACTIVE, now));
        assertThatThrownBy(() -> service.send(1L, 30L, 2L, "hello", key))
                .hasMessageContaining("본인");
        verifyNoInteractions(writer);
    }

    @Test
    @DisplayName("차단 관계인 작성자의 영상에는 답장할 수 없다")
    void send_rejectsBlockedOwner() {
        setupSend();
        when(blocks.isBlockedBetween(2L, 3L)).thenReturn(true);
        assertThatThrownBy(() -> service.send(1L, 30L, 2L, "hello", key))
                .isInstanceOf(RuntimeException.class);
        verifyNoInteractions(writer);
    }

    @Test
    @DisplayName("빈 답장은 저장 전에 거부된다")
    void send_rejectsBlankContentBeforeSave() {
        assertThatThrownBy(() -> service.send(1L, 30L, 2L, "  ", key))
                .isInstanceOf(RuntimeException.class);
        verifyNoInteractions(writer);
    }

    @Test
    @DisplayName("삭제된 영상 미리보기는 DELETED이고 미디어 URL을 서명하지 않는다")
    void preview_deletedVideo_returnsDeletedWithoutSigning() {
        setupRoom();
        setupTarget();
        when(videos.getById(30L)).thenReturn(post.deletedAt(now));
        assertThat(service.preview(1L, 30L, 2L).state()).isEqualTo(VideoReplyState.DELETED);
        verifyNoInteractions(storage);
    }

    @Test
    @DisplayName("답장은 한 번 브로드캐스트되고 영상 작성자에게만 알림 이벤트가 발행된다")
    void send_publishesBroadcastAndNotifiesOnlyOwner() {
        setupSend();
        var result = service.send(1L, 30L, 2L, " hello ", key);
        assertThat(result.videoPostId()).isEqualTo(30L);
        assertThat(result.content()).isEqualTo("hello");
        var captor = ArgumentCaptor.forClass(Object.class);
        verify(events, times(2)).publishEvent(captor.capture());
        assertThat(captor.getAllValues())
                .anySatisfy(
                        e -> {
                            assertThat(e).isInstanceOf(VideoReplySentEvent.class);
                            var reply = (VideoReplySentEvent) e;
                            assertThat(reply.ownerId()).isEqualTo(3L);
                            assertThat(reply.senderName()).isEqualTo("민준");
                        });
    }

    @Test
    @DisplayName("대문자 요청 ID도 받고 소문자로 정규화해 저장한다")
    void send_uppercaseRequestId_storesLowercase() {
        setupSend();
        service.send(1L, 30L, 2L, "hello", key.toUpperCase(Locale.ROOT));
        var saved = ArgumentCaptor.forClass(ChatMessage.class);
        verify(writer).save(saved.capture());
        assertThat(saved.getValue().clientRequestId()).isEqualTo(key);
        verify(messages).findByRequest(10L, 2L, key);
    }

    @Test
    @DisplayName("대소문자만 다른 요청 ID로 재시도하면 기존 메시지를 반환한다")
    void send_retryWithDifferentCase_returnsOriginalMessage() {
        setupSend();
        when(messages.findByRequest(10L, 2L, key))
                .thenReturn(
                        Optional.of(
                                new ChatMessage(50L, 10L, 2L, "hello", now, 30L, key)));
        var result = service.send(1L, 30L, 2L, "hello", key.toUpperCase(Locale.ROOT));
        assertThat(result.messageId()).isEqualTo(50L);
        verifyNoInteractions(writer, events);
    }

    @Test
    @DisplayName("표준 UUID 형식이 아니거나 없는 요청 ID는 거부된다")
    void send_rejectsNonCanonicalRequestId() {
        assertThatThrownBy(() -> service.send(1L, 30L, 2L, "hello", "1-1-1-1-1"))
                .isInstanceOf(VideoReplyInvalidException.class);
        assertThatThrownBy(() -> service.send(1L, 30L, 2L, "hello", null))
                .isInstanceOf(VideoReplyInvalidException.class);
        verifyNoInteractions(writer, rooms);
    }

    @Test
    @DisplayName("작성자가 단체 채팅을 나갔으면 보낸 사람의 멤버십 오류가 아닌 답장 불가로 응답한다")
    void send_ownerLeftChat_throwsUnavailable() {
        setupSend();
        when(members.findMembership(10L, 3L))
                .thenReturn(Optional.of(ChatRoomMember.join(10L, 3L, now).leave(now)));
        assertThatThrownBy(() -> service.send(1L, 30L, 2L, "hello", key))
                .isInstanceOf(VideoReplyUnavailableException.class)
                .isNotInstanceOf(ChatNotMemberException.class);
        assertThat(service.preview(1L, 30L, 2L).state()).isEqualTo(VideoReplyState.UNAVAILABLE);
        verifyNoInteractions(writer, storage);
    }

    @Test
    @DisplayName("다른 방의 영상은 답장·미리보기 모두 찾을 수 없다")
    void sendAndPreview_otherRoomVideo_notFound() {
        setupSend();
        when(roomMembers.getById(20L))
                .thenReturn(new RoomMember(20L, 99L, 3L, "MALE", "도윤", null, now));
        assertThatThrownBy(() -> service.send(1L, 30L, 2L, "hello", key))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> service.preview(1L, 30L, 2L)).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(writer, storage);
    }

    @Test
    @DisplayName("탈퇴한 작성자의 영상에는 답장할 수 없다")
    void send_rejectsWithdrawnOwner() {
        setupSend();
        when(users.getById(3L)).thenReturn(new User(3L, UserStatus.DELETED, now));
        assertThatThrownBy(() -> service.send(1L, 30L, 2L, "hello", key))
                .isInstanceOf(RuntimeException.class);
        verifyNoInteractions(writer);
    }

    @Test
    @DisplayName("채팅을 나간 사용자는 답장·미리보기를 할 수 없다")
    void sendAndPreview_senderLeftChat_throwsNotMember() {
        setupRoom();
        when(members.findMembership(10L, 2L))
                .thenReturn(Optional.of(ChatRoomMember.join(10L, 2L, now).leave(now)));
        assertThatThrownBy(() -> service.send(1L, 30L, 2L, "hello", key))
                .isInstanceOf(ChatNotMemberException.class);
        assertThatThrownBy(() -> service.preview(1L, 30L, 2L))
                .isInstanceOf(ChatNotMemberException.class);
        verifyNoInteractions(writer, storage, videos);
    }

    @Test
    @DisplayName("차단 관계인 작성자의 영상 미리보기는 UNAVAILABLE이고 미디어 URL을 서명하지 않는다")
    void preview_blockedOwner_returnsUnavailableWithoutSigning() {
        setupRoom();
        setupTarget();
        when(blocks.isBlockedBetween(2L, 3L)).thenReturn(true);
        assertThat(service.preview(1L, 30L, 2L).state()).isEqualTo(VideoReplyState.UNAVAILABLE);
        verifyNoInteractions(storage);
    }
}
