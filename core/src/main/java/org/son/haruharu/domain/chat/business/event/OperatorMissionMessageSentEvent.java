package org.son.haruharu.domain.chat.business.event;

import java.util.List;

public record OperatorMissionMessageSentEvent(Long missionId, Long chatRoomId, List<Long> recipients) {}
