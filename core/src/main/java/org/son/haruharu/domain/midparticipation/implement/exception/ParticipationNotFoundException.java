package org.son.haruharu.domain.midparticipation.implement.exception;
import org.son.haruharu.common.exception.NotFoundException;
public class ParticipationNotFoundException extends NotFoundException {
    public ParticipationNotFoundException() { super("MID_PARTICIPATION_NOT_FOUND", "참여 안내를 찾을 수 없어요."); }
}
