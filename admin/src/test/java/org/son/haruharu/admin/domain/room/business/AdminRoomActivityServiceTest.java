package org.son.haruharu.admin.domain.room.business;

import org.junit.jupiter.api.Test;
import org.son.haruharu.admin.domain.room.implement.AdminRoomActivityReader;
import org.son.haruharu.admin.domain.room.implement.AdminRoomReader;
import org.son.haruharu.admin.domain.room.implement.RoomActivity;
import org.son.haruharu.admin.domain.room.implement.RoomActivityRepository;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.infrastructure.s3.S3StoragePort;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

class AdminRoomActivityServiceTest {
    @Test
    void givenPrivateMedia_whenList_thenReturnsSignedVideoAndThumbnail() {
        var rooms = mock(AdminRoomReader.class);
        var repository = mock(RoomActivityRepository.class);
        var media = mock(S3StoragePort.class);
        var reader = new AdminRoomActivityReader(repository, rooms);
        var service = new AdminRoomActivityService(rooms, reader, media);
        var now = LocalDateTime.of(2026, 9, 1, 19, 0);
        given(rooms.getById(1L)).willReturn(new Room(1L, org.son.haruharu.domain.room.implement.RoomStatus.ACTIVE,
                now.toLocalDate(), 3, now, null));
        given(rooms.membersOf(1L)).willReturn(List.of());
        given(rooms.profilesOf(List.of())).willReturn(List.of());
        var filter = new RoomActivity.Filter(1L, null, null, null, null);
        var video = "https://test-only-activity-bucket.s3.ap-northeast-2.amazonaws.com/video.mp4";
        var thumbnail = "https://test-only-activity-bucket.s3.ap-northeast-2.amazonaws.com/thumbnail.jpg";
        given(repository.find(filter, 0, 24, false)).willReturn(List.of(new RoomActivity("VIDEO", 1L, now, 101L,
                null, null, 1L, video, thumbnail, null, null, null, null, false, null, now, null)));
        given(repository.count(filter)).willReturn(1L);
        given(media.createPresignedGetUrl(video)).willReturn("https://signed.example.test/video");
        given(media.createPresignedImageGetUrl(thumbnail)).willReturn("https://signed.example.test/thumbnail");

        var result = service.getActivities(1L, null, null, null, 0, 24, "desc");
        assertThat(result.content().getFirst().video().videoUrl()).isEqualTo("https://signed.example.test/video");
        assertThat(result.content().getFirst().video().thumbnailUrl()).isEqualTo("https://signed.example.test/thumbnail");
        verify(media).createPresignedGetUrl(video);
        verify(media).createPresignedImageGetUrl(thumbnail);
    }
}
