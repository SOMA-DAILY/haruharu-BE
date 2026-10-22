package org.son.haruharu.domain.room.implement;

import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.config.InteractionGuidanceProperties;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InteractionGuidanceWriterTest {
    @Mock RoomReader rooms;
    @Mock RoomMemberReader members;
    @Mock InteractionGuidanceRepository repository;
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-23T00:00:00Z"), ZoneOffset.UTC);
    private final RoomMember member = RoomMember.of(1L, 2L, null, null, LocalDateTime.now(clock));

    @Test
    void givenDisabledFeature_whenSuccessRecorded_thenExistingFlowNeedsNoGuidanceDatabase() {
        var writer = new InteractionGuidanceWriter(new InteractionGuidanceProperties(false, null, 50, "test"),
                rooms, members, repository, clock);
        writer.recordFirstReaction(member);
        writer.recordFirstVideoReply(member);
        verifyNoInteractions(rooms, members, repository);
    }

    @Test
    void givenRoomBeforeEnrollment_whenRecord_thenNoNewTableAccess() {
        LocalDateTime now = LocalDateTime.now(clock);
        when(rooms.findById(1L)).thenReturn(Optional.of(Room.createScheduled(now.minusDays(1), now.plusDays(1),
                now.plusDays(1).plusHours(1), now.plusDays(1).plusHours(2), now.plusDays(2), 3)));
        var writer = new InteractionGuidanceWriter(new InteractionGuidanceProperties(true, now, 50, "test"),
                rooms, members, repository, clock);
        writer.recordFirstReaction(member);
        verifyNoInteractions(members, repository);
    }

    @Test
    void givenSuccessfulFirstActions_whenReRecorded_thenOriginalTimesAreImmutable() {
        LocalDateTime at = LocalDateTime.now(clock);
        var first = new InteractionFirstActions(10L, at.minusHours(2), at.minusHours(1));
        assertThat(first.record(InteractionGuideKind.REACTION, at)).isEqualTo(first);
        assertThat(first.record(InteractionGuideKind.REPLY, at)).isEqualTo(first);
    }
}
