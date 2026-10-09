package org.son.haruharu.admin.domain.room.business;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.admin.domain.room.business.command.AssignGenderCommand;
import org.son.haruharu.admin.domain.room.business.command.CreateAdminRoomCommand;
import org.son.haruharu.admin.domain.room.business.dto.AdminRoomCreatedResponse;
import org.son.haruharu.admin.domain.room.business.dto.AdminRoomInviteResponse;
import org.son.haruharu.admin.domain.room.business.dto.AdminRoomMemberResponse;
import org.son.haruharu.admin.domain.room.implement.AdminRoomReader;
import org.son.haruharu.admin.domain.room.storage.exception.AdminRoomNotFoundException;
import org.son.haruharu.domain.room.business.RoomInviteService;
import org.son.haruharu.domain.room.business.RoomService;
import org.son.haruharu.domain.room.business.exception.RoomInvalidScheduleException;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomInvite;
import org.son.haruharu.domain.room.implement.RoomInviteStatus;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomMemberWriter;
import org.son.haruharu.domain.room.implement.exception.InvalidRoomMemberGenderException;
import org.son.haruharu.domain.room.storage.exception.RoomMemberNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AdminRoomCommandServiceTest {

    private static final Long ADMIN_ID = 1L;
    private static final Long ROOM_ID = 10L;
    private static final Long MEMBER_ID = 33L;
    private static final LocalDateTime STARTS_AT = LocalDateTime.of(2026, 8, 20, 19, 0);

    @Mock RoomService roomService;
    @Mock RoomInviteService roomInviteService;
    @Mock RoomMemberReader roomMemberReader;
    @Mock RoomMemberWriter roomMemberWriter;
    @Mock AdminRoomReader adminRoomReader;
    @Mock org.son.haruharu.domain.roomschedule.business.RoomScheduleService roomScheduleService;

    AdminRoomCommandService service;

    @BeforeEach
    void setUp() {
        service = new AdminRoomCommandService(roomService, roomInviteService,
                roomMemberReader, roomMemberWriter, adminRoomReader, roomScheduleService);
    }

    private CreateAdminRoomCommand createCommand() {
        return new CreateAdminRoomCommand(
                STARTS_AT,
                STARTS_AT.plusHours(60),
                STARTS_AT.plusHours(63),
                STARTS_AT.plusHours(64),
                STARTS_AT.plusHours(72),
                3, 6, null, List.of());
    }

    private RoomInvite issuedInvite(String code) {
        return new RoomInvite(5L, ROOM_ID, code, RoomInviteStatus.ACTIVE, 6, 0, null, "admin:1");
    }

    private RoomMember memberOfRoom(Long roomId) {
        return new RoomMember(MEMBER_ID, roomId, 901L, null, null, LocalDate.of(2000, 1, 1), STARTS_AT);
    }

    @Test
    @DisplayName("방 생성 시 방과 초대코드가 함께 만들어지고 roomId·code가 반환된다")
    void givenValidSchedule_whenCreateRoomWithInvite_thenReturnsRoomIdAndCode() {
        given(roomService.createRoom(any())).willReturn(ROOM_ID);
        given(roomInviteService.issueFor(eq(ROOM_ID), eq(6), isNull(), eq("admin:1")))
                .willReturn(issuedInvite("K7M2QX"));

        AdminRoomCreatedResponse res = service.createRoomWithInvite(createCommand(), ADMIN_ID);

        assertThat(res.roomId()).isEqualTo(ROOM_ID);
        assertThat(res.code()).isEqualTo("K7M2QX");
        verify(roomService).createRoom(argThat(c ->
                c.startsAt().equals(STARTS_AT) && c.totalDays() == 3));
        verify(roomScheduleService).createAll(ROOM_ID, List.of(), "admin:1");
    }

    @Test
    @DisplayName("스케줄 시각 순서가 어긋나면 RoomInvalidScheduleException이 전파되고 코드는 발급되지 않는다")
    void givenInvalidSchedule_whenCreateRoomWithInvite_thenThrowsAndNoInvite() {
        given(roomService.createRoom(any())).willThrow(new RoomInvalidScheduleException());

        assertThatThrownBy(() -> service.createRoomWithInvite(createCommand(), ADMIN_ID))
                .isInstanceOf(RoomInvalidScheduleException.class);
        verify(roomInviteService, never()).issueFor(any(), any(), any(), any());
    }

    @Test
    @DisplayName("코드 재발급은 방 존재를 확인한 뒤 core의 reissueFor에 위임한다")
    void givenExistingRoom_whenReissueInvite_thenDelegatesToCore() {
        given(roomInviteService.reissueFor(eq(ROOM_ID), eq(6), isNull(), eq("admin:1")))
                .willReturn(issuedInvite("NEWCDE"));

        AdminRoomInviteResponse res = service.reissueInvite(ROOM_ID, 6, null, ADMIN_ID);

        assertThat(res.code()).isEqualTo("NEWCDE");
        assertThat(res.status()).isEqualTo(RoomInviteStatus.ACTIVE);
        verify(adminRoomReader).requireExists(ROOM_ID);
        verify(adminRoomReader, never()).getById(ROOM_ID);
    }

    @Test
    @DisplayName("없는 방에 코드 재발급 시 AdminRoomNotFoundException")
    void givenUnknownRoom_whenReissueInvite_thenNotFound() {
        doThrow(new AdminRoomNotFoundException()).when(adminRoomReader).requireExists(ROOM_ID);

        assertThatThrownBy(() -> service.reissueInvite(ROOM_ID, 6, null, ADMIN_ID))
                .isInstanceOf(AdminRoomNotFoundException.class);
        verify(roomInviteService, never()).reissueFor(any(), any(), any(), any());
    }

    @Test
    @DisplayName("성별 부여 시 gender가 갱신되고 가명 미배정이면 입장 순번대로 가명이 백필된다(#393)")
    void givenMemberWithoutGenderAndPseudonym_whenAssignGender_thenGenderSavedAndPseudonymBackfilled() {
        given(roomMemberReader.getById(MEMBER_ID)).willReturn(memberOfRoom(ROOM_ID));
        // 이미 여성 1명 존재 → 이 멤버는 2번째(index 1) → 여성 풀 "지우"
        given(roomMemberReader.countByRoomIdAndGender(ROOM_ID, "FEMALE")).willReturn(1L);
        given(roomMemberWriter.save(any())).willAnswer(inv -> inv.getArgument(0));

        AdminRoomMemberResponse res = service.assignGender(ROOM_ID, MEMBER_ID, new AssignGenderCommand("FEMALE"));

        verify(roomMemberWriter).save(argThat(m ->
                "FEMALE".equals(m.gender()) && "지우".equals(m.pseudonym())
                        && m.id().equals(MEMBER_ID) && m.userId().equals(901L)));
        // 응답에도 부여된 성별·백필된 가명이 함께 내려간다(#400)
        assertThat(res.gender()).isEqualTo("FEMALE");
        assertThat(res.pseudonym()).isEqualTo("지우");
    }

    @Test
    @DisplayName("이미 가명이 배정된 멤버는 성별 부여 시 가명이 유지되고 순번을 다시 계산하지 않는다(#393)")
    void givenMemberWithPseudonym_whenAssignGender_thenKeepsPseudonym() {
        given(roomMemberReader.getById(MEMBER_ID)).willReturn(
                new RoomMember(MEMBER_ID, ROOM_ID, 901L, null, "현우", null, STARTS_AT));
        given(roomMemberWriter.save(any())).willAnswer(inv -> inv.getArgument(0));

        service.assignGender(ROOM_ID, MEMBER_ID, new AssignGenderCommand("MALE"));

        verify(roomMemberWriter).save(argThat(m -> "현우".equals(m.pseudonym()) && "MALE".equals(m.gender())));
        verify(roomMemberReader, never()).countByRoomIdAndGender(any(), any());
    }

    @Test
    @DisplayName("MALE·FEMALE 이외의 성별 값이면 도메인이 InvalidRoomMemberGenderException")
    void givenInvalidGender_whenAssignGender_thenBadRequest() {
        given(roomMemberReader.getById(MEMBER_ID)).willReturn(memberOfRoom(ROOM_ID));

        assertThatThrownBy(() -> service.assignGender(ROOM_ID, MEMBER_ID, new AssignGenderCommand("OTHER")))
                .isInstanceOf(InvalidRoomMemberGenderException.class);
        verify(roomMemberWriter, never()).save(any());
    }

    @Test
    @DisplayName("성별 값이 null이면 InvalidRoomMemberGenderException")
    void givenNullGender_whenAssignGender_thenBadRequest() {
        given(roomMemberReader.getById(MEMBER_ID)).willReturn(memberOfRoom(ROOM_ID));

        assertThatThrownBy(() -> service.assignGender(ROOM_ID, MEMBER_ID, new AssignGenderCommand(null)))
                .isInstanceOf(InvalidRoomMemberGenderException.class);
        verify(roomMemberWriter, never()).save(any());
    }

    @Test
    @DisplayName("다른 방의 멤버 id로 성별 부여 시 RoomMemberNotFoundException")
    void givenMemberOfAnotherRoom_whenAssignGender_thenNotFound() {
        given(roomMemberReader.getById(MEMBER_ID)).willReturn(memberOfRoom(999L));

        assertThatThrownBy(() -> service.assignGender(ROOM_ID, MEMBER_ID, new AssignGenderCommand("MALE")))
                .isInstanceOf(RoomMemberNotFoundException.class);
        verify(roomMemberWriter, never()).save(any());
    }

    @Test
    @DisplayName("방 취소는 방 존재 확인 후 core RoomService.cancel 에 위임한다")
    void givenExistingRoom_whenCancelRoom_thenDelegatesToCore() {
        service.cancelRoom(ROOM_ID);

        verify(adminRoomReader).requireExists(ROOM_ID);
        verify(adminRoomReader, never()).getById(ROOM_ID);
        verify(roomService).cancel(ROOM_ID);
    }

    @Test
    @DisplayName("없는 방을 취소하면 AdminRoomNotFoundException — core 는 호출되지 않는다")
    void givenUnknownRoom_whenCancelRoom_thenNotFound() {
        doThrow(new AdminRoomNotFoundException()).when(adminRoomReader).requireExists(ROOM_ID);

        assertThatThrownBy(() -> service.cancelRoom(ROOM_ID))
                .isInstanceOf(AdminRoomNotFoundException.class);
        verify(roomService, never()).cancel(any());
    }

    @Test
    @DisplayName("코드 목록 조회는 방 존재 확인 후 해당 방의 코드를 반환한다")
    void givenRoomWithInvites_whenGetInvites_thenReturnsList() {
        given(adminRoomReader.getById(ROOM_ID)).willReturn(room());
        given(roomInviteService.getByRoomId(ROOM_ID)).willReturn(List.of(issuedInvite("K7M2QX")));

        List<AdminRoomInviteResponse> res = service.getInvites(ROOM_ID);

        assertThat(res).hasSize(1);
        assertThat(res.getFirst().code()).isEqualTo("K7M2QX");
        assertThat(res.getFirst().maxUses()).isEqualTo(6);
    }

    private Room room() {
        return Room.createScheduled(STARTS_AT, STARTS_AT.plusDays(2).withHour(18),
                STARTS_AT.plusDays(2).withHour(21), STARTS_AT.plusDays(2).withHour(22),
                STARTS_AT.plusDays(3).withHour(9), 3);
    }
}
