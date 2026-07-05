package org.son.haruharu.domain.video.business.exception;

import org.son.haruharu.common.exception.BadRequestException;
import org.son.haruharu.domain.video.implement.ReactionType;

import java.util.Arrays;
import java.util.stream.Collectors;

public class InvalidReactionTypeException extends BadRequestException {

    public InvalidReactionTypeException() {
        super("VIDEO_INVALID_REACTION_TYPE", "지원하지 않는 반응 유형입니다. (" + supportedTypes() + ")");
    }

    private static String supportedTypes() {
        return Arrays.stream(ReactionType.values()).map(Enum::name).collect(Collectors.joining(", "));
    }
}
