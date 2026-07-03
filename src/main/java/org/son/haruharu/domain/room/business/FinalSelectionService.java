package org.son.haruharu.domain.room.business;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

import org.son.haruharu.domain.mission.implement.UploadSlot;
import org.son.haruharu.domain.room.business.dto.FinalSelectionResponse;
import org.son.haruharu.domain.room.business.exception.AlreadySelectedException;
import org.son.haruharu.domain.room.business.exception.CannotSelectSelfException;
import org.son.haruharu.domain.room.business.exception.NotFinalSelectionDayException;
import org.son.haruharu.domain.room.business.exception.RoomNotFoundException;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Day3 최종선택. 멤버당 1회 확정(변경 불가)이며, 상대도 나를 선택했으면
 * 즉시 상호매칭(MatchResult, canonical ordering 1쌍 1행)을 성사시킨다.
 */
@Service
@Transactional(readOnly = true)
public class FinalSelectionService {

    /** 최종선택이 열리는 Day. 기본 프로필 공개(PROFILE_REVEAL_DAY)와 동일 기준. */
    private static final int FINAL_SELECTION_DAY = 3;

    private final RoomReader roomReader;
    private final RoomMemberReader roomMemberReader;
    private final FinalSelectionReader finalSelectionReader;
    private final FinalSelectionWriter finalSelectionWriter;
    private final MatchResultWriter matchResultWriter;
    private final Clock clock;

    public FinalSelectionService(RoomReader roomReader, RoomMemberReader roomMemberReader,
                                 FinalSelectionReader finalSelectionReader, FinalSelectionWriter finalSelectionWriter,
                                 MatchResultWriter matchResultWriter, Clock clock) {
        this.roomReader = roomReader;
        this.roomMemberReader = roomMemberReader;
        this.finalSelectionReader = finalSelectionReader;
        this.finalSelectionWriter = finalSelectionWriter;
        this.matchResultWriter = matchResultWriter;
        this.clock = clock;
    }

    @Transactional
    public FinalSelectionResponse select(UUID roomId, UUID selectedMemberId, Long userId) {
        RoomMember me = roomMemberReader.getByRoomIdAndUserId(roomId, userId); // 멤버십 인가 게이트
        Room room = roomReader.findById(roomId)
                .orElseThrow(RoomNotFoundException::new);
        if (room.currentDayAt(LocalDateTime.now(clock), UploadSlot.dayStartHour()) < FINAL_SELECTION_DAY) {
            throw new NotFinalSelectionDayException();
        }
        if (selectedMemberId.equals(me.id())) {
            throw new CannotSelectSelfException();
        }
        RoomMember selected = roomMemberReader.getById(selectedMemberId);
        if (!selected.roomId().equals(roomId)) {
            throw new RoomMemberNotFoundException(); // 같은 방 멤버만 선택 가능
        }
        if (finalSelectionReader.hasSelected(me.id())) {
            throw new AlreadySelectedException(); // 1회 확정
        }

        LocalDateTime now = LocalDateTime.now(clock);
        finalSelectionWriter.save(FinalSelection.of(me.id(), selectedMemberId, now));

        boolean matched = isMutual(selectedMemberId, me.id());
        if (matched) {
            matchResultWriter.save(MatchResult.of(me.id(), selectedMemberId, now));
        }

        return new FinalSelectionResponse(selectedMemberId, matched);
    }

    /** 상대(selectedMemberId)도 나(meId)를 최종선택했는지. */
    private boolean isMutual(UUID selectedMemberId, UUID meId) {
        return finalSelectionReader.findByRoomMemberId(selectedMemberId)
                .map(theirSelection -> theirSelection.selectedMemberId().equals(meId))
                .orElse(false);
    }
}
