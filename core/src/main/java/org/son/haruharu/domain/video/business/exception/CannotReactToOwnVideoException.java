package org.son.haruharu.domain.video.business.exception;

import org.son.haruharu.common.exception.BadRequestException;

public class CannotReactToOwnVideoException extends BadRequestException {

    public CannotReactToOwnVideoException() {
        super("CANNOT_REACT_OWN_VIDEO", "자기 자신의 영상에는 반응할 수 없습니다.");
    }
}
