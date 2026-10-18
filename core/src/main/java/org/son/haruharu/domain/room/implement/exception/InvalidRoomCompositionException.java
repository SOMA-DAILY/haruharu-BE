package org.son.haruharu.domain.room.implement.exception;

import org.son.haruharu.common.ApiFieldError;
import org.son.haruharu.common.exception.FieldValidationException;

import java.util.List;

/** 방 편성 정보(기수·이름·성별 정원)가 잘못됐을 때(#513). 잘못된 칸을 모아 한 번에 알려준다. */
public class InvalidRoomCompositionException extends FieldValidationException {

    public InvalidRoomCompositionException(String field, String message) {
        super(List.of(new ApiFieldError(field, message)));
    }

    public InvalidRoomCompositionException(List<ApiFieldError> fieldErrors) {
        super(fieldErrors);
    }
}
