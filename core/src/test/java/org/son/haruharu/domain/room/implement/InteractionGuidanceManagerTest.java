package org.son.haruharu.domain.room.implement;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.config.InteractionGuidanceProperties;
import org.son.haruharu.domain.chat.implement.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InteractionGuidanceManagerTest {
    @Mock RoomReader rooms;
    @Mock RoomMemberReader members;
    @Mock InteractionGuidanceRepository repository;
    @Mock ChatRoomReader chats;
    @Mock ChatRoomMemberReader chatMembers;
    @Mock VideoReplyTargetReader targets;
    final LocalDateTime starts = LocalDateTime.of(2026, 10, 23, 8, 0);
    final InteractionGuidanceProperties properties = new InteractionGuidanceProperties(true, starts, 100, "test");

    @BeforeEach
    void setup() {
        var room = new Room(1L, RoomStatus.PENDING, starts.toLocalDate(), 3, null, null,
                starts, starts.plusDays(2), starts.plusDays(2).plusHours(1), starts.plusDays(2).plusHours(2), starts.plusDays(3));
        when(members.getByRoomIdAndUserId(1L, 2L))
                .thenReturn(new RoomMember(10L, 1L, 2L, null, null, null, starts));
        when(rooms.findById(1L)).thenReturn(Optional.of(room));
        lenient().when(repository.findAssignment(1L)).thenReturn(Optional.of(
                new InteractionExperimentAssignment(1L, "test", InteractionExperimentVariant.TOOLTIP, starts)));
    }

    InteractionGuidanceManager manager(InteractionGuidanceProperties config) {
        return new InteractionGuidanceManager(config, rooms, members, repository, chats, chatMembers, targets);
    }

    @Test
    void givenThreshold_whenOneNanosecondBeforeAndExactlyOneHour_thenOnlyBoundaryIsEligible() {
        when(repository.findClaims(10L)).thenReturn(List.of());
        when(repository.findFirstActions(10L)).thenReturn(Optional.empty());
        var manager = manager(properties);
        assertThat(manager.get(1L, 2L, starts.plusHours(1).minusNanos(1)).nextGuide()).isNull();
        assertThat(manager.get(1L, 2L, starts.plusHours(1)).nextGuide()).isEqualTo(InteractionGuideKind.REACTION);
        assertThat(manager.get(1L, 2L, starts.plusHours(25)).nextGuide()).isEqualTo(InteractionGuideKind.REACTION);
        assertThat(manager.get(1L, 2L, starts.plusDays(2)).nextGuide()).isNull();
        assertThat(manager.get(1L, 2L, starts.plusDays(3)).nextGuide()).isNull();
    }

    @Test
    void givenDisabledFeature_whenGet_thenNotEnrolledWithoutNewRepositoryAccess() {
        var result = manager(new InteractionGuidanceProperties(false, null, 50, "test")).get(1L, 2L, starts.plusHours(2));
        assertThat(result.variant()).isEqualTo(InteractionExperimentVariant.NOT_ENROLLED);
        assertThat(result.nextGuide()).isNull();
        verifyNoInteractions(repository);
    }

    @Test
    void givenBothClaims_whenRequestedWithOtherKindsSession_thenReasonDoesNotDependOnQueryOrder() {
        var reaction = new InteractionGuideClaim("r", 10L, InteractionGuideKind.REACTION, 30L, "first", starts);
        var reply = new InteractionGuideClaim("p", 10L, InteractionGuideKind.REPLY, 30L, "second", starts.plusHours(1));
        when(repository.findClaims(10L)).thenReturn(List.of(reaction, reply), List.of(reply, reaction));
        var manager = manager(properties);
        assertThat(manager.claim(1L, 2L, InteractionGuideKind.REACTION, 30L, "second", starts.plusHours(2)).reason())
                .isEqualTo("SESSION_ALREADY_CONSUMED");
        assertThat(manager.claim(1L, 2L, InteractionGuideKind.REACTION, 30L, "second", starts.plusHours(2)).reason())
                .isEqualTo("SESSION_ALREADY_CONSUMED");
    }
}
