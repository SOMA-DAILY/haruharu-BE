package org.son.haruharu.domain.notification.business;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.block.implement.BlockReader;
import org.son.haruharu.domain.chat.implement.ChatMessageReader;
import org.son.haruharu.domain.chat.implement.ChatRoomMember;
import org.son.haruharu.domain.chat.implement.ChatRoomMemberReader;
import org.son.haruharu.domain.notification.business.PushBundleService.ChatDecision;
import org.son.haruharu.domain.notification.implement.PushBundle;
import org.son.haruharu.domain.notification.implement.PushBundleRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class PushBundleServiceTest {

    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 14, 20, 0);
    static final String KEY = PushBundle.chatKey(9L);

    @Mock PushBundleRepository pushBundleRepository;
    @Mock ChatRoomMemberReader chatRoomMemberReader;
    @Mock ChatMessageReader chatMessageReader;
    @Mock BlockReader blockReader;

    PushBundleService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.atZone(SEOUL).toInstant(), SEOUL);
        service = new PushBundleService(pushBundleRepository, chatRoomMemberReader, chatMessageReader, blockReader, clock);
    }

    private static ChatRoomMember member(Long userId, Long lastRead) {
        return new ChatRoomMember(userId + 100, 9L, userId, NOW.minusDays(1), null, lastRead);
    }

    @Test
    @DisplayName("울린 메시지까지 읽고 10분이 지난 사람만 다시 울리고, 안 읽은 수는 내 메시지·차단한 상대를 빼고 센다")
    void givenReadCursors_whenDecideGroupChat_thenAlertsOnlyReaders() {
        given(pushBundleRepository.findAllForUpdate(List.of(2L, 3L), KEY)).willReturn(List.of(
                new PushBundle(1L, 2L, KEY, NOW.minusMinutes(15), 50L, List.of()),
                new PushBundle(2L, 3L, KEY, NOW.minusMinutes(15), 50L, List.of())));
        given(chatRoomMemberReader.findByChatRoomId(9L)).willReturn(List.of(member(2L, 50L), member(3L, 40L)));
        given(blockReader.getBlockedUserIds(2L)).willReturn(Set.of());
        given(blockReader.getBlockedUserIds(3L)).willReturn(Set.of(7L));
        given(chatMessageReader.countUnread(9L, 50L, Set.of(2L))).willReturn(1L);
        given(chatMessageReader.countUnread(9L, 40L, Set.of(3L, 7L))).willReturn(11L);

        List<ChatDecision> decisions = service.decideGroupChat(9L, 60L, List.of(2L, 3L));

        assertThat(decisions).containsExactly(new ChatDecision(2L, true, 1), new ChatDecision(3L, false, 11));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<PushBundle>> saved = ArgumentCaptor.forClass(List.class);
        then(pushBundleRepository).should().saveAll(saved.capture());
        assertThat(saved.getValue()).singleElement().satisfies(b -> {
            assertThat(b.id()).isEqualTo(1L);
            assertThat(b.lastAlertedAt()).isEqualTo(NOW);
            assertThat(b.lastAlertedRef()).isEqualTo(60L);
        });
    }

    @Test
    @DisplayName("묶음 상태가 없으면 울리고 새 상태를 저장한다")
    void givenNoBundle_whenDecideGroupChat_thenAlertsAndCreates() {
        given(pushBundleRepository.findAllForUpdate(anyList(), org.mockito.ArgumentMatchers.eq(KEY))).willReturn(List.of());
        given(chatRoomMemberReader.findByChatRoomId(9L)).willReturn(List.of());
        given(blockReader.getBlockedUserIds(2L)).willReturn(Set.of());
        given(chatMessageReader.countUnread(9L, null, Set.of(2L))).willReturn(1L);

        assertThat(service.decideGroupChat(9L, 60L, List.of(2L))).containsExactly(new ChatDecision(2L, true, 1));
        then(pushBundleRepository).should().saveAll(List.of(PushBundle.alertedChat(2L, KEY, NOW, 60L)));
    }
}
