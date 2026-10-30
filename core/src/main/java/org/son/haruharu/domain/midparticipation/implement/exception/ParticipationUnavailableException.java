package org.son.haruharu.domain.midparticipation.implement.exception;
import org.son.haruharu.common.exception.ConflictException;
public class ParticipationUnavailableException extends ConflictException {
    public ParticipationUnavailableException() { super("MID_PARTICIPATION_UNAVAILABLE", "마감되었거나 더 이상 참여할 수 없는 제안이에요."); }
    public ParticipationUnavailableException(String message) { super("MID_PARTICIPATION_UNAVAILABLE", message); }
}
