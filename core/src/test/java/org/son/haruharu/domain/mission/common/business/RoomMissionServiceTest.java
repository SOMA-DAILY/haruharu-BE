package org.son.haruharu.domain.mission.common.business;

import java.time.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.mission.chat.business.MissionAnnouncementService;
import org.son.haruharu.domain.mission.chat.implement.MissionAnnouncementReader;
import org.son.haruharu.domain.mission.chat.implement.MissionAnnouncementWriter;
import org.son.haruharu.domain.mission.common.business.command.RoomMissionCommand;
import org.son.haruharu.domain.mission.common.business.exception.MissionNotEditableException;
import org.son.haruharu.domain.mission.common.implement.MissionDetailsReader;
import org.son.haruharu.domain.mission.common.implement.MissionStatus;
import org.son.haruharu.domain.mission.common.implement.MissionType;
import org.son.haruharu.domain.mission.common.implement.MissionTypeHandler;
import org.son.haruharu.domain.mission.common.implement.MissionTypeRegistry;
import org.son.haruharu.domain.mission.common.implement.RoomMission;
import org.son.haruharu.domain.mission.common.implement.RoomMissionReader;
import org.son.haruharu.domain.mission.common.implement.RoomMissionWriter;
import org.son.haruharu.domain.mission.common.storage.exception.MissionNotFoundException;
import org.son.haruharu.domain.mission.types.announcement.implement.AnnouncementMissionDefinition;
import org.son.haruharu.domain.room.implement.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RoomMissionServiceTest {
    final Clock clock = Clock.fixed(Instant.parse("2026-10-07T12:00:00Z"), ZoneOffset.UTC);
    final LocalDateTime now = LocalDateTime.now(clock);
    final Room room = Room.createScheduled(now.minusHours(1), now.plusDays(2), now.plusDays(2).plusHours(1),
            now.plusDays(2).plusHours(2), now.plusDays(3), 3);
    final RoomReader rooms = mock(RoomReader.class);
    final RoomMissionReader reader = mock(RoomMissionReader.class);
    final RoomMissionWriter writer = mock(RoomMissionWriter.class);
    final MissionAnnouncementReader announcements = mock(MissionAnnouncementReader.class);
    final MissionAnnouncementWriter announcementWriter = mock(MissionAnnouncementWriter.class);
    final MissionAnnouncementService announcementService = mock(MissionAnnouncementService.class);
    final RoomMemberReader members = mock(RoomMemberReader.class);
    final MissionTypeHandler handler = mock(MissionTypeHandler.class);
    final MissionTypeRegistry types = mock(MissionTypeRegistry.class);
    final MissionDetailsReader details = mock(MissionDetailsReader.class);
    @org.junit.jupiter.api.BeforeEach
    void typeDetails() {
        when(types.get(MissionType.ANNOUNCEMENT)).thenReturn(handler);
        when(details.get(any())).thenReturn(AnnouncementMissionDefinition.from("질문", List.of()));
    }
    final RoomMissionService service = new RoomMissionService(rooms, reader, writer, announcements,
            announcementWriter, announcementService, members, types, details, clock);

    RoomMission mission(LocalDateTime opensAt) {
        return new RoomMission(10L, 1L, MissionType.ANNOUNCEMENT, opensAt, null, null,
                "admin:1", "admin:1", now.minusDays(1), now.minusDays(1));
    }

    @Test
    void givenFutureMission_whenParticipantReads_thenHidden() {
        when(rooms.findById(1L)).thenReturn(Optional.of(room));
        when(reader.getInRoom(1L, 10L)).thenReturn(mission(now.plusSeconds(1)));
        doThrow(new MissionNotFoundException()).when(reader).requireVisible(any(), any(), any());
        assertThatThrownBy(() -> service.getForParticipant(1L, 10L, 2L)).isInstanceOf(MissionNotFoundException.class);
        verify(members).getByRoomIdAndUserId(1L, 2L);
    }

    @Test
    void givenOpenMissionWithoutChat_whenParticipantReads_thenVisibleWithoutResponse() {
        when(rooms.findById(1L)).thenReturn(Optional.of(room));
        when(reader.getInRoom(1L, 10L)).thenReturn(mission(now));
        var result = service.getForParticipant(1L, 10L, 2L);
        assertThat(result.status()).isEqualTo(MissionStatus.OPEN);
        assertThat(result.canRespond()).isFalse();
        assertThat(result.requiresResponse()).isFalse();
        verifyNoInteractions(writer, announcementWriter, announcementService);
    }

    @Test
    void givenStartedMission_whenUpdate_thenImmutable() {
        when(rooms.findByIdForUpdate(1L)).thenReturn(Optional.of(room));
        when(reader.getForUpdateInRoom(1L, 10L)).thenReturn(mission(now));
        assertThatThrownBy(() -> service.update(1L, 10L,
                new RoomMissionCommand(MissionType.ANNOUNCEMENT, AnnouncementMissionDefinition.from("변경", List.of()), now.plusHours(1), null, null), "admin:1"))
                .isInstanceOf(MissionNotEditableException.class);
        verifyNoInteractions(writer);
    }

    @Test
    void givenOpenMission_whenCancel_thenCancelPendingAnnouncementsAndKeepRecord() {
        when(rooms.findByIdForUpdate(1L)).thenReturn(Optional.of(room));
        when(reader.getForUpdateInRoom(1L, 10L)).thenReturn(mission(now));
        when(writer.save(any())).thenAnswer(i -> i.getArgument(0));
        var result = service.cancel(1L, 10L, "admin:2");
        assertThat(result.status()).isEqualTo(MissionStatus.CANCELED);
        assertThat(result.details()).isEqualTo(AnnouncementMissionDefinition.from("질문", List.of()));
        verify(announcementWriter).cancelScheduledForMission(10L, "admin:2", now);
    }
}
