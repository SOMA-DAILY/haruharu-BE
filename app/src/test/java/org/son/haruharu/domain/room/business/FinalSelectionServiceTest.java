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
import org.son.haruharu.domain.room.business.dto.FinalSelectionResponse;
import org.son.haruharu.domain.room.business.dto.MyFinalSelectionResponse;
import org.son.haruharu.domain.room.business.exception.AlreadySelectedException;
import org.son.haruharu.domain.room.business.exception.CannotSelectSelfException;
import org.son.haruharu.domain.room.business.exception.NotFinalSelectionDayException;
import org.son.haruharu.domain.room.business.exception.SelectionClosedException;
import org.son.haruharu.domain.room.implement.FinalSelection;
import org.son.haruharu.domain.room.implement.FinalSelectionReader;
import org.son.haruharu.domain.room.implement.FinalSelectionWriter;
import org.son.haruharu.domain.room.implement.MatchResult;
import org.son.haruharu.domain.room.implement.MatchResultWriter;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.business.event.MemberSelectedEvent;
import org.son.haruharu.domain.room.business.event.MutualMatchEvent;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.domain.room.implement.exception.NotRoomMemberException;
import org.son.haruharu.domain.room.storage.exception.RoomMemberNotFoundException;
import org.springframework.context.ApplicationEventPublisher;

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
    @Mock MatchResultWriter matchResultWriter;
    @Mock ApplicationEventPublisher eventPublisher;

    FinalSelectionService service;

    private final Clock clock = Clock.fixed(Instant.parse("2026-06-26T10:00:00Z"), ZoneId.of("Asia/Seoul"));

    private final Long roomId = 10L;
    private final Long userId = 1L;
    private final Long meId = 20L;
    private final Long selectedId = 30L;

    @BeforeEach
    void setUp() {
        service = new FinalSelectionService(roomReader, roomMemberReader, finalSelectionReader,
                finalSelectionWriter, matchResultWriter, eventPublisher, clock);
    }

    private RoomMember member(Long id, Long memberUserId, Long memberRoomId) {
        return new RoomMember(id, memberRoomId, memberUserId, "MALE", null, LocalDateTime.now());
    }

    // currentDay는 startedAt에서 파생된다. clock=2026-06-26 19:00(KST) 기준 방날짜에서 (currentDay-1)일 전 09:00 앵커.
    private Room room(int currentDay) {
        LocalDateTime startedAt = LocalDateTime.of(2026, 6, 26, 9, 0).minusDays(currentDay - 1L);
        return new Room(roomId, RoomStatus.ACTIVE, LocalDate.of(2026, 6, 24), 3, startedAt, null);
    }

    private void stubMeAndRoom(int currentDay) {
        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willReturn(member(meId, userId, roomId));
        given(roomReader.findById(roomId)).willReturn(Optional.of(room(currentDay)));
    }

    @Test
    @DisplayName("Day3에 상대가 아직 나를 선택하지 않았으면 저장만 하고 matched=false")
    void givenDay3AndNoMutual_whenSelect_thenSavedNotMatched() {
        stubMeAndRoom(3);
        given(roomMemberReader.getById(selectedId)).willReturn(member(selectedId, 2L, roomId));
        given(finalSelectionReader.hasSelected(meId)).willReturn(false);
        given(finalSelectionReader.findByRoomMemberId(selectedId)).willReturn(Optional.empty());

        FinalSelectionResponse result = service.select(roomId, selectedId, userId);

        assertThat(result.matched()).isFalse();
        then(finalSelectionWriter).should().save(any(FinalSelection.class));
        then(matchResultWriter).should(never()).save(any());

        ArgumentCaptor<MemberSelectedEvent> captor = ArgumentCaptor.forClass(MemberSelectedEvent.class);
        then(eventPublisher).should().publishEvent(captor.capture());
        assertThat(captor.getValue().roomId()).isEqualTo(roomId);
        assertThat(captor.getValue().selectedUserId()).isEqualTo(2L); // 선택받은 멤버의 userId
    }

    @Test
    @DisplayName("상대도 나를 선택했으면 즉시 매칭되고 MatchResult는 canonical 정렬(a<b)로 저장된다")
    void givenMutual_whenSelect_thenMatchedAndCanonicalSaved() {
        stubMeAndRoom(3);
        given(roomMemberReader.getById(selectedId)).willReturn(member(selectedId, 2L, roomId));
        given(finalSelectionReader.hasSelected(meId)).willReturn(false);
        given(finalSelectionReader.findByRoomMemberId(selectedId))
                .willReturn(Optional.of(new FinalSelection(40L, selectedId, meId, LocalDateTime.now())));
        given(matchResultWriter.save(any()))
                .willReturn(new MatchResult(500L, meId, selectedId, LocalDateTime.now()));

        FinalSelectionResponse result = service.select(roomId, selectedId, userId);

        assertThat(result.matched()).isTrue();
        ArgumentCaptor<MatchResult> captor = ArgumentCaptor.forClass(MatchResult.class);
        then(matchResultWriter).should().save(captor.capture());
        MatchResult saved = captor.getValue();
        assertThat(saved.memberAId().compareTo(saved.memberBId())).isLessThan(0);

        ArgumentCaptor<MutualMatchEvent> eventCaptor = ArgumentCaptor.forClass(MutualMatchEvent.class);
        then(eventPublisher).should().publishEvent(eventCaptor.capture());
        MutualMatchEvent event = eventCaptor.getValue();
        assertThat(event.roomId()).isEqualTo(roomId);
        assertThat(event.matchResultId()).isEqualTo(500L); // 저장된 MatchResult id가 이벤트에 실림(#61 방 자동생성용)
        assertThat(event.userIdA()).isEqualTo(userId);   // 나
        assertThat(event.userIdB()).isEqualTo(2L);        // 상대
    }

    @Test
    @DisplayName("Day3 이전이면 NotFinalSelectionDayException이 발생한다")
    void givenBeforeDay3_whenSelect_thenThrows() {
        stubMeAndRoom(2);

        assertThatThrownBy(() -> service.select(roomId, selectedId, userId))
                .isInstanceOf(NotFinalSelectionDayException.class);
        then(finalSelectionWriter).should(never()).save(any());
    }

    private FinalSelectionService serviceWith(Clock fixedClock) {
        return new FinalSelectionService(roomReader, roomMemberReader, finalSelectionReader,
                finalSelectionWriter, matchResultWriter, eventPublisher, fixedClock);
    }

    @Test
    @DisplayName("Day3라도 마지막 슬롯 종료(18:00) 전이면 NotFinalSelectionDayException이 발생한다")
    void givenDay3Before18_whenSelect_thenThrows() {
        // 2026-06-26 17:00 KST — Day3 slot3(15~18) 진행 중
        FinalSelectionService early = serviceWith(
                Clock.fixed(Instant.parse("2026-06-26T08:00:00Z"), ZoneId.of("Asia/Seoul")));
        stubMeAndRoom(3);

        assertThatThrownBy(() -> early.select(roomId, selectedId, userId))
                .isInstanceOf(NotFinalSelectionDayException.class);
        then(finalSelectionWriter).should(never()).save(any());
    }

    @Test
    @DisplayName("Day3 18:00 정각부터 최종선택이 가능하다")
    void givenDay3At18_whenSelect_thenAllowed() {
        FinalSelectionService atOpen = serviceWith(
                Clock.fixed(Instant.parse("2026-06-26T09:00:00Z"), ZoneId.of("Asia/Seoul")));
        stubMeAndRoom(3);
        given(roomMemberReader.getById(selectedId)).willReturn(member(selectedId, 2L, roomId));
        given(finalSelectionReader.hasSelected(meId)).willReturn(false);
        given(finalSelectionReader.findByRoomMemberId(selectedId)).willReturn(Optional.empty());

        FinalSelectionResponse result = atOpen.select(roomId, selectedId, userId);

        assertThat(result.matched()).isFalse();
        then(finalSelectionWriter).should().save(any(FinalSelection.class));
    }

    @Test
    @DisplayName("Day3 심야(익일 02:00)는 22:00 마감 이후이므로 SelectionClosedException이 발생한다")
    void givenDay3LateNight_whenSelect_thenClosed() {
        // 2026-06-27 02:00 KST — 하루 경계(09:00) 전이라 Day3이지만 22:00 마감을 지남
        FinalSelectionService lateNight = serviceWith(
                Clock.fixed(Instant.parse("2026-06-26T17:00:00Z"), ZoneId.of("Asia/Seoul")));
        stubMeAndRoom(3);

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
        stubMeAndRoom(3);
        given(roomMemberReader.getById(selectedId)).willReturn(member(selectedId, 2L, roomId));
        given(finalSelectionReader.hasSelected(meId)).willReturn(false);
        given(finalSelectionReader.findByRoomMemberId(selectedId)).willReturn(Optional.empty());

        FinalSelectionResponse result = beforeClose.select(roomId, selectedId, userId);

        assertThat(result.matched()).isFalse();
        then(finalSelectionWriter).should().save(any(FinalSelection.class));
    }

    @Test
    @DisplayName("Day3 22:00 정각부터는 SelectionClosedException이 발생한다")
    void givenDay3AtClose_whenSelect_thenClosed() {
        // 2026-06-26 22:00 KST — 결과 발표 시각(CONFIRM_HOUR)
        FinalSelectionService atClose = serviceWith(
                Clock.fixed(Instant.parse("2026-06-26T13:00:00Z"), ZoneId.of("Asia/Seoul")));
        stubMeAndRoom(3);

        assertThatThrownBy(() -> atClose.select(roomId, selectedId, userId))
                .isInstanceOf(SelectionClosedException.class);
        then(finalSelectionWriter).should(never()).save(any());
    }

    @Test
    @DisplayName("22:00 마감 이후에는 기권도 SelectionClosedException이 발생한다")
    void givenAfterClose_whenAbstain_thenClosed() {
        FinalSelectionService atClose = serviceWith(
                Clock.fixed(Instant.parse("2026-06-26T13:00:00Z"), ZoneId.of("Asia/Seoul")));
        stubMeAndRoom(3);

        assertThatThrownBy(() -> atClose.select(roomId, null, userId))
                .isInstanceOf(SelectionClosedException.class);
        then(finalSelectionWriter).should(never()).save(any());
    }

    @Test
    @DisplayName("자기 자신을 선택하면 CannotSelectSelfException이 발생한다")
    void givenSelfSelection_whenSelect_thenThrows() {
        stubMeAndRoom(3);

        assertThatThrownBy(() -> service.select(roomId, meId, userId))
                .isInstanceOf(CannotSelectSelfException.class);
    }

    @Test
    @DisplayName("이미 최종선택했으면 AlreadySelectedException이 발생한다(1회 확정)")
    void givenAlreadySelected_whenSelect_thenThrows() {
        stubMeAndRoom(3);
        given(roomMemberReader.getById(selectedId)).willReturn(member(selectedId, 2L, roomId));
        given(finalSelectionReader.hasSelected(meId)).willReturn(true);

        assertThatThrownBy(() -> service.select(roomId, selectedId, userId))
                .isInstanceOf(AlreadySelectedException.class);
        then(finalSelectionWriter).should(never()).save(any());
    }

    @Test
    @DisplayName("다른 방 멤버를 선택하면 RoomMemberNotFoundException이 발생한다")
    void givenMemberInAnotherRoom_whenSelect_thenThrows() {
        stubMeAndRoom(3);
        given(roomMemberReader.getById(selectedId)).willReturn(member(selectedId, 2L, 99L));

        assertThatThrownBy(() -> service.select(roomId, selectedId, userId))
                .isInstanceOf(RoomMemberNotFoundException.class);
    }

    @Test
    @DisplayName("기권(selectedMemberId=null)하면 아무도 선택하지 않고 저장하며 matched=false·매칭·이벤트 없음")
    void givenAbstain_whenSelect_thenSavedWithNullAndNotMatched() {
        stubMeAndRoom(3);
        given(finalSelectionReader.hasSelected(meId)).willReturn(false);

        FinalSelectionResponse result = service.select(roomId, null, userId);

        assertThat(result.selectedMemberId()).isNull();
        assertThat(result.matched()).isFalse();

        ArgumentCaptor<FinalSelection> captor = ArgumentCaptor.forClass(FinalSelection.class);
        then(finalSelectionWriter).should().save(captor.capture());
        assertThat(captor.getValue().selectedMemberId()).isNull();
        then(matchResultWriter).should(never()).save(any());
        then(eventPublisher).should(never()).publishEvent(any());
    }

    @Test
    @DisplayName("이미 최종선택(또는 기권)했으면 기권 요청도 AlreadySelectedException이 발생한다")
    void givenAlreadySelected_whenAbstain_thenThrows() {
        stubMeAndRoom(3);
        given(finalSelectionReader.hasSelected(meId)).willReturn(true);

        assertThatThrownBy(() -> service.select(roomId, null, userId))
                .isInstanceOf(AlreadySelectedException.class);
        then(finalSelectionWriter).should(never()).save(any());
    }

    @Test
    @DisplayName("상대가 기권(selectedMemberId=null)했으면 내가 상대를 선택해도 NPE 없이 matched=false")
    void givenTargetAbstained_whenSelect_thenNotMatchedNoNpe() {
        stubMeAndRoom(3);
        given(roomMemberReader.getById(selectedId)).willReturn(member(selectedId, 2L, roomId));
        given(finalSelectionReader.hasSelected(meId)).willReturn(false);
        // 상대는 기권(selectedMemberId=null)한 최종선택 행을 가짐
        given(finalSelectionReader.findByRoomMemberId(selectedId))
                .willReturn(Optional.of(new FinalSelection(40L, selectedId, null, LocalDateTime.now())));

        FinalSelectionResponse result = service.select(roomId, selectedId, userId);

        assertThat(result.matched()).isFalse();
        then(matchResultWriter).should(never()).save(any());
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
    @DisplayName("방 멤버가 아니면 최종선택 조회 시 NotRoomMemberException이 발생한다")
    void givenNonMember_whenGetMySelection_thenThrows() {
        given(roomMemberReader.getByRoomIdAndUserId(roomId, userId)).willThrow(new NotRoomMemberException());

        assertThatThrownBy(() -> service.getMySelection(roomId, userId))
                .isInstanceOf(NotRoomMemberException.class);
    }
}
