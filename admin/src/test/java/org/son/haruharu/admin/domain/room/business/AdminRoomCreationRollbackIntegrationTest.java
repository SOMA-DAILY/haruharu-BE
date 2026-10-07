package org.son.haruharu.admin.domain.room.business;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.admin.domain.room.business.command.CreateAdminRoomCommand;
import org.son.haruharu.domain.room.implement.RoomInviteWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@SpringBootTest
@ActiveProfiles("test")
class AdminRoomCreationRollbackIntegrationTest {

    private static final LocalDateTime STARTS_AT = LocalDateTime.now().plusDays(1).withNano(0);

    @Autowired
    AdminRoomCommandService service;

    @PersistenceContext
    EntityManager entityManager;

    @MockitoBean
    RoomInviteWriter roomInviteWriter;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("초대코드 저장 실패 시 먼저 저장한 방도 롤백된다")
    void givenInviteSaveFailure_whenCreateRoom_thenRollsBackRoom() {
        long roomCount = roomCount();
        given(roomInviteWriter.save(any())).willThrow(new IllegalStateException("invite save failed"));

        assertThatThrownBy(() -> service.createRoomWithInvite(command(), 1L))
                .isInstanceOf(IllegalStateException.class);

        assertThat(roomCount()).isEqualTo(roomCount);
    }


    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("채팅 편성 줄 검증에 실패하면 방과 편성 모두 롤백된다(#457)")
    void givenInvalidChatSchedule_whenCreateRoom_thenRollsBackEverything() {
        long roomCount = roomCount();
        given(roomInviteWriter.save(any())).willAnswer(inv -> inv.getArgument(0));
        CreateAdminRoomCommand base = command();
        CreateAdminRoomCommand withSchedule = new CreateAdminRoomCommand(base.startsAt(), base.finalSelectionOpensAt(),
                base.finalSelectionClosesAt(), base.resultAt(), base.endsAt(), base.totalDays(), base.maxUses(),
                base.inviteExpiresAt(),
                List.of(new org.son.haruharu.domain.mission.common.business.command.RoomMissionCommand(
                        org.son.haruharu.domain.mission.common.implement.MissionType.ANNOUNCEMENT, org.son.haruharu.domain.mission.types.announcement.implement.AnnouncementMissionDefinition.from("ok", List.of()), STARTS_AT, null, null),
                        new org.son.haruharu.domain.mission.common.business.command.RoomMissionCommand(
                                org.son.haruharu.domain.mission.common.implement.MissionType.ANNOUNCEMENT, org.son.haruharu.domain.mission.types.announcement.implement.AnnouncementMissionDefinition.from(null, List.of()), STARTS_AT, null, null)));

        assertThatThrownBy(() -> service.createRoomWithInvite(withSchedule, 1L))
                .isInstanceOf(org.son.haruharu.domain.mission.common.implement.exception.InvalidMissionException.class);

        assertThat(roomCount()).isEqualTo(roomCount);
        assertThat(entityManager.createQuery("select count(i) from RoomMissionEntity i", Long.class)
                .getSingleResult()).isZero();
    }

    private CreateAdminRoomCommand command() {
        return new CreateAdminRoomCommand(
                STARTS_AT,
                STARTS_AT.plusHours(60),
                STARTS_AT.plusHours(63),
                STARTS_AT.plusHours(64),
                STARTS_AT.plusHours(72),
                3,
                6,
                null,
                List.of());
    }


    private long roomCount() {
        return entityManager.createQuery("select count(r) from RoomEntity r", Long.class)
                .getSingleResult();
    }

}
