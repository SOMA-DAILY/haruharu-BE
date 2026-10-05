package org.son.haruharu.domain.video.business;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.room.implement.*;
import org.son.haruharu.domain.video.implement.*;
import org.son.haruharu.domain.video.business.command.VideoUploadCommand;
import org.son.haruharu.domain.video.business.exception.RoomNotActiveException;
import org.son.haruharu.infrastructure.s3.S3StoragePort;
import org.springframework.context.ApplicationEventPublisher;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
class VideoUploadRetryTest {
    @Mock S3StoragePort s3;
    @Mock RoomReader rooms;
    @Mock RoomMemberReader members;
    @Mock VideoPostReader posts;
    @Mock VideoPostWriter writer;
    @Mock ApplicationEventPublisher events;
    LocalDateTime now=LocalDateTime.of(2026,10,4,12,0);
    VideoService service(){return new VideoService(s3,rooms,members,posts,writer,events,
            Clock.fixed(now.atZone(ZoneId.of("Asia/Seoul")).toInstant(),ZoneId.of("Asia/Seoul")));}
    void setup(){
        given(members.getByRoomIdAndUserId(1L,7L)).willReturn(new RoomMember(8L,1L,7L,null,null,null,null));
        given(rooms.findByIdForUpdate(1L)).willReturn(Optional.of(Room.createScheduled(now.minusHours(2),now.plusDays(1),now.plusDays(1).plusHours(1),now.plusDays(1).plusHours(2),now.plusDays(2),3)));
        given(s3.publicBaseUrl()).willReturn("https://media.test/");
    }
    @Test void givenLostResponse_whenRetryUpload_thenReturnOriginalWithoutNewNotification(){
        setup();
        given(posts.findActiveByRoomMember(8L)).willReturn(List.of(new VideoPost(9L,8L,now.minusHours(1),null,null,
                "https://media.test/users/7/videos/job.mp4",null,false,null,null,now.minusHours(1))));
        var result=service().upload(7L,1L,new VideoUploadCommand("https://media.test/users/7/videos/job.mp4",null,null,now.minusHours(1)));
        assertThat(result.videoPostId()).isEqualTo(9L);
        then(writer).shouldHaveNoInteractions();then(events).shouldHaveNoInteractions();
    }
    @Test void givenDelayedJob_whenUpload_thenRejectAnotherSlot(){
        setup();
        assertThatThrownBy(()->service().upload(7L,1L,new VideoUploadCommand("https://media.test/users/7/videos/job.mp4",null,null,now.minusHours(1))))
                .isInstanceOf(RoomNotActiveException.class);
        then(writer).shouldHaveNoInteractions();
    }
}
