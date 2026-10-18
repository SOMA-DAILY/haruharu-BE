package org.son.haruharu.admin.config;

import org.son.haruharu.domain.cohort.business.CohortApplicationClosingService;
import org.son.haruharu.domain.cohort.business.CohortAssignmentService;
import org.son.haruharu.domain.cohort.business.CohortService;
import org.son.haruharu.domain.cohort.business.RecruitTrackService;
import org.son.haruharu.domain.cohort.implement.CohortApplicationReader;
import org.son.haruharu.domain.cohort.implement.CohortApplicationWriter;
import org.son.haruharu.domain.cohort.implement.CohortReader;
import org.son.haruharu.domain.cohort.implement.CohortWriter;
import org.son.haruharu.domain.cohort.implement.RecruitTrackReader;
import org.son.haruharu.domain.cohort.implement.RecruitTrackWriter;
import org.son.haruharu.domain.cohort.storage.CohortApplicationRepositoryImpl;
import org.son.haruharu.domain.cohort.storage.CohortRepositoryImpl;
import org.son.haruharu.domain.cohort.storage.RecruitTrackRepositoryImpl;
import org.son.haruharu.domain.user.implement.UserReader;
import org.son.haruharu.domain.user.storage.UserRepositoryImpl;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 모집 트랙·기수 관리(#510)에 필요한 core cohort 빈만 명시적으로 배선한다.
 * 잠금 순서·상태 전이 규칙을 admin 에서 다시 만들지 않고 core 서비스를 그대로 쓴다.
 * 기수 취소가 배정 전 신청을 닫고 참가권을 돌려주므로(#511) 신청 Reader·Writer·종료 서비스도 함께 둔다
 * (TicketService 는 AdminTicketConfig 가 배선한다). 방에 넣기(#516)는 CohortAssignmentService 가 방 빈(AdminRoomDomainConfig)을 함께 쓴다.
 */
@Configuration
@Import({RecruitTrackService.class, CohortService.class, RecruitTrackReader.class, RecruitTrackWriter.class,
        CohortReader.class, CohortWriter.class, RecruitTrackRepositoryImpl.class, CohortRepositoryImpl.class,
        CohortApplicationReader.class, CohortApplicationWriter.class, CohortApplicationRepositoryImpl.class,
        CohortApplicationClosingService.class, CohortAssignmentService.class, UserReader.class,
        UserRepositoryImpl.class})
public class AdminCohortConfig {}
