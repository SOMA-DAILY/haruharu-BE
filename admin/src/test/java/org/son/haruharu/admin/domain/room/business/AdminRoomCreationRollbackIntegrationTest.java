package org.son.haruharu.admin.domain.room.business;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.son.haruharu.admin.domain.room.business.command.CreateAdminRoomCommand;
import org.son.haruharu.domain.mission.business.command.CreateRoomMissionSlotCommand;
import org.son.haruharu.domain.mission.implement.MissionPeriod;
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

    private static final LocalDateTime STARTS_AT = LocalDateTime.of(2026, 9, 1, 9, 0);

    @Autowired
    AdminRoomCommandService service;

    @PersistenceContext
    EntityManager entityManager;

    @MockitoBean
    RoomInviteWriter roomInviteWriter;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("초대코드 저장 실패 시 먼저 저장한 방과 미션 슬롯도 모두 롤백된다")
    void givenInviteSaveFailure_whenCreateRoom_thenRollsBackRoomAndMissionSlots() {
        long roomCount = roomCount();
        long slotCount = roomMissionSlotCount();
        given(roomInviteWriter.save(any())).willThrow(new IllegalStateException("invite save failed"));

        assertThatThrownBy(() -> service.createRoomWithInvite(command(), 1L))
                .isInstanceOf(IllegalStateException.class);

        assertThat(roomCount()).isEqualTo(roomCount);
        assertThat(roomMissionSlotCount()).isEqualTo(slotCount);
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
                List.of(
                        slot(1, MissionPeriod.MORNING, 1, 6),
                        slot(1, MissionPeriod.AFTERNOON, 9, 14),
                        slot(2, MissionPeriod.MORNING, 25, 30),
                        slot(2, MissionPeriod.AFTERNOON, 33, 38),
                        slot(3, MissionPeriod.MORNING, 49, 54),
                        slot(3, MissionPeriod.AFTERNOON, 55, 58)));
    }

    private CreateRoomMissionSlotCommand slot(int dayNumber, MissionPeriod period,
                                               int opensOffset, int closesOffset) {
        return new CreateRoomMissionSlotCommand(dayNumber, period,
                STARTS_AT.plusHours(opensOffset), STARTS_AT.plusHours(closesOffset));
    }

    private long roomCount() {
        return entityManager.createQuery("select count(r) from RoomEntity r", Long.class)
                .getSingleResult();
    }

    private long roomMissionSlotCount() {
        return entityManager.createQuery("select count(s) from RoomMissionSlotEntity s", Long.class)
                .getSingleResult();
    }
}
