package org.son.haruharu.domain.cohort.business.event;

import java.util.List;

/** 신청자들이 방에 배정됐다(#516). 배정 알림은 후속 이슈에서 이 이벤트를 듣는다. */
public record CohortApplicationsAssignedEvent(Long roomId, List<Long> applicationIds, String assignedBy) {
}
