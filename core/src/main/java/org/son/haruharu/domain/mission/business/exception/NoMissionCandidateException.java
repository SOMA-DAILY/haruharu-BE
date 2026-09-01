package org.son.haruharu.domain.mission.business.exception;

import org.son.haruharu.common.exception.ConflictException;

public class NoMissionCandidateException extends ConflictException {

    public NoMissionCandidateException() {
        super("MISSION_ASSIGNMENT_CANDIDATE_UNAVAILABLE", "배정 가능한 미션 후보가 없습니다.");
    }
}
