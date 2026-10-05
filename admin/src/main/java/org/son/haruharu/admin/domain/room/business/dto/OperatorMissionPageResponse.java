package org.son.haruharu.admin.domain.room.business.dto;

import java.time.LocalDateTime;
import java.util.List;
import org.son.haruharu.domain.mission.implement.OperatorMission;
import org.son.haruharu.domain.mission.implement.OperatorMissionTemplate;
import org.son.haruharu.domain.room.implement.RoomStatus;

public record OperatorMissionPageResponse(Long roomId, RoomStatus roomStatus, LocalDateTime startsAt,
                                          LocalDateTime finalSelectionOpensAt, LocalDateTime serverNow,
                                          List<OperatorMissionTemplate> templates, List<OperatorMissionResponse> items,
                                          Long nextCursor) {}
