package org.son.haruharu.admin.domain.room.business.command;

/** 방 멤버 성별 부여 입력(#318). 허용 값은 MALE·FEMALE — 검증은 AdminRoomCommandService 가 한다. */
public record AssignGenderCommand(String gender) {
}
