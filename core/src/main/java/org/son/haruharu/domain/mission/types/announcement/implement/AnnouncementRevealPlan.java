package org.son.haruharu.domain.mission.types.announcement.implement;

import java.time.LocalDateTime;
import java.util.List;

/** 안내형 전용 공개 항목과 공통 일정의 읽기 모델. */
public record AnnouncementRevealPlan(Long missionId, LocalDateTime opensAt, LocalDateTime canceledAt,
                                      List<MissionRevealField> revealFields) {}
