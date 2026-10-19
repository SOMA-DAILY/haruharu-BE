package org.son.haruharu.domain.room.business;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.room.business.event.RoomMemberJoinedEvent;
import org.son.haruharu.domain.room.implement.RoomEntryPath;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomMemberWriter;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RoomMemberJoinerTest {

    private static final Long ROOM_ID = 10L;
    private static final Long USER_ID = 1L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 20, 19, 0);

    @Mock RoomMemberReader roomMemberReader;
    @Mock RoomMemberWriter roomMemberWriter;
    @Mock ApplicationEventPublisher eventPublisher;

    RoomMemberJoiner joiner;

    @BeforeEach
    void setUp() {
        joiner = new RoomMemberJoiner(roomMemberReader, roomMemberWriter, eventPublisher);
        given(roomMemberWriter.save(any())).willAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    @DisplayName("같은 성별 기존 인원을 순번으로 가명을 배정하고, 저장한 뒤 입장 이벤트를 발행한다")
    void givenGenderedNewcomer_whenJoin_thenAssignsPseudonymSavesAndPublishes() {
        given(roomMemberReader.countByRoomIdAndGender(ROOM_ID, "FEMALE")).willReturn(1L);

        RoomMember saved = joiner.join(RoomMember.of(ROOM_ID, USER_ID, "FEMALE", null, NOW));

        assertThat(saved.pseudonym()).isEqualTo("지우"); // 여성 풀 두 번째
        InOrder order = inOrder(roomMemberReader, roomMemberWriter, eventPublisher);
        order.verify(roomMemberReader).countByRoomIdAndGender(ROOM_ID, "FEMALE");
        order.verify(roomMemberWriter).save(argThat(member -> "지우".equals(member.pseudonym())));
        order.verify(eventPublisher).publishEvent(new RoomMemberJoinedEvent(ROOM_ID, USER_ID, NOW));
    }

    @Test
    @DisplayName("성별을 모르면 가명 없이 저장하고 성별 인원도 세지 않는다")
    void givenUnknownGender_whenJoin_thenSavesWithoutPseudonym() {
        RoomMember saved = joiner.join(RoomMember.of(ROOM_ID, USER_ID, null, null, NOW));

        assertThat(saved.pseudonym()).isNull();
        verify(roomMemberReader, never()).countByRoomIdAndGender(any(), any());
        verify(eventPublisher).publishEvent(new RoomMemberJoinedEvent(ROOM_ID, USER_ID, NOW));
    }

    @Test
    @DisplayName("앱 신청 배정 멤버도 같은 경로로 들어가고 입장 경로·신청 id·생년월일이 유지된다")
    void givenApplicationNewcomer_whenJoin_thenKeepsEntryPathAndApplication() {
        given(roomMemberReader.countByRoomIdAndGender(ROOM_ID, "MALE")).willReturn(0L);
        LocalDate birthday = LocalDate.of(2001, 3, 4);

        RoomMember saved = joiner.join(RoomMember.fromApplication(ROOM_ID, USER_ID, "MALE", birthday, 55L, NOW));

        assertThat(saved.entryPath()).isEqualTo(RoomEntryPath.APPLICATION);
        assertThat(saved.applicationId()).isEqualTo(55L);
        assertThat(saved.birthday()).isEqualTo(birthday);
        assertThat(saved.pseudonym()).isEqualTo("민준");
        verify(eventPublisher).publishEvent(new RoomMemberJoinedEvent(ROOM_ID, USER_ID, NOW));
    }
}
