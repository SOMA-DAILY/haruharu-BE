package org.son.haruharu.admin.domain.mission.business.command;

/** 부분 수정 — null이 아닌 필드만 갱신한다. */
public record UpdateMissionCommand(
        String description,
        Integer points,
        Boolean active
) {
}
