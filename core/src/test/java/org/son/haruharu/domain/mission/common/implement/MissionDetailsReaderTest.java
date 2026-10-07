package org.son.haruharu.domain.mission.common.implement;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.mission.types.announcement.implement.AnnouncementMissionDefinition;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MissionDetailsReaderTest {
    final MissionTypeHandler handler = mock(MissionTypeHandler.class);
    final MissionDefinition definition = AnnouncementMissionDefinition.from("질문", List.of());

    MissionDetailsReader reader() {
        when(handler.type()).thenReturn(MissionType.ANNOUNCEMENT);
        return new MissionDetailsReader(new MissionTypeRegistry(List.of(handler)));
    }

    RoomMission mission(long id) {
        return new RoomMission(id, 1L, MissionType.ANNOUNCEMENT, LocalDateTime.now(), null, null,
                "admin:1", "admin:1", null, null);
    }

    @Test
    void givenFullPage_whenReadingDetails_thenLoadsTypeOnlyOnce() {
        var ids = LongStream.rangeClosed(1, 100).boxed().toList();
        when(handler.loadDefinitions(ids)).thenReturn(ids.stream().collect(Collectors.toMap(id -> id, id -> definition)));
        assertThat(reader().findAll(ids.stream().map(this::mission).toList())).hasSize(100);
        verify(handler, times(1)).loadDefinitions(ids);
    }

    @Test
    void givenMissingSubtype_whenReading_thenRejectsIncompleteResponse() {
        when(handler.loadDefinitions(List.of(1L))).thenReturn(Map.of());
        assertThatThrownBy(() -> reader().get(mission(1)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("missionId=1");
    }
}
