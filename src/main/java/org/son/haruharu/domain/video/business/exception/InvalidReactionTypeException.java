package org.son.haruharu.domain.video.business.exception;

import org.son.haruharu.common.exception.BadRequestException;

public class InvalidReactionTypeException extends BadRequestException {

    public InvalidReactionTypeException() {
        super("VIDEO_INVALID_REACTION_TYPE", "지원하지 않는 반응 유형입니다. (LIKE, CURIOUS, EMPATHY)");
    }
}
