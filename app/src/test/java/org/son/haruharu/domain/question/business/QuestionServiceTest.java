package org.son.haruharu.domain.question.business;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import org.junit.jupiter.api.*;
import org.son.haruharu.domain.block.implement.BlockReader;
import org.son.haruharu.domain.question.business.command.*;
import org.son.haruharu.domain.question.implement.*;
import org.son.haruharu.domain.question.implement.event.*;
import org.son.haruharu.domain.report.implement.*;
import org.son.haruharu.domain.room.implement.*;
import org.son.haruharu.domain.user.implement.*;
import org.springframework.context.ApplicationEventPublisher;

import java.time.*;
import java.util.*;

class QuestionServiceTest {
    final LocalDateTime now = LocalDateTime.of(2026, 9, 13, 12, 0);
    Clock clock = Clock.fixed(now.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    QuestionRepository repository;
    RoomReader rooms;
    RoomMemberReader members;
    UserReader users;
    BlockReader blocks;
    ReportReader reports;
    ReportWriter reportWriter;
    ApplicationEventPublisher events;
    QuestionService service;
    Room room;
    List<RoomMember> participants;
    Map<Long, Question> questions;
    Map<String, QuestionGrant> grants;

    @BeforeEach
    void setup() {
        repository = mock(QuestionRepository.class);
        rooms = mock(RoomReader.class);
        members = mock(RoomMemberReader.class);
        users = mock(UserReader.class);
        blocks = mock(BlockReader.class);
        reports = mock(ReportReader.class);
        reportWriter = mock(ReportWriter.class);
        events = mock(ApplicationEventPublisher.class);
        questions = new HashMap<>();
        grants = new HashMap<>();
        room =
                new Room(
                        1L,
                        RoomStatus.PENDING,
                        now.toLocalDate().minusDays(1),
                        3,
                        null,
                        null,
                        now.minusHours(24),
                        now.plusHours(24),
                        now.plusHours(25),
                        now.plusHours(26),
                        now.plusHours(27));
        participants =
                List.of(
                        new RoomMember(10L, 1L, 100L, "MALE", "해님", null, now),
                        new RoomMember(11L, 1L, 101L, "FEMALE", "달님", null, now),
                        new RoomMember(12L, 1L, 102L, "FEMALE", "별님", null, now),
                        new RoomMember(13L, 1L, 103L, "FEMALE", "꽃님", null, now));
        given(rooms.findById(1L)).willAnswer(i -> Optional.of(room));
        given(rooms.findByIdForUpdate(1L)).willAnswer(i -> Optional.of(room));
        given(members.getByRoomId(1L)).willAnswer(i -> participants);
        given(users.findActiveIds(any()))
                .willAnswer(i -> new HashSet<>((Collection<Long>) i.getArgument(0)));
        given(repository.findSent(eq(1L), anyLong()))
                .willAnswer(
                        i ->
                                questions.values().stream()
                                        .filter(q -> q.senderUserId().equals(i.getArgument(1)))
                                        .toList());
        given(repository.findByRequest(eq(1L), anyLong(), any()))
                .willAnswer(
                        i ->
                                questions.values().stream()
                                        .filter(
                                                q ->
                                                        q.senderUserId().equals(i.getArgument(1))
                                                                && q.requestId()
                                                                        .equals(i.getArgument(2)))
                                        .findFirst());
        given(repository.countSent(eq(1L), anyLong()))
                .willAnswer(
                        i ->
                                questions.values().stream()
                                        .filter(q -> q.senderUserId().equals(i.getArgument(1)))
                                        .count());
        given(repository.hasSentTo(eq(1L), anyLong(), anyLong(), anyInt()))
                .willAnswer(
                        i ->
                                questions.values().stream()
                                        .anyMatch(
                                                q ->
                                                        q.senderUserId().equals(i.getArgument(1))
                                                                && q.recipientUserId()
                                                                        .equals(i.getArgument(2))
                                                                && q.day()
                                                                        == (int) i.getArgument(3)));
        given(repository.findReceived(eq(1L), anyLong(), anySet(), anyLong(), anyInt()))
                .willAnswer(
                        i ->
                                questions.values().stream()
                                        .filter(
                                                q ->
                                                        q.recipientUserId().equals(i.getArgument(1))
                                                                && !q.hiddenFor(i.getArgument(1))
                                                                && !((Set<Long>) i.getArgument(2))
                                                                        .contains(q.senderUserId()))
                                        .toList());
        given(repository.countReceived(eq(1L), anyLong(), anySet()))
                .willAnswer(
                        i ->
                                questions.values().stream()
                                        .filter(
                                                q ->
                                                        q.recipientUserId().equals(i.getArgument(1))
                                                                && !q.hiddenFor(i.getArgument(1))
                                                                && !((Set<Long>) i.getArgument(2))
                                                                        .contains(q.senderUserId()))
                                        .count());
        given(repository.latestUnread(eq(1L), anyLong(), anySet()))
                .willAnswer(
                        i ->
                                questions.values().stream()
                                        .filter(
                                                q ->
                                                        q.participant(i.getArgument(1))
                                                                && q.unreadFor(i.getArgument(1))
                                                                && !((Set<Long>) i.getArgument(2))
                                                                        .contains(
                                                                                q.otherUserId(
                                                                                        i
                                                                                                .getArgument(
                                                                                                        1))))
                                        .findFirst());
        given(repository.findById(anyLong()))
                .willAnswer(i -> Optional.ofNullable(questions.get(i.getArgument(0))));
        given(repository.save(any()))
                .willAnswer(
                        i -> {
                            Question q = i.getArgument(0);
                            if (q.id() == null)
                                q =
                                        new Question(
                                                (long) questions.size() + 1,
                                                q.roomId(),
                                                q.senderMemberId(),
                                                q.recipientMemberId(),
                                                q.senderUserId(),
                                                q.recipientUserId(),
                                                q.recipientNickname(),
                                                q.content(),
                                                q.requestId(),
                                                q.answer(),
                                                q.createdAt(),
                                                q.answeredAt(),
                                                q.recipientRead(),
                                                q.senderRead(),
                                                q.senderReported(),
                                                q.recipientReported(),
                                                q.day());
                            questions.put(q.id(), q);
                            return q;
                        });
        given(repository.findGrant(eq(1L), anyLong(), anyInt()))
                .willAnswer(
                        i ->
                                Optional.ofNullable(
                                        grants.get(i.getArgument(1) + ":" + i.getArgument(2))));
        given(repository.saveGrant(any()))
                .willAnswer(
                        i -> {
                            QuestionGrant g = i.getArgument(0);
                            grants.put(g.userId() + ":" + g.day(), g);
                            return g;
                        });
        at(now);
    }

    /** 서비스가 읽는 현재 시각을 옮긴다. 방은 now-24h(9/12) 시작, 최종 선택은 9/14 12:00이다. */
    void at(LocalDateTime time) {
        clock = Clock.fixed(time.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        service =
                new QuestionService(
                        new QuestionReader(repository),
                        new QuestionWriter(repository),
                        rooms,
                        members,
                        users,
                        blocks,
                        reportWriter,
                        events,
                        clock);
    }

    @Test
    @DisplayName("모든 이성 상대에게 한 번씩 질문하고 동일 요청 재시도는 중복 저장하지 않는다")
    void givenOpenRoom_whenSend_thenPerTargetLimitAndIdempotency() {
        var command = new CreateQuestionCommand(11L, "안녕", UUID.randomUUID());
        var first = service.create(1L, 100L, command);
        assertThat(service.create(1L, 100L, command).id()).isEqualTo(first.id());
        assertThatThrownBy(
                        () ->
                                service.create(
                                        1L,
                                        100L,
                                        new CreateQuestionCommand(11L, "다시", UUID.randomUUID())))
                .hasMessageContaining("이미 질문");
        service.create(1L, 100L, new CreateQuestionCommand(12L, "안녕", UUID.randomUUID()));
        assertThat(service.list(1L, 100L).remainingCount()).isEqualTo(1);
        assertThat(service.list(1L, 100L).targets())
                .filteredOn(t -> t.roomMemberId().equals(13L))
                .extracting(t -> t.status())
                .containsExactly("AVAILABLE");
        service.create(1L, 100L, new CreateQuestionCommand(13L, "안녕", UUID.randomUUID()));
        assertThat(questions).hasSize(3);
        assertThat(service.list(1L, 100L).targets())
                .extracting(t -> t.questionId())
                .doesNotContainNull();
        assertThat(service.list(1L, 100L).remainingCount()).isZero();
        then(events).should(times(3)).publishEvent(any(QuestionCreatedEvent.class));
    }

    @Test
    @DisplayName("남은 횟수는 차단·탈퇴·이미 전송한 상대를 제외한 실제 질문 가능 상대 수다")
    void givenUnavailableAndSentTargets_whenList_thenCountOnlyAvailableTargets() {
        assertThat(service.list(1L, 100L).remainingCount()).isEqualTo(3);
        service.create(1L, 100L, new CreateQuestionCommand(11L, "안녕", UUID.randomUUID()));
        given(blocks.getBlockedUserIds(100L)).willReturn(Set.of(102L));
        given(blocks.getRelatedUserIds(100L)).willReturn(Set.of(102L, 103L));
        assertThat(service.list(1L, 100L).remainingCount()).isZero();
        given(blocks.getBlockedUserIds(100L)).willReturn(Set.of());
        given(blocks.getRelatedUserIds(100L)).willReturn(Set.of());
        willReturn(Set.of(100L, 101L, 102L)).given(users).findActiveIds(any());
        assertThat(service.list(1L, 100L).remainingCount()).isEqualTo(1);
        participants = participants.stream().filter(p -> !p.userId().equals(101L)).toList();
        assertThat(service.list(1L, 100L).remainingCount()).isEqualTo(1);
        assertThat(service.list(1L, 100L).targets())
                .filteredOn(t -> t.roomMemberId().equals(11L))
                .extracting(t -> t.status())
                .containsExactly("CLOSED");
    }

    @Test
    @DisplayName("수신 상세는 질문자 정보를 내보내지 않고 실제 열 때만 읽음 처리한다")
    void givenReceived_whenGetAndRead_thenAnonymousAndExplicitRead() {
        var sent =
                service.create(1L, 100L, new CreateQuestionCommand(11L, "안녕", UUID.randomUUID()));
        var received = service.detail(1L, 101L, sent.id());
        assertThat(received.direction()).isEqualTo("RECEIVED");
        assertThat(received.recipient()).isNull();
        assertThat(service.list(1L, 101L).hasUnread()).isTrue();
        service.read(1L, 101L, sent.id());
        assertThat(service.list(1L, 101L).received().getFirst().unread()).isFalse();
        service.answer(1L, 101L, sent.id(), new AnswerQuestionCommand("반가워"));
        assertThat(service.list(1L, 100L).hasUnread()).isTrue();
    }

    @Test
    @DisplayName("오픈 직전과 마감 정각에는 작성이 거부되고 종료 기록은 읽는다")
    void givenBoundaries_whenSend_thenRejectAndKeepHistory() {
        var command = new CreateQuestionCommand(11L, "안녕", UUID.randomUUID());
        var sent = service.create(1L, 100L, command);
        room =
                new Room(
                        1L,
                        RoomStatus.PENDING,
                        now.toLocalDate(),
                        3,
                        null,
                        null,
                        now,
                        now.plusHours(50),
                        now.plusHours(3),
                        now.plusHours(4),
                        now.plusHours(5));
        assertThat(service.list(1L, 100L).phase()).isEqualTo("LOCKED");
        assertThatThrownBy(
                        () ->
                                service.create(
                                        1L,
                                        100L,
                                        new CreateQuestionCommand(12L, "안녕", UUID.randomUUID())))
                .hasMessageContaining("보낼 수");
        room =
                new Room(
                        1L,
                        RoomStatus.ACTIVE,
                        now.toLocalDate(),
                        3,
                        null,
                        null,
                        now.minusHours(24),
                        now,
                        now.plusHours(3),
                        now.plusHours(4),
                        now.plusHours(5));
        assertThat(service.detail(1L, 101L, sent.id()).status()).isEqualTo("CLOSED");
        assertThatThrownBy(
                        () -> service.answer(1L, 101L, sent.id(), new AnswerQuestionCommand("반가워")))
                .hasMessageContaining("보낼 수");
    }

    @Test
    @DisplayName("차단·신고·이탈 시 쓰기를 차단하고 본인에게만 기록을 숨긴다")
    void givenModeration_whenReadAndAnswer_thenRespectVisibility() {
        var sent =
                service.create(1L, 100L, new CreateQuestionCommand(11L, "안녕", UUID.randomUUID()));
        service.report(1L, 101L, sent.id(), new ReportQuestionCommand(ReportReason.OTHER, "불쾌"));
        assertThat(service.list(1L, 101L).received()).isEmpty();
        assertThat(service.detail(1L, 100L, sent.id()).canAnswer()).isFalse();
        assertThatThrownBy(() -> service.detail(1L, 101L, sent.id())).hasMessageContaining("접근");
        assertThat(service.canNotify(1L, sent.id())).isFalse();
        given(blocks.getBlockedUserIds(100L)).willReturn(Set.of(101L));
        assertThatThrownBy(() -> service.detail(1L, 100L, sent.id())).hasMessageContaining("접근");
        participants = participants.stream().filter(p -> p.userId() != 100L).toList();
        assertThatThrownBy(() -> service.list(1L, 100L)).hasMessageContaining("접근");
    }

    @Test
    @DisplayName("동일 요청의 본문이나 상대 변경은 거부한다")
    void givenRequestId_whenChangedPayload_thenConflict() {
        UUID request = UUID.randomUUID();
        service.create(1L, 100L, new CreateQuestionCommand(11L, "안녕", request));
        assertThatThrownBy(
                        () ->
                                service.create(
                                        1L, 100L, new CreateQuestionCommand(12L, "안녕", request)))
                .hasMessageContaining("같은 요청");
        assertThatThrownBy(
                        () ->
                                service.create(
                                        1L, 100L, new CreateQuestionCommand(11L, "다르게", request)))
                .hasMessageContaining("같은 요청");
        assertThat(questions).hasSize(1);
    }

    @Test
    @DisplayName("다른 방·동성·미지정 성별 상대는 질문할 수 없다")
    void givenInvalidTarget_whenSend_thenRejectWithoutSpending() {
        assertThatThrownBy(
                        () ->
                                service.create(
                                        1L,
                                        100L,
                                        new CreateQuestionCommand(10L, "안녕", UUID.randomUUID())))
                .hasMessageContaining("보낼 수");
        assertThatThrownBy(
                        () ->
                                service.create(
                                        1L,
                                        100L,
                                        new CreateQuestionCommand(99L, "안녕", UUID.randomUUID())))
                .hasMessageContaining("보낼 수");
        participants =
                List.of(new RoomMember(10L, 1L, 100L, null, null, null, now), participants.get(1));
        assertThat(service.list(1L, 100L).remainingCount()).isZero();
        assertThatThrownBy(
                        () ->
                                service.create(
                                        1L,
                                        100L,
                                        new CreateQuestionCommand(11L, "안녕", UUID.randomUUID())))
                .hasMessageContaining("보낼 수");
        assertThat(questions).isEmpty();
    }

    @Test
    @DisplayName("차단한 쪽만 내용을 숨기고 양방향 작성과 알림을 차단한다")
    void givenBlockedPair_whenAccess_thenHideOneSideAndForbidWrites() {
        var sent =
                service.create(1L, 100L, new CreateQuestionCommand(11L, "안녕", UUID.randomUUID()));
        given(blocks.getBlockedUserIds(100L)).willReturn(Set.of(101L));
        given(blocks.getRelatedUserIds(100L)).willReturn(Set.of(101L));
        given(blocks.getRelatedUserIds(101L)).willReturn(Set.of(100L));
        assertThat(service.list(1L, 100L).targets()).noneMatch(t -> t.roomMemberId().equals(11L));
        assertThat(service.detail(1L, 101L, sent.id()).question()).isEqualTo("안녕");
        assertThat(service.detail(1L, 101L, sent.id()).canAnswer()).isFalse();
        assertThatThrownBy(
                        () -> service.answer(1L, 101L, sent.id(), new AnswerQuestionCommand("답변")))
                .hasMessageContaining("보낼 수");
        assertThat(service.canNotify(1L, sent.id())).isFalse();
    }

    @Test
    @DisplayName("취소 방과 이탈 상대의 기록은 읽기 전용이며 비활성 본인은 접근하지 못한다")
    void givenCancelledOrDeparted_whenRead_thenReadonlyAndDenyInactiveCaller() {
        var sent =
                service.create(1L, 100L, new CreateQuestionCommand(11L, "안녕", UUID.randomUUID()));
        room = room.cancelled();
        assertThat(service.list(1L, 101L).phase()).isEqualTo("CLOSED");
        assertThat(service.detail(1L, 101L, sent.id()).canAnswer()).isFalse();
        participants = participants.stream().filter(p -> !p.userId().equals(101L)).toList();
        assertThat(service.list(1L, 100L).targets())
                .anyMatch(t -> t.questionId() != null && t.status().equals("CLOSED"));
        assertThat(service.detail(1L, 100L, sent.id()).recipient().nickname()).isEqualTo("달님");
        willReturn(Set.of()).given(users).findActiveIds(any());
        assertThatThrownBy(() -> service.detail(1L, 100L, sent.id())).hasMessageContaining("접근");
    }

    @Test
    @DisplayName("답변 완료 후 마감된 동일 재시도는 기존 답변만 반환한다")
    void givenAnsweredThenClosed_whenRetry_thenIdempotent() {
        var sent =
                service.create(1L, 100L, new CreateQuestionCommand(11L, "안녕", UUID.randomUUID()));
        service.answer(1L, 101L, sent.id(), new AnswerQuestionCommand("반가워"));
        room = room.cancelled();
        assertThat(service.answer(1L, 101L, sent.id(), new AnswerQuestionCommand("반가워")).answer())
                .isEqualTo("반가워");
        assertThatThrownBy(
                        () -> service.answer(1L, 101L, sent.id(), new AnswerQuestionCommand("변경")))
                .hasMessageContaining("이미 답변");
        then(events).should(times(1)).publishEvent(any(QuestionAnsweredEvent.class));
    }

    @Test
    @DisplayName("질문권 지급과 읽음은 중복되지 않고 다른 소식과 독립이다")
    void givenEligibleRoom_whenReconcileAndReadGrant_thenOnceAndPersistent() {
        assertThat(service.list(1L, 100L).grantUnread()).isTrue();
        service.reconcileGrants(1L);
        service.reconcileGrants(1L);
        assertThat(grants).hasSize(4);
        then(events).should(times(4)).publishEvent(any(QuestionGrantAvailableEvent.class));
        service.readGrant(1L, 100L);
        assertThat(service.list(1L, 100L).grantUnread()).isFalse();
        assertThat(service.list(1L, 101L).grantUnread()).isTrue();
        assertThat(service.canNotifyGrant(1L, 100L)).isTrue();
        room = room.cancelled();
        assertThat(service.canNotifyGrant(1L, 100L)).isFalse();
        service.reconcileGrants(1L);
        assertThat(grants).hasSize(4);
    }

    @Test
    @DisplayName("답변 없는 발신자 신고와 잘못된 신고 입력은 상태를 변경하지 않는다")
    void givenInvalidReport_whenReport_thenKeepExchange() {
        var sent =
                service.create(1L, 100L, new CreateQuestionCommand(11L, "안녕", UUID.randomUUID()));
        assertThatThrownBy(
                        () ->
                                service.report(
                                        1L,
                                        100L,
                                        sent.id(),
                                        new ReportQuestionCommand(ReportReason.OTHER, null)))
                .hasMessageContaining("보낼 수");
        assertThatThrownBy(
                        () ->
                                service.report(
                                        1L, 101L, sent.id(), new ReportQuestionCommand(null, null)))
                .hasMessageContaining("질문");
        assertThatThrownBy(
                        () ->
                                service.report(
                                        1L,
                                        101L,
                                        sent.id(),
                                        new ReportQuestionCommand(
                                                ReportReason.OTHER, "가".repeat(501))))
                .hasMessageContaining("질문");
        assertThat(service.list(1L, 101L).received()).hasSize(1);
        then(reportWriter).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("신고는 콘텐츠를 연결해 한 번만 접수하고 상대에게 신고자를 드러내지 않는다")
    void givenAnswered_whenSenderReportsTwice_thenScopedReportAndOneSidedHide() {
        var sent =
                service.create(1L, 100L, new CreateQuestionCommand(11L, "안녕", UUID.randomUUID()));
        service.answer(1L, 101L, sent.id(), new AnswerQuestionCommand("답변"));
        service.report(1L, 100L, sent.id(), new ReportQuestionCommand(ReportReason.OTHER, "신고"));
        service.report(1L, 100L, sent.id(), new ReportQuestionCommand(ReportReason.OTHER, "신고"));
        then(reportWriter)
                .should(times(1))
                .save(
                        argThat(
                                r ->
                                        r.questionId().equals(sent.id())
                                                && r.reportedUserId().equals(101L)));
        assertThat(service.list(1L, 100L).targets())
                .anyMatch(t -> t.status().equals("REPORTED") && !t.unread());
        assertThat(service.detail(1L, 101L, sent.id()).answer()).isEqualTo("답변");
    }

    @Test
    @DisplayName("알림 권한은 존재·회원 상태·방과 신고를 다시 확인한다")
    void givenMissingOrWrongRoom_whenNotificationCheck_thenSuppress() {
        assertThat(service.canNotify(1L, 999L)).isFalse();
        assertThat(service.canNotifyGrant(999L, 100L)).isFalse();
        var sent =
                service.create(1L, 100L, new CreateQuestionCommand(11L, "안녕", UUID.randomUUID()));
        assertThat(service.canNotify(1L, sent.id())).isTrue();
        assertThat(service.canNotify(999L, sent.id())).isFalse();
        assertThatThrownBy(() -> service.detail(1L, 102L, sent.id())).hasMessageContaining("접근");
    }

    @Test
    @DisplayName("상대 멤버십 ID가 재생성되어도 같은 사용자에게 두 번 질문할 수 없다")
    void givenRecreatedMembership_whenSendAgain_thenSamePersonLimitRemains() {
        service.create(1L, 100L, new CreateQuestionCommand(11L, "안녕", UUID.randomUUID()));
        participants =
                List.of(
                        participants.getFirst(),
                        new RoomMember(99L, 1L, 101L, "FEMALE", "달님", null, now));
        assertThatThrownBy(
                        () ->
                                service.create(
                                        1L,
                                        100L,
                                        new CreateQuestionCommand(99L, "다시", UUID.randomUUID())))
                .hasMessageContaining("이미 질문");
        assertThat(questions).hasSize(1);
        assertThat(service.list(1L, 100L).targets())
                .singleElement()
                .satisfies(
                        target -> {
                            assertThat(target.roomMemberId()).isEqualTo(99L);
                            assertThat(target.questionId()).isNotNull();
                            assertThat(target.status()).isEqualTo("WAITING");
                        });
    }

    @Test
    @DisplayName("같은 상대에게도 2일차와 3일차에 한 번씩 질문하고 날마다 다시 한 번만 허용한다")
    void givenSameTarget_whenDayTwoAndThree_thenOncePerDay() {
        var dayTwo =
                service.create(1L, 100L, new CreateQuestionCommand(11L, "안녕", UUID.randomUUID()));
        assertThat(dayTwo.day()).isEqualTo(2);
        assertThatThrownBy(
                        () ->
                                service.create(
                                        1L,
                                        100L,
                                        new CreateQuestionCommand(11L, "또", UUID.randomUUID())))
                .hasMessageContaining("이미 질문");

        at(now.plusHours(22));
        var list = service.list(1L, 100L);
        assertThat(list.currentDay()).isEqualTo(3);
        assertThat(list.days()).extracting(d -> d.state()).containsExactly("CLOSED", "OPEN");
        assertThat(list.days().getFirst().targets())
                .extracting(t -> t.roomMemberId(), t -> t.status())
                .containsExactlyInAnyOrder(
                        tuple(11L, "WAITING"), tuple(12L, "CLOSED"), tuple(13L, "CLOSED"));
        assertThat(list.days().get(1).targets())
                .extracting(t -> t.status())
                .containsOnly("AVAILABLE");
        assertThat(list.days().get(1).remainingCount()).isEqualTo(3);

        var dayThree =
                service.create(
                        1L, 100L, new CreateQuestionCommand(11L, "다시 안녕", UUID.randomUUID()));
        assertThat(dayThree.day()).isEqualTo(3);
        assertThatThrownBy(
                        () ->
                                service.create(
                                        1L,
                                        100L,
                                        new CreateQuestionCommand(11L, "세 번째", UUID.randomUUID())))
                .hasMessageContaining("이미 질문");
        assertThat(service.list(1L, 100L).days().get(1).remainingCount()).isEqualTo(2);
        assertThat(service.list(1L, 100L).remainingCount()).isEqualTo(2);
        assertThat(service.list(1L, 101L).received())
                .extracting(r -> r.day())
                .containsExactlyInAnyOrder(2, 3);
        assertThat(service.detail(1L, 101L, dayTwo.id()).canAnswer()).isTrue();
    }

    @Test
    @DisplayName("자정부터 08:00까지는 질문을 보낼 수 없지만 받은 질문에는 답할 수 있다")
    void givenNight_whenSendAndAnswer_thenSendClosedAnswerOpen() {
        var sent =
                service.create(1L, 100L, new CreateQuestionCommand(11L, "안녕", UUID.randomUUID()));
        at(now.plusHours(12).plusMinutes(30));
        assertThatThrownBy(
                        () ->
                                service.create(
                                        1L,
                                        100L,
                                        new CreateQuestionCommand(12L, "안녕", UUID.randomUUID())))
                .hasMessageContaining("보낼 수");
        var list = service.list(1L, 100L);
        assertThat(list.phase()).isEqualTo("CLOSED");
        assertThat(list.currentDay()).isEqualTo(3);
        assertThat(list.days().get(1).state()).isEqualTo("LOCKED");
        assertThat(list.days().get(1).targets()).extracting(t -> t.status()).containsOnly("LOCKED");
        assertThat(list.targets()).extracting(t -> t.status()).doesNotContain("LOCKED");
        assertThat(list.remainingCount()).isZero();
        assertThat(service.answer(1L, 101L, sent.id(), new AnswerQuestionCommand("반가워")).answer())
                .isEqualTo("반가워");
    }

    @Test
    @DisplayName("질문 열림은 날마다 한 번씩 지급하고 밤사이에는 지급하지 않는다")
    void givenDays_whenReconcileGrants_thenOncePerOpenDay() {
        service.reconcileGrants(1L);
        service.readGrant(1L, 100L);
        assertThat(grants).hasSize(4);
        assertThat(service.list(1L, 100L).grantUnread()).isFalse();

        at(now.plusHours(12).plusMinutes(30));
        service.reconcileGrants(1L);
        assertThat(grants).hasSize(4);
        assertThat(service.list(1L, 100L).grantUnread()).isFalse();

        at(now.plusHours(21));
        assertThat(service.list(1L, 100L).grantUnread()).isTrue();
        assertThat(service.list(1L, 100L).latestEvent().key()).isEqualTo("grant:1:3");
        service.reconcileGrants(1L);
        service.reconcileGrants(1L);
        assertThat(grants).hasSize(8);
        then(events).should(times(8)).publishEvent(any(QuestionGrantAvailableEvent.class));
    }
}
