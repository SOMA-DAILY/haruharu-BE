package org.son.haruharu.domain.chat.business;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.block.implement.BlockReader;
import org.son.haruharu.domain.chat.implement.*;
import org.son.haruharu.domain.room.implement.*;
import org.son.haruharu.domain.user.business.UserService;
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
    @Mock UserService users;
    @Mock S3StoragePort storage;
    @Mock ApplicationEventPublisher events;
    @Mock Clock clock;
    @InjectMocks VideoReplyService service;
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

    void setupRoom() {
        when(rooms.getByRoomId(1L))
                .thenReturn(new ChatRoom(10L, ChatRoomType.GROUP, 1L, null, now));
        when(rooms.getById(10L)).thenReturn(new ChatRoom(10L, ChatRoomType.GROUP, 1L, null, now));
        when(members.findMembership(10L, 2L))
                .thenReturn(Optional.of(ChatRoomMember.join(10L, 2L, now)));
    }

    void setupSend() {
        setupRoom();
        when(clock.instant()).thenReturn(now.toInstant(ZoneOffset.UTC));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        when(sourceRooms.findById(1L))
                .thenReturn(
                        Optional.of(
                                new Room(
                                        1L,
                                        RoomStatus.ACTIVE,
                                        now.toLocalDate(),
                                        3,
                                        now.minusDays(1),
                                        null)));
        when(videos.getById(30L)).thenReturn(post);
        when(roomMembers.getById(20L))
                .thenReturn(new RoomMember(20L, 1L, 3L, "MALE", "도윤", null, now));
    }

    @Test
    void ownVideoRejected() {
        setupSend();
        when(roomMembers.getById(20L))
                .thenReturn(new RoomMember(20L, 1L, 2L, "MALE", "민준", null, now));
        assertThatThrownBy(() -> service.send(1L, 30L, 2L, "hello", key))
                .hasMessageContaining("본인");
        verifyNoInteractions(writer);
    }

    @Test
    void blockedAuthorRejected() {
        setupSend();
        when(blocks.isBlockedBetween(2L, 3L)).thenReturn(true);
        assertThatThrownBy(() -> service.send(1L, 30L, 2L, "hello", key))
                .isInstanceOf(RuntimeException.class);
        verifyNoInteractions(writer);
    }

    @Test
    void blankRejectedBeforeSave() {
        assertThatThrownBy(() -> service.send(1L, 30L, 2L, "  ", key))
                .isInstanceOf(RuntimeException.class);
        verifyNoInteractions(writer);
    }

    @Test
    void deletedPreviewNeverSignsMedia() {
        setupRoom();
        when(videos.getById(30L)).thenReturn(post.deletedAt(now));
        when(roomMembers.getById(20L))
                .thenReturn(new RoomMember(20L, 1L, 3L, "MALE", "도윤", null, now));
        assertThat(service.preview(1L, 30L, 2L).state()).isEqualTo("DELETED");
        verifyNoInteractions(storage);
    }

    @Test
    void sameGenderReplyIsPublishedOnceAndOnlyOwnerIsNotified() {
        setupSend();
        when(members.findMembership(10L, 3L))
                .thenReturn(Optional.of(ChatRoomMember.join(10L, 3L, now)));
        when(roomMembers.getByRoomIdAndUserId(1L, 2L))
                .thenReturn(new RoomMember(21L, 1L, 2L, "MALE", "민준", null, now));
        when(writer.save(any()))
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
        var result = service.send(1L, 30L, 2L, " hello ", key);
        assertThat(result.videoPostId()).isEqualTo(30L);
        assertThat(result.content()).isEqualTo("hello");
        var captor = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(events, times(2)).publishEvent(captor.capture());
        assertThat(captor.getAllValues())
                .anySatisfy(
                        e -> {
                            assertThat(e)
                                    .isInstanceOf(
                                            org.son.haruharu.domain.chat.business.event
                                                    .VideoReplySentEvent.class);
                            var reply =
                                    (org.son.haruharu.domain.chat.business.event
                                                    .VideoReplySentEvent)
                                            e;
                            assertThat(reply.ownerId()).isEqualTo(3L);
                            assertThat(reply.senderName()).isEqualTo("민준");
                        });
    }

    @Test
    void crossRoomSendAndPreviewNeverExposeMedia() {
        setupSend();
        when(roomMembers.getById(20L))
                .thenReturn(new RoomMember(20L, 99L, 3L, "MALE", "도윤", null, now));
        assertThatThrownBy(() -> service.send(1L, 30L, 2L, "hello", key))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> service.preview(1L, 30L, 2L)).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(writer, storage);
    }

    @Test
    void withdrawnOwnerCannotReceiveReplies() {
        setupSend();
        when(users.isWithdrawn(3L)).thenReturn(true);
        assertThatThrownBy(() -> service.send(1L, 30L, 2L, "hello", key))
                .isInstanceOf(RuntimeException.class);
        verifyNoInteractions(writer);
    }

    @Test
    void departedSenderCannotSendOrReadPreview() {
        setupRoom();
        when(members.findMembership(10L, 2L))
                .thenReturn(Optional.of(ChatRoomMember.join(10L, 2L, now).leave(now)));
        assertThatThrownBy(() -> service.send(1L, 30L, 2L, "hello", key))
                .isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> service.preview(1L, 30L, 2L)).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(writer, storage, videos);
    }

    @Test
    void blockedPreviewDoesNotSignMedia() {
        setupRoom();
        when(videos.getById(30L)).thenReturn(post);
        when(roomMembers.getById(20L))
                .thenReturn(new RoomMember(20L, 1L, 3L, "MALE", "도윤", null, now));
        when(blocks.isBlockedBetween(2L, 3L)).thenReturn(true);
        assertThat(service.preview(1L, 30L, 2L).state()).isEqualTo("UNAVAILABLE");
        verifyNoInteractions(storage);
    }
}
