package org.son.haruharu.domain.room.business;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.son.haruharu.domain.block.implement.BlockReader;
import org.son.haruharu.domain.room.business.dto.FinalSelectionResponse;
import org.son.haruharu.domain.room.business.dto.MyFinalSelectionResponse;
import org.son.haruharu.domain.room.business.exception.AlreadySelectedException;
import org.son.haruharu.domain.room.business.exception.CannotSelectBlockedException;
import org.son.haruharu.domain.room.business.exception.CannotSelectSameGenderException;
import org.son.haruharu.domain.room.business.exception.CannotSelectSelfException;
import org.son.haruharu.domain.room.business.exception.NotFinalSelectionDayException;
import org.son.haruharu.domain.room.business.exception.SelectionClosedException;
import org.son.haruharu.domain.room.implement.FinalSelection;
import org.son.haruharu.domain.room.implement.FinalSelectionReader;
import org.son.haruharu.domain.room.implement.FinalSelectionWriter;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.domain.room.implement.exception.NotRoomMemberException;
import org.son.haruharu.domain.room.storage.exception.RoomMemberNotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class FinalSelectionServiceTest {

    @Mock RoomReader roomReader;
    @Mock RoomMemberReader roomMemberReader;
    @Mock FinalSelectionReader finalSelectionReader;
    @Mock FinalSelectionWriter finalSelectionWriter;
    @Mock BlockReader blockReader;

    FinalSelectionService service;

    private final Clock clock = Clock.fixed(Instant.parse("2026-06-26T10:00:00Z"), ZoneId.of("Asia/Seoul"));

    private final Long roomId = 10L;
    private final Long userId = 1L;
    private final Long meId = 20L;
    private final Long selectedId = 30L;

    @BeforeEach
    void setUp() {
        service = new FinalSelectionService(roomReader, roomMemberReader, finalSelectionReader,
                finalSelectionWriter, blockReader, clock);
    }

    private RoomMember member(Long id, Long memberUserId, Long memberRoomId) {
        return new RoomMember(id, memberRoomId, memberUserId, "MALE", null, null, LocalDateTime.now());
    }

    private RoomMember female(Long id, Long memberUserId, Long memberRoomId) {
        return new RoomMember(id, memberRoomId, memberUserId, "FEMALE", null, null, LocalDateTime.now());
    }

    // 새 모델(#320·#337): 최종선택 오픈/마감은 어드민이 방마다 입력한 시각으로 판정한다.
    // 기존 시나리오를 보존해 opens=2026-06-26 18:00, closes=2026-06-26 22:00 KST 로 고정한다.
    private static final LocalDateTime SELECTION_OPENS_AT = LocalDateTime.of(2026, 6, 26, 18, 0);
    private static final LocalDateTime SELECTION_CLOSES_AT = LocalDateTime.of(2026, 6, 26, 22, 0);

    private Room room() {
        LocalDateTime startsAt = LocalDateTime.of(2026, 6, 24, 18, 0);
        return new Room(roomId, RoomStatus.ACTIVE, startsAt.toLocalDate(), 3, startsAt, null,
                startsAt, SELECTION_OPENS_AT, SELECTION_CLOSES_AT,
                SELECTION_CLOSES_AT, startsAt.plusDays(3));
    }

    private void stubMeAndRoom() {
        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willReturn(member(meId, userId, roomId));
        given(roomReader.findById(roomId)).willReturn(Optional.of(room()));
    }

    @Test
    @DisplayName("Day3에 최종선택을 제출하면 선택이 저장되고 submitted=true로 응답한다(동시공개라 매칭 판정 없음)")
    void givenDay3_whenSelect_thenSavedAndSubmitted() {
        stubMeAndRoom();
        given(roomMemberReader.getById(selectedId)).willReturn(female(selectedId, 2L, roomId));
        given(finalSelectionReader.hasSelected(meId)).willReturn(false);

        FinalSelectionResponse result = service.select(roomId, selectedId, userId);

        assertThat(result.submitted()).isTrue();
        assertThat(result.selectedMemberId()).isEqualTo(selectedId);
        then(finalSelectionWriter).should().save(any(FinalSelection.class));
    }

    @Test
    @DisplayName("최종선택 오픈(18:00) 전이면 NotFinalSelectionDayException이 발생한다")
    void givenBeforeOpen_whenSelect_thenThrows() {
        // 2026-06-26 17:00 KST — opens_at(18:00) 이전
        FinalSelectionService beforeOpen = serviceWith(
                Clock.fixed(Instant.parse("2026-06-26T08:00:00Z"), ZoneId.of("Asia/Seoul")));
        stubMeAndRoom();

        assertThatThrownBy(() -> beforeOpen.select(roomId, selectedId, userId))
                .isInstanceOf(NotFinalSelectionDayException.class);
        then(finalSelectionWriter).should(never()).save(any());
    }

    private FinalSelectionService serviceWith(Clock fixedClock) {
        return new FinalSelectionService(roomReader, roomMemberReader, finalSelectionReader,
                finalSelectionWriter, blockReader, fixedClock);
    }

    @Test
    @DisplayName("차단 관계인 상대를 최종선택하면 CannotSelectBlockedException이 발생한다(#408)")
    void givenBlockedPair_whenSelect_thenThrows() {
        stubMeAndRoom();
        given(roomMemberReader.getById(selectedId)).willReturn(female(selectedId, 2L, roomId));
        given(blockReader.isBlockedBetween(userId, 2L)).willReturn(true);

        assertThatThrownBy(() -> service.select(roomId, selectedId, userId))
                .isInstanceOf(CannotSelectBlockedException.class);
        then(finalSelectionWriter).should(never()).save(any());
    }

    @Test
    @DisplayName("구 모델 방(opens_at 미설정)은 마지막 날 18:00 fallback으로 판정한다(NPE 없음)")
    void givenLegacyRoomWithoutSchedule_whenSelect_thenUsesFallbackWindow() {
        // 구 모델 방: startsAt·opens_at·closes_at 전부 null. startedAt 2026-06-24 09:00 → Day3=2026-06-26.
        // 고정시각 19:00 KST 는 fallback opens(18:00)~closes(22:00) 사이라 선택 가능해야 한다.
        LocalDateTime startedAt = LocalDateTime.of(2026, 6, 24, 9, 0);
        Room legacy = new Room(roomId, RoomStatus.ACTIVE, LocalDate.of(2026, 6, 24), 3, startedAt, null);
        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willReturn(member(meId, userId, roomId));
        given(roomReader.findById(roomId)).willReturn(Optional.of(legacy));
        given(roomMemberReader.getById(selectedId)).willReturn(female(selectedId, 2L, roomId));
        given(finalSelectionReader.hasSelected(meId)).willReturn(false);

        FinalSelectionResponse result = service.select(roomId, selectedId, userId);

        assertThat(result.submitted()).isTrue();
        assertThat(result.selectedMemberId()).isEqualTo(selectedId);
        then(finalSelectionWriter).should().save(any(FinalSelection.class));
    }

    @Test
    @DisplayName("Day3라도 마지막 슬롯 종료(18:00) 전이면 NotFinalSelectionDayException이 발생한다")
    void givenDay3Before18_whenSelect_thenThrows() {
        // 2026-06-26 17:00 KST — Day3 slot3(15~18) 진행 중
        FinalSelectionService early = serviceWith(
                Clock.fixed(Instant.parse("2026-06-26T08:00:00Z"), ZoneId.of("Asia/Seoul")));
        stubMeAndRoom();

        assertThatThrownBy(() -> early.select(roomId, selectedId, userId))
                .isInstanceOf(NotFinalSelectionDayException.class);
        then(finalSelectionWriter).should(never()).save(any());
    }

    @Test
    @DisplayName("Day3 18:00 정각부터 최종선택이 가능하다")
    void givenDay3At18_whenSelect_thenAllowed() {
        FinalSelectionService atOpen = serviceWith(
                Clock.fixed(Instant.parse("2026-06-26T09:00:00Z"), ZoneId.of("Asia/Seoul")));
        stubMeAndRoom();
        given(roomMemberReader.getById(selectedId)).willReturn(female(selectedId, 2L, roomId));
        given(finalSelectionReader.hasSelected(meId)).willReturn(false);

        FinalSelectionResponse result = atOpen.select(roomId, selectedId, userId);

        assertThat(result.submitted()).isTrue();
        assertThat(result.selectedMemberId()).isEqualTo(selectedId);
        then(finalSelectionWriter).should().save(any(FinalSelection.class));
    }

    @Test
    @DisplayName("Day3 심야(익일 02:00)는 22:00 마감 이후이므로 SelectionClosedException이 발생한다")
    void givenDay3LateNight_whenSelect_thenClosed() {
        // 2026-06-27 02:00 KST — 하루 경계(09:00) 전이라 Day3이지만 22:00 마감을 지남
        FinalSelectionService lateNight = serviceWith(
                Clock.fixed(Instant.parse("2026-06-26T17:00:00Z"), ZoneId.of("Asia/Seoul")));
        stubMeAndRoom();

        assertThatThrownBy(() -> lateNight.select(roomId, selectedId, userId))
                .isInstanceOf(SelectionClosedException.class);
        then(finalSelectionWriter).should(never()).save(any());
    }

    @Test
    @DisplayName("Day3 21:59(마감 직전)까지는 최종선택이 가능하다")
    void givenDay3JustBeforeClose_whenSelect_thenAllowed() {
        // 2026-06-26 21:59 KST
        FinalSelectionService beforeClose = serviceWith(
                Clock.fixed(Instant.parse("2026-06-26T12:59:00Z"), ZoneId.of("Asia/Seoul")));
        stubMeAndRoom();
        given(roomMemberReader.getById(selectedId)).willReturn(female(selectedId, 2L, roomId));
        given(finalSelectionReader.hasSelected(meId)).willReturn(false);

        FinalSelectionResponse result = beforeClose.select(roomId, selectedId, userId);

        assertThat(result.submitted()).isTrue();
        assertThat(result.selectedMemberId()).isEqualTo(selectedId);
        then(finalSelectionWriter).should().save(any(FinalSelection.class));
    }

    @Test
    @DisplayName("Day3 22:00 정각부터는 SelectionClosedException이 발생한다")
    void givenDay3AtClose_whenSelect_thenClosed() {
        // 2026-06-26 22:00 KST — 결과 발표 시각(CONFIRM_HOUR)
        FinalSelectionService atClose = serviceWith(
                Clock.fixed(Instant.parse("2026-06-26T13:00:00Z"), ZoneId.of("Asia/Seoul")));
        stubMeAndRoom();

        assertThatThrownBy(() -> atClose.select(roomId, selectedId, userId))
                .isInstanceOf(SelectionClosedException.class);
        then(finalSelectionWriter).should(never()).save(any());
    }

    @Test
    @DisplayName("22:00 마감 이후에는 기권도 SelectionClosedException이 발생한다")
    void givenAfterClose_whenAbstain_thenClosed() {
        FinalSelectionService atClose = serviceWith(
                Clock.fixed(Instant.parse("2026-06-26T13:00:00Z"), ZoneId.of("Asia/Seoul")));
        stubMeAndRoom();

        assertThatThrownBy(() -> atClose.select(roomId, null, userId))
                .isInstanceOf(SelectionClosedException.class);
        then(finalSelectionWriter).should(never()).save(any());
    }

    @Test
    @DisplayName("자기 자신을 선택하면 CannotSelectSelfException이 발생한다")
    void givenSelfSelection_whenSelect_thenThrows() {
        stubMeAndRoom();

        assertThatThrownBy(() -> service.select(roomId, meId, userId))
                .isInstanceOf(CannotSelectSelfException.class);
    }

    @Test
    @DisplayName("이미 최종선택했으면 AlreadySelectedException이 발생한다(1회 확정)")
    void givenAlreadySelected_whenSelect_thenThrows() {
        stubMeAndRoom();
        given(roomMemberReader.getById(selectedId)).willReturn(female(selectedId, 2L, roomId));
        given(finalSelectionReader.hasSelected(meId)).willReturn(true);

        assertThatThrownBy(() -> service.select(roomId, selectedId, userId))
                .isInstanceOf(AlreadySelectedException.class);
        then(finalSelectionWriter).should(never()).save(any());
    }

    @Test
    @DisplayName("다른 방 멤버를 선택하면 RoomMemberNotFoundException이 발생한다")
    void givenMemberInAnotherRoom_whenSelect_thenThrows() {
        stubMeAndRoom();
        given(roomMemberReader.getById(selectedId)).willReturn(member(selectedId, 2L, 99L));

        assertThatThrownBy(() -> service.select(roomId, selectedId, userId))
                .isInstanceOf(RoomMemberNotFoundException.class);
    }

    @Test
    @DisplayName("기권(selectedMemberId=null)하면 selected_member_id=null로 저장하고 submitted=true로 응답한다")
    void givenAbstain_whenSelect_thenSavedWithNullAndSubmitted() {
        stubMeAndRoom();
        given(finalSelectionReader.hasSelected(meId)).willReturn(false);

        FinalSelectionResponse result = service.select(roomId, null, userId);

        assertThat(result.selectedMemberId()).isNull();
        assertThat(result.submitted()).isTrue();

        ArgumentCaptor<FinalSelection> captor = ArgumentCaptor.forClass(FinalSelection.class);
        then(finalSelectionWriter).should().save(captor.capture());
        assertThat(captor.getValue().selectedMemberId()).isNull();
    }

    @Test
    @DisplayName("이미 최종선택(또는 기권)했으면 기권 요청도 AlreadySelectedException이 발생한다")
    void givenAlreadySelected_whenAbstain_thenThrows() {
        stubMeAndRoom();
        given(finalSelectionReader.hasSelected(meId)).willReturn(true);

        assertThatThrownBy(() -> service.select(roomId, null, userId))
                .isInstanceOf(AlreadySelectedException.class);
        then(finalSelectionWriter).should(never()).save(any());
    }

    @Test
    @DisplayName("최종선택을 제출했으면 조회 시 submitted=true와 선택 대상·제출 시각이 반환된다")
    void givenSubmitted_whenGetMySelection_thenSubmittedTrue() {
        LocalDateTime submittedAt = LocalDateTime.of(2026, 6, 26, 18, 30);
        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willReturn(member(meId, userId, roomId));
        given(finalSelectionReader.findByRoomMemberId(meId))
                .willReturn(Optional.of(new FinalSelection(1L, meId, selectedId, submittedAt)));

        MyFinalSelectionResponse result = service.getMySelection(roomId, userId);

        assertThat(result.submitted()).isTrue();
        assertThat(result.selectedMemberId()).isEqualTo(selectedId);
        assertThat(result.selectedAt()).isEqualTo(submittedAt);
    }

    @Test
    @DisplayName("기권으로 제출했으면 조회 시 submitted=true·selected_member_id=null이 반환된다")
    void givenAbstained_whenGetMySelection_thenSubmittedTrueWithNullTarget() {
        LocalDateTime submittedAt = LocalDateTime.of(2026, 6, 26, 18, 30);
        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willReturn(member(meId, userId, roomId));
        given(finalSelectionReader.findByRoomMemberId(meId))
                .willReturn(Optional.of(new FinalSelection(1L, meId, null, submittedAt)));

        MyFinalSelectionResponse result = service.getMySelection(roomId, userId);

        assertThat(result.submitted()).isTrue();
        assertThat(result.selectedMemberId()).isNull();
        assertThat(result.selectedAt()).isEqualTo(submittedAt);
    }

    @Test
    @DisplayName("아직 제출하지 않았으면 조회 시 예외 없이 submitted=false·전부 null이 반환된다")
    void givenNotSubmitted_whenGetMySelection_thenSubmittedFalse() {
        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willReturn(member(meId, userId, roomId));
        given(finalSelectionReader.findByRoomMemberId(meId)).willReturn(Optional.empty());

        MyFinalSelectionResponse result = service.getMySelection(roomId, userId);

        assertThat(result.submitted()).isFalse();
        assertThat(result.selectedMemberId()).isNull();
        assertThat(result.selectedAt()).isNull();
    }

    @Test
    @DisplayName("같은 방의 동성 멤버를 선택하면 CannotSelectSameGenderException이 발생하고 저장되지 않는다")
    void givenSameGenderTarget_whenSelect_thenThrows() {
        stubMeAndRoom();
        // me(MALE)가 동성(MALE) 멤버를 선택
        given(roomMemberReader.getById(selectedId)).willReturn(member(selectedId, 2L, roomId));

        assertThatThrownBy(() -> service.select(roomId, selectedId, userId))
                .isInstanceOf(CannotSelectSameGenderException.class);
        then(finalSelectionWriter).should(never()).save(any());
    }

    @Test
    @DisplayName("성별 스냅샷이 null인 멤버가 섞여 있으면(구시드 데이터) 동성 검증을 건너뛰고 정상 처리된다")
    void givenNullGenderTarget_whenSelect_thenSkipsGenderCheck() {
        stubMeAndRoom();
        given(roomMemberReader.getById(selectedId))
                .willReturn(new RoomMember(selectedId, roomId, 2L, null, null, null, LocalDateTime.now()));
        given(finalSelectionReader.hasSelected(meId)).willReturn(false);

        FinalSelectionResponse result = service.select(roomId, selectedId, userId);

        assertThat(result.submitted()).isTrue();
        assertThat(result.selectedMemberId()).isEqualTo(selectedId);
        then(finalSelectionWriter).should().save(any(FinalSelection.class));
    }

    @Test
    @DisplayName("방 멤버가 아니면 최종선택 조회 시 NotRoomMemberException이 발생한다")
    void givenNonMember_whenGetMySelection_thenThrows() {
        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willThrow(new NotRoomMemberException());

        assertThatThrownBy(() -> service.getMySelection(roomId, userId))
                .isInstanceOf(NotRoomMemberException.class);
    }

    // ── #320 ③ 새 모델: 어드민이 입력한 시각을 따르고 Day 게이트를 타지 않는다 ──

    /** starts_at=2026-06-24 19:00 · opens=2026-06-26 18:00(= starts 기준 Day2) · closes=21:00 · result=22:00 · ends=익일 09:00 */
    private Room scheduledRoom() {
        LocalDateTime starts = LocalDateTime.of(2026, 6, 24, 19, 0);
        return new Room(roomId, RoomStatus.SELECTING, starts.toLocalDate(), 3, null, null,
                starts,
                LocalDateTime.of(2026, 6, 26, 18, 0),
                LocalDateTime.of(2026, 6, 26, 21, 0),
                LocalDateTime.of(2026, 6, 26, 22, 0),
                LocalDateTime.of(2026, 6, 27, 9, 0));
    }

    private void stubMeAndScheduledRoom() {
        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willReturn(member(meId, userId, roomId));
        given(roomReader.findById(roomId)).willReturn(Optional.of(scheduledRoom()));
    }

    @Test
    @DisplayName("새 모델: 어드민이 지정한 opens_at 이후면 Day가 3이 아니어도 선택할 수 있다")
    void givenScheduledRoomAfterOpensAt_whenSelect_thenAllowed() {
        // 2026-06-26 18:30 KST — starts_at(6/24 19:00) 기준으로는 아직 Day2 다
        FinalSelectionService svc = serviceWith(
                Clock.fixed(Instant.parse("2026-06-26T09:30:00Z"), ZoneId.of("Asia/Seoul")));
        stubMeAndScheduledRoom();
        given(roomMemberReader.getById(selectedId)).willReturn(female(selectedId, 2L, roomId));
        given(finalSelectionReader.hasSelected(meId)).willReturn(false);

        svc.select(roomId, selectedId, userId);

        then(finalSelectionWriter).should().save(any());
    }

    @Test
    @DisplayName("새 모델: opens_at 이전이면 NotFinalSelectionDayException")
    void givenScheduledRoomBeforeOpensAt_whenSelect_thenThrows() {
        // 2026-06-26 17:59 KST
        FinalSelectionService svc = serviceWith(
                Clock.fixed(Instant.parse("2026-06-26T08:59:00Z"), ZoneId.of("Asia/Seoul")));
        stubMeAndScheduledRoom();

        assertThatThrownBy(() -> svc.select(roomId, selectedId, userId))
                .isInstanceOf(NotFinalSelectionDayException.class);
        then(finalSelectionWriter).should(never()).save(any());
    }

    @Test
    @DisplayName("새 모델: closes_at 이후면 SelectionClosedException (구 모델의 22:00 고정이 아니다)")
    void givenScheduledRoomAfterClosesAt_whenSelect_thenClosed() {
        // 2026-06-26 21:01 KST — 어드민이 지정한 마감(21:00) 직후. 구 로직(22:00)이라면 아직 열려 있다.
        FinalSelectionService svc = serviceWith(
                Clock.fixed(Instant.parse("2026-06-26T12:01:00Z"), ZoneId.of("Asia/Seoul")));
        stubMeAndScheduledRoom();

        assertThatThrownBy(() -> svc.select(roomId, selectedId, userId))
                .isInstanceOf(SelectionClosedException.class);
        then(finalSelectionWriter).should(never()).save(any());
    }
}
