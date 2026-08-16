package org.son.haruharu.domain.room.business;

import java.time.Clock;
import java.time.LocalDateTime;

import org.son.haruharu.domain.room.business.dto.FinalSelectionResponse;
import org.son.haruharu.domain.room.business.dto.MyFinalSelectionResponse;
import org.son.haruharu.domain.room.business.event.MemberSelectedEvent;
import org.son.haruharu.domain.room.business.event.MutualMatchEvent;
import org.son.haruharu.domain.room.business.exception.AlreadySelectedException;
import org.son.haruharu.domain.room.business.exception.CannotSelectSameGenderException;
import org.son.haruharu.domain.room.business.exception.CannotSelectSelfException;
import org.son.haruharu.domain.room.business.exception.NotFinalSelectionDayException;
import org.son.haruharu.domain.room.business.exception.RoomNotFoundException;
import org.son.haruharu.domain.room.business.exception.SelectionClosedException;
import org.son.haruharu.domain.room.implement.FinalSelection;
import org.son.haruharu.domain.room.implement.FinalSelectionReader;
import org.son.haruharu.domain.room.implement.FinalSelectionWriter;
import org.son.haruharu.domain.room.implement.MatchResult;
import org.son.haruharu.domain.room.implement.MatchResultWriter;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.storage.exception.RoomMemberNotFoundException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Day3 최종선택. 멤버당 1회 확정(변경 불가)이며, 상대도 나를 선택했으면
 * 즉시 상호매칭(MatchResult, canonical ordering 1쌍 1행)을 성사시킨다.
 */
@Service
@Transactional(readOnly = true)
public class FinalSelectionService {

    private final RoomReader roomReader;
    private final RoomMemberReader roomMemberReader;
    private final FinalSelectionReader finalSelectionReader;
    private final FinalSelectionWriter finalSelectionWriter;
    private final MatchResultWriter matchResultWriter;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public FinalSelectionService(RoomReader roomReader, RoomMemberReader roomMemberReader,
                                 FinalSelectionReader finalSelectionReader, FinalSelectionWriter finalSelectionWriter,
                                 MatchResultWriter matchResultWriter, ApplicationEventPublisher eventPublisher,
                                 Clock clock) {
        this.roomReader = roomReader;
        this.roomMemberReader = roomMemberReader;
        this.finalSelectionReader = finalSelectionReader;
        this.finalSelectionWriter = finalSelectionWriter;
        this.matchResultWriter = matchResultWriter;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional
    public FinalSelectionResponse select(Long roomId, Long selectedMemberId, Long userId) {
        RoomMember me = roomMemberReader.getByRoomIdAndUserId(roomId, userId); // 멤버십 인가 게이트
        Room room = roomReader.findById(roomId)
                .orElseThrow(RoomNotFoundException::new);
        LocalDateTime now = LocalDateTime.now(clock);
        // 최종선택은 Day 가 아니라 어드민이 방마다 입력한 시각(opens_at)으로만 판정한다("두 시계 분리", #320).
        if (now.isBefore(selectionOpensAt(room))) {
            throw new NotFinalSelectionDayException();
        }
        // 결과 발표(22:00) 이후 선택 불가 — 발표 시점의 미매칭 확정(위로 구름 지급, #143)이 뒤집히지 않게 한다.
        if (!now.isBefore(selectionClosesAt(room))) {
            throw new SelectionClosedException();
        }
        // 기권: 아무도 선택하지 않고 최종선택을 확정한다. 상호매칭 판정·이벤트 없이 selectedMemberId=null 행만 남긴다.
        if (selectedMemberId == null) {
            if (finalSelectionReader.hasSelected(me.id())) {
                throw new AlreadySelectedException(); // 1회 확정
            }
            finalSelectionWriter.save(FinalSelection.of(me.id(), null, now));
            return new FinalSelectionResponse(null, false);
        }
        if (selectedMemberId.equals(me.id())) {
            throw new CannotSelectSelfException();
        }
        RoomMember selected = roomMemberReader.getById(selectedMemberId);
        if (!selected.roomId().equals(roomId)) {
            throw new RoomMemberNotFoundException(); // 같은 방 멤버만 선택 가능
        }
        // 동성 선택 차단(#206). gender가 null인 스냅샷(구시드 데이터)은 검증을 건너뛴다 — 실유저는 인증 시 항상 채워진다.
        if (me.gender() != null && me.gender().equals(selected.gender())) {
            throw new CannotSelectSameGenderException();
        }
        if (finalSelectionReader.hasSelected(me.id())) {
            throw new AlreadySelectedException(); // 1회 확정
        }

        finalSelectionWriter.save(FinalSelection.of(me.id(), selectedMemberId, now));

        boolean matched = isMutual(selectedMemberId, me.id());
        if (matched) {
            MatchResult savedMatch = matchResultWriter.save(MatchResult.of(me.id(), selectedMemberId, now));
            eventPublisher.publishEvent(
                    new MutualMatchEvent(roomId, savedMatch.id(), me.userId(), selected.userId()));
        } else {
            eventPublisher.publishEvent(new MemberSelectedEvent(roomId, selected.userId()));
        }

        return new FinalSelectionResponse(selectedMemberId, matched);
    }

    /** 내 최종선택 제출 상태 조회(방 재진입 시 복원용). 미제출은 예외가 아니라 submitted=false로 응답한다. */
    public MyFinalSelectionResponse getMySelection(Long roomId, Long userId) {
        RoomMember me = roomMemberReader.getByRoomIdAndUserId(roomId, userId); // 멤버십 인가 게이트
        return finalSelectionReader.findByRoomMemberId(me.id())
                .map(selection -> new MyFinalSelectionResponse(true, selection.selectedMemberId(), selection.submittedAt()))
                .orElseGet(MyFinalSelectionResponse::notSubmitted);
    }

    /**
     * 최종선택 오픈 시각 = 어드민이 방마다 입력한 opens_at(새 모델). 시(hour)가 아닌 시각으로 판정한다.
     * DB에 잔존할 수 있는 구 모델 방(opens_at 미설정)은 마지막 날 마지막 슬롯 종료 시각(Day3 18:00)으로 방어 계산한다.
     */
    private LocalDateTime selectionOpensAt(Room room) {
        if (room.finalSelectionOpensAt() != null) {
            return room.finalSelectionOpensAt();
        }
        return room.legacySelectionOpensAt();
    }

    /** 최종선택 마감 = 어드민이 입력한 closes_at(새 모델). 구 모델 방은 결과 발표 시각(Room.announcedAt). */
    private LocalDateTime selectionClosesAt(Room room) {
        if (room.finalSelectionClosesAt() != null) {
            return room.finalSelectionClosesAt();
        }
        return room.announcedAt();
    }

    /** 상대(selectedMemberId)도 나(meId)를 최종선택했는지. 상대가 기권(selectedMemberId=null)했으면 미성립. */
    private boolean isMutual(Long selectedMemberId, Long meId) {
        return finalSelectionReader.findByRoomMemberId(selectedMemberId)
                .map(theirSelection -> theirSelection.selectedMemberId() != null
                        && theirSelection.selectedMemberId().equals(meId))
                .orElse(false);
    }
}
