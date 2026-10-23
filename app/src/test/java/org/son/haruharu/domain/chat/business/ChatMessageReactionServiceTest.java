package org.son.haruharu.domain.chat.business;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.chat.business.dto.MessageReactionResponse;
import org.son.haruharu.domain.chat.business.dto.MessageReactorResponse;
import org.son.haruharu.domain.chat.business.dto.ReactionCountItem;
import org.son.haruharu.domain.chat.business.event.ReactionUpdatedEvent;
import org.son.haruharu.domain.chat.business.exception.CannotReactToOwnMessageException;
import org.son.haruharu.domain.chat.business.exception.ChatMessageNotFoundException;
import org.son.haruharu.domain.chat.business.exception.ChatNotMemberException;
import org.son.haruharu.domain.chat.business.exception.ChatReactionOwnerOnlyException;
import org.son.haruharu.domain.chat.implement.*;
import org.son.haruharu.domain.video.implement.ReactionType;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class ChatMessageReactionServiceTest {
    @Mock ChatRoomLifecyclePolicy lifecycle;

    private static final Long CHAT = 10L;
    private static final Long MSG = 100L;
    private static final Long OWNER = 1L;
    private static final Long ME = 2L;

    @Mock ChatRoomReader chatRoomReader;
    @Mock ChatRoomMemberReader chatRoomMemberReader;
    @Mock ChatMessageReader chatMessageReader;
    @Mock ChatMessageReactionReader reactionReader;
    @Mock ChatMessageReactionWriter reactionWriter;
    @Mock ChatParticipantPolicy participants;
    @Mock ApplicationEventPublisher eventPublisher;

    private final Clock clock = Clock.fixed(Instant.parse("2026-09-29T03:00:00Z"), ZoneOffset.UTC);
    private final ChatRoom group = new ChatRoom(CHAT, ChatRoomType.GROUP, 5L, null, LocalDateTime.now(clock));
    ChatMessageReactionService service;

    @BeforeEach
    void setUp() {
        service = new ChatMessageReactionService(chatRoomReader, chatRoomMemberReader, chatMessageReader,
                reactionReader, reactionWriter, participants, eventPublisher, clock, lifecycle);
    }

    private void memberAndMessage(Long userId) {
        given(chatRoomReader.getById(CHAT)).willReturn(group);
        given(chatRoomMemberReader.findActiveMembership(CHAT, userId))
                .willReturn(Optional.of(new ChatRoomMember(1L, CHAT, userId, LocalDateTime.now(clock), null, null)));
        given(chatMessageReader.findById(MSG))
                .willReturn(Optional.of(new ChatMessage(MSG, CHAT, OWNER, "안녕", LocalDateTime.now(clock))));
    }

    private ChatMessageReaction reaction(String type) {
        return new ChatMessageReaction(7L, MSG, ME, type, LocalDateTime.now(clock));
    }

    @Test
    @DisplayName("처음 누르면 추가되고 개수와 내 반응, REACTION_UPDATED 이벤트가 나간다")
    void givenNoReaction_whenToggle_thenAddsAndPublishes() {
        memberAndMessage(ME);
        given(reactionReader.findMine(MSG, ME)).willReturn(Optional.empty());
        given(reactionWriter.insertIfAbsent(any())).willReturn(true);
        given(reactionReader.countByMessageIds(List.of(MSG)))
                .willReturn(List.of(new MessageReactionCount(MSG, "LAUGH", 3)));

        MessageReactionResponse result = service.toggle(ME, CHAT, MSG, ReactionType.LAUGH);

        assertThat(result.myReaction()).isEqualTo("LAUGH");
        assertThat(result.counts()).containsExactly(new ReactionCountItem("LAUGH", 3));
        then(eventPublisher).should().publishEvent(
                new ReactionUpdatedEvent(CHAT, MSG, List.of(new ReactionCountItem("LAUGH", 3))));
    }

    @Test
    @DisplayName("같은 반응을 다시 누르면 취소(my_reaction=null), 다른 반응이면 교체")
    void givenExisting_whenToggle_thenRemovesOrReplaces() {
        memberAndMessage(ME);
        given(reactionReader.findMine(MSG, ME)).willReturn(Optional.of(reaction("LAUGH")));

        assertThat(service.toggle(ME, CHAT, MSG, ReactionType.LAUGH).myReaction()).isNull();
        then(reactionWriter).should().delete(7L);

        assertThat(service.toggle(ME, CHAT, MSG, ReactionType.FIRE).myReaction()).isEqualTo("FIRE");
        then(reactionWriter).should().save(reaction("FIRE"));
    }

    @Test
    @DisplayName("같은 유저의 동시 요청이 먼저 넣었으면 다시 읽어서 의도(이 타입)대로 맞추고 해제하지 않는다")
    void givenConcurrentInsert_whenToggle_thenKeepsIntendedType() {
        memberAndMessage(ME);
        given(reactionReader.findMine(MSG, ME)).willReturn(Optional.empty(), Optional.of(reaction("LAUGH")));
        given(reactionWriter.insertIfAbsent(any())).willReturn(false);

        assertThat(service.toggle(ME, CHAT, MSG, ReactionType.LAUGH).myReaction()).isEqualTo("LAUGH");
        then(reactionWriter).should(never()).delete(any());
    }

    @Test
    @DisplayName("시스템 안내에는 반응할 수 있지만 일반 유저는 반응자 명단을 조회할 수 없다")
    void givenSystemMessage_whenReactAndGetReactors_thenReactAllowedButOwnerOnly() {
        memberAndMessage(ME);
        given(chatMessageReader.findById(MSG)).willReturn(Optional.of(ChatMessage.system(CHAT, "공지", 5L)));
        given(reactionWriter.insertIfAbsent(any())).willReturn(true);

        assertThat(service.toggle(ME, CHAT, MSG, ReactionType.LIKE).myReaction()).isEqualTo("LIKE");
        assertThatThrownBy(() -> service.getReactors(ME, CHAT, MSG)).isInstanceOf(ChatReactionOwnerOnlyException.class);
        then(reactionReader).should(never()).findByMessageId(any());
    }

    @Test
    @DisplayName("내 메시지에는 반응할 수 없다(403)")
    void givenOwnMessage_whenToggle_thenForbidden() {
        memberAndMessage(OWNER);

        assertThatThrownBy(() -> service.toggle(OWNER, CHAT, MSG, ReactionType.LIKE))
                .isInstanceOf(CannotReactToOwnMessageException.class);
        then(reactionWriter).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("나간 멤버나 멤버가 아니면 403, 다른 방 메시지면 404")
    void givenNotMemberOrOtherRoom_whenToggle_thenRejects() {
        given(chatRoomReader.getById(CHAT)).willReturn(group);
        given(chatRoomMemberReader.findActiveMembership(CHAT, 9L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.toggle(9L, CHAT, MSG, ReactionType.LIKE))
                .isInstanceOf(ChatNotMemberException.class);

        given(chatRoomMemberReader.findActiveMembership(CHAT, ME))
                .willReturn(Optional.of(new ChatRoomMember(1L, CHAT, ME, LocalDateTime.now(clock), null, null)));
        given(chatMessageReader.findById(MSG))
                .willReturn(Optional.of(new ChatMessage(MSG, 77L, OWNER, "x", LocalDateTime.now(clock))));
        assertThatThrownBy(() -> service.toggle(ME, CHAT, MSG, ReactionType.LIKE))
                .isInstanceOf(ChatMessageNotFoundException.class);
    }

    @Test
    @DisplayName("운영자가 지운 메시지에는 반응할 수 없다(400 계열)")
    void givenAdminDeletedMessage_whenToggle_thenRejected() {
        given(chatRoomReader.getById(CHAT)).willReturn(group);
        given(chatRoomMemberReader.findActiveMembership(CHAT, ME))
                .willReturn(Optional.of(new ChatRoomMember(1L, CHAT, ME, LocalDateTime.now(clock), null, null)));
        given(chatMessageReader.findById(MSG)).willReturn(Optional.of(new ChatMessage(MSG, CHAT, null, "공지",
                LocalDateTime.now(clock), null, null, null, null, null, ChatMessageType.TEXT, 5L,
                LocalDateTime.now(clock), null, ChatSenderType.SYSTEM)));

        assertThatThrownBy(() -> service.toggle(ME, CHAT, MSG, ReactionType.LIKE))
                .isInstanceOf(org.son.haruharu.domain.chat.business.exception.CannotReactToDeletedMessageException.class);
        then(reactionWriter).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("DIRECT 방은 상대가 차단/탈퇴면 토글과 조회 모두 막힌다(메시지 목록과 같은 규칙)")
    void givenBlockedDirect_whenToggleOrReactors_thenRejected() {
        ChatRoom direct = new ChatRoom(CHAT, ChatRoomType.DIRECT, null, 99L, LocalDateTime.now(clock));
        given(chatRoomReader.getById(CHAT)).willReturn(direct);
        given(chatRoomMemberReader.findActiveMembership(CHAT, ME))
                .willReturn(Optional.of(new ChatRoomMember(1L, CHAT, ME, LocalDateTime.now(clock), null, null)));
        org.mockito.BDDMockito.willThrow(new org.son.haruharu.domain.chat.business.exception.ChatPartnerBlockedException())
                .given(participants).requireActiveIfOneToOne(CHAT, ME);

        assertThatThrownBy(() -> service.toggle(ME, CHAT, MSG, ReactionType.LIKE))
                .isInstanceOf(org.son.haruharu.domain.chat.business.exception.ChatPartnerBlockedException.class);
        assertThatThrownBy(() -> service.getReactors(ME, CHAT, MSG))
                .isInstanceOf(org.son.haruharu.domain.chat.business.exception.ChatPartnerBlockedException.class);
        then(reactionWriter).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("누가 반응했는지는 메시지 주인만 본다 — 주인 아니면 403, 주인은 표시 이름과 타입")
    void givenOwnerOrNot_whenGetReactors_thenOwnerOnly() {
        memberAndMessage(ME);
        assertThatThrownBy(() -> service.getReactors(ME, CHAT, MSG))
                .isInstanceOf(ChatReactionOwnerOnlyException.class);

        given(chatRoomMemberReader.findActiveMembership(CHAT, OWNER))
                .willReturn(Optional.of(new ChatRoomMember(2L, CHAT, OWNER, LocalDateTime.now(clock), null, null)));
        given(reactionReader.findByMessageId(MSG)).willReturn(List.of(reaction("HEART")));
        given(participants.displayName(group, ME)).willReturn("선우");

        assertThat(service.getReactors(OWNER, CHAT, MSG)).containsExactly(new MessageReactorResponse("선우", "HEART"));
    }
}
