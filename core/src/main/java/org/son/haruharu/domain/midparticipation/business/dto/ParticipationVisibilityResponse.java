package org.son.haruharu.domain.midparticipation.business.dto;
import java.util.List;
public record ParticipationVisibilityResponse(String version,List<Long> removedRoomIds,List<Long> closedChatIds) {}
