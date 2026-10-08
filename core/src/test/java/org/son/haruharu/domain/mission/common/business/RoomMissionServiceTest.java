package org.son.haruharu.domain.mission.common.business;

import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.mission.chat.business.MissionAnnouncementService;
import org.son.haruharu.domain.mission.chat.implement.MissionAnnouncementReader;
import org.son.haruharu.domain.mission.chat.implement.MissionAnnouncementWriter;
import org.son.haruharu.domain.mission.common.business.command.RoomMissionCommand;
import org.son.haruharu.domain.mission.common.business.exception.MissionNotEditableException;
import org.son.haruharu.domain.mission.common.implement.MissionImage;
import org.son.haruharu.domain.mission.common.implement.MissionImageStore;
import org.son.haruharu.domain.mission.common.implement.MissionStatus;
import org.son.haruharu.domain.mission.common.implement.exception.InvalidMissionException;
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
    final MissionImageStore images = mock(MissionImageStore.class);
    @org.junit.jupiter.api.BeforeEach
    void typeDetails() {
        when(types.get(MissionType.ANNOUNCEMENT)).thenReturn(handler);
    }
    final RoomMissionService service = new RoomMissionService(rooms, reader, writer, announcements,
            announcementWriter, announcementService, members, types, images, clock);

    RoomMission mission(LocalDateTime opensAt) {
        return new RoomMission(10L, 1L, MissionType.ANNOUNCEMENT, new AnnouncementMissionDefinition("질문"), null, opensAt, null, null,
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
                new RoomMissionCommand(MissionType.ANNOUNCEMENT, new AnnouncementMissionDefinition("변경"), now.plusHours(1), null, null), "admin:1"))
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
        assertThat(result.details()).isEqualTo(new AnnouncementMissionDefinition("질문"));
        verify(announcementWriter).cancelScheduledForMission(10L, "admin:2", now);
    }

    static final String IMAGE_KEY = "mission-images/3f2a1b4c-5d6e-4f70-8a9b-0c1d2e3f4a5b-1080x720.jpg";

    @Test
    void givenUploadedImageKey_whenCreate_thenAttachesImageAndReturnsSignedUrl() {
        var image = new MissionImage(IMAGE_KEY, 1080, 720);
        when(images.require(IMAGE_KEY)).thenReturn(image);
        when(images.signedUrl(image)).thenReturn("https://signed");
        when(rooms.findByIdForUpdate(1L)).thenReturn(Optional.of(room));
        when(writer.save(any())).thenAnswer(i -> i.getArgument(0));
        var result = service.create(1L, new RoomMissionCommand(MissionType.ANNOUNCEMENT,
                new AnnouncementMissionDefinition("사진 있는 안내"), IMAGE_KEY, now.plusHours(1), null, null), "admin:1");
        assertThat(result.image().key()).isEqualTo(IMAGE_KEY);
        assertThat(result.image().url()).isEqualTo("https://signed");
        assertThat(result.image().width()).isEqualTo(1080);
        assertThat(result.image().height()).isEqualTo(720);
        verify(writer).save(argThat(saved -> image.equals(saved.image())));
    }

    @Test
    void givenNoImageKey_whenCreateOrReplace_thenMissionHasNoImage() {
        when(rooms.findByIdForUpdate(1L)).thenReturn(Optional.of(room));
        when(writer.save(any())).thenAnswer(i -> i.getArgument(0));
        assertThat(service.create(1L, new RoomMissionCommand(MissionType.ANNOUNCEMENT,
                new AnnouncementMissionDefinition("사진 없는 안내"), now.plusHours(1), null, null), "admin:1").image()).isNull();
        // 전체 교체: 사진이 있던 미션에 image_key 없이 PUT 하면 사진을 뗀다.
        var withImage = new RoomMission(10L, 1L, MissionType.ANNOUNCEMENT, new AnnouncementMissionDefinition("질문"),
                new MissionImage(IMAGE_KEY, 1080, 720), now.plusHours(1), null, null, "admin:1", "admin:1", now, now);
        when(reader.getForUpdateInRoom(1L, 10L)).thenReturn(withImage);
        when(announcements.findByMissionId(10L)).thenReturn(java.util.List.of());
        assertThat(service.update(1L, 10L, new RoomMissionCommand(MissionType.ANNOUNCEMENT,
                new AnnouncementMissionDefinition("질문"), now.plusHours(1), null, null), "admin:2").image()).isNull();
    }

    @Test
    void givenImageKeyThatWasNeverUploaded_whenCreate_thenRejectsBeforeLockingOrSaving() {
        when(images.require("mission-images/unknown")).thenThrow(new InvalidMissionException("image_key", "업로드한 미션 사진을 선택해 주세요"));
        assertThatThrownBy(() -> service.create(1L, new RoomMissionCommand(MissionType.ANNOUNCEMENT,
                new AnnouncementMissionDefinition("안내"), "mission-images/unknown", now.plusHours(1), null, null), "admin:1"))
                .isInstanceOf(InvalidMissionException.class);
        verifyNoInteractions(rooms, writer);
    }

    @Test
    void givenSameImageOnManyInitialMissions_whenCreateAll_thenConfirmsEachKeyOnceAndIndexesErrors() {
        var image = new MissionImage(IMAGE_KEY, 1080, 720);
        String missing = IMAGE_KEY.replace("3f2a", "0000");
        when(images.require(IMAGE_KEY)).thenReturn(image);
        when(images.require(missing)).thenThrow(new InvalidMissionException("image_key", "업로드한 미션 사진을 선택해 주세요"));
        when(rooms.findByIdForUpdate(1L)).thenReturn(Optional.of(room));
        when(writer.save(any())).thenAnswer(i -> i.getArgument(0));
        var withImage = new RoomMissionCommand(MissionType.ANNOUNCEMENT,
                new AnnouncementMissionDefinition("사진 있는 안내"), IMAGE_KEY, now.plusHours(1), null, null);
        var plain = new RoomMissionCommand(MissionType.ANNOUNCEMENT,
                new AnnouncementMissionDefinition("사진 없는 안내"), now.plusHours(1), null, null);
        var created = service.createAll(1L, java.util.List.of(withImage, plain, withImage), "admin:1");
        assertThat(created).extracting(r -> r.image() == null ? null : r.image().key())
                .containsExactly(IMAGE_KEY, null, IMAGE_KEY);
        verify(images, times(1)).require(IMAGE_KEY);

        var broken = new RoomMissionCommand(MissionType.ANNOUNCEMENT,
                new AnnouncementMissionDefinition("없는 사진"), missing, now.plusHours(1), null, null);
        assertThatThrownBy(() -> service.createAll(1L, java.util.List.of(plain, broken), "admin:1"))
                .isInstanceOfSatisfying(InvalidMissionException.class, e ->
                        assertThat(e.getFieldErrors()).extracting(f -> f.field()).containsExactly("missions[1].image_key"));
    }
}
