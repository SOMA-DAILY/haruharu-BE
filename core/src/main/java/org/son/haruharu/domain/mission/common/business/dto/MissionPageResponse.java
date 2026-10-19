package org.son.haruharu.domain.mission.common.business.dto;
import java.util.List;
public record MissionPageResponse(List<RoomMissionResponse> missions, Long nextCursor) {}
