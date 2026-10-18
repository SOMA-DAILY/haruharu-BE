package org.son.haruharu.domain.room.storage;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomComposition;
import org.son.haruharu.domain.room.implement.RoomEntryPath;
import org.son.haruharu.domain.room.implement.RoomMember;

import static org.assertj.core.api.Assertions.assertThat;

class RoomEntityTest {

    @Test
    @DisplayName("RoomEntity 왕복 변환은 방 스케줄을 보존한다")
    void givenRoomWithSchedule_whenEntityRoundTrips_thenScheduleIsPreserved() {
        LocalDateTime starts = LocalDateTime.of(2026, 9, 3, 19, 0);
        Room room = Room.createScheduled(starts, starts.plusDays(2), starts.plusDays(2).plusHours(2),
                starts.plusDays(2).plusHours(3), starts.plusDays(3), 3);

        assertThat(RoomEntity.from(room).toDomain().startsAt()).isEqualTo(starts);
    }

    @Test
    @DisplayName("RoomEntity 왕복 변환은 기수·이름·성별 정원을 보존하고, 비어 있으면 NONE 이다(#513)")
    void givenRoomWithComposition_whenEntityRoundTrips_thenCompositionIsPreserved() {
        LocalDateTime starts = LocalDateTime.of(2026, 9, 3, 19, 0);
        RoomComposition composition = RoomComposition.of(4L, "서울 6기 1조", 3, 2);
        Room room = Room.createScheduled(starts, starts.plusDays(2), starts.plusDays(2).plusHours(2),
                starts.plusDays(2).plusHours(3), starts.plusDays(3), 3, composition);
        Room legacy = Room.createScheduled(starts, starts.plusDays(2), starts.plusDays(2).plusHours(2),
                starts.plusDays(2).plusHours(3), starts.plusDays(3), 3);

        assertThat(RoomEntity.from(room).toDomain().composition()).isEqualTo(composition);
        assertThat(RoomEntity.from(legacy).toDomain().composition()).isEqualTo(RoomComposition.NONE);
    }

    @Test
    @DisplayName("RoomMemberEntity 왕복 변환은 입장 경로와 신청 id 를 보존한다(#513)")
    void givenApplicationMember_whenEntityRoundTrips_thenEntryPathIsPreserved() {
        RoomMember member = RoomMember.fromApplication(7L, 901L, "FEMALE", null, 55L, LocalDateTime.of(2026, 9, 3, 19, 0));

        RoomMember restored = RoomMemberEntity.from(member).toDomain();

        assertThat(restored.entryPath()).isEqualTo(RoomEntryPath.APPLICATION);
        assertThat(restored.applicationId()).isEqualTo(55L);
    }

    @Test
    @DisplayName("직접 SQL 로 어긋난 값이 들어간 행도 엔티티 → 도메인 변환에서 예외가 나지 않는다(CHECK 없음, 감사는 verify SQL)")
    void givenCorruptedRow_whenToDomain_thenDoesNotThrow() throws Exception {
        RoomMemberEntity member = RoomMemberEntity.from(RoomMember.of(7L, 901L, null, null, LocalDateTime.of(2026, 9, 3, 19, 0)));
        java.lang.reflect.Field entryPath = RoomMemberEntity.class.getDeclaredField("entryPath");
        entryPath.setAccessible(true);
        entryPath.set(member, RoomEntryPath.APPLICATION); // application_id 는 null 그대로

        assertThat(member.toDomain().entryPath()).isEqualTo(RoomEntryPath.APPLICATION);
        assertThat(member.toDomain().applicationId()).isNull();
    }
}
