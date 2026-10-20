package org.son.haruharu.admin.domain.cohort.implement;

import org.son.haruharu.admin.domain.cohort.storage.AdminCohortApplicationQueryRepository;
import org.son.haruharu.domain.cohort.implement.ApplicantGender;
import org.son.haruharu.domain.cohort.implement.CohortApplication;
import org.son.haruharu.domain.cohort.implement.CohortApplicationStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** 기수 신청자 표 조회 협력자(#515). 트랜잭션 경계는 Service 가 갖는다. */
@Component
public class AdminCohortApplicationReader {

    private final AdminCohortApplicationQueryRepository queryRepository;

    public AdminCohortApplicationReader(AdminCohortApplicationQueryRepository queryRepository) {
        this.queryRepository = queryRepository;
    }

    public List<CohortApplicant> findApplicants(Long cohortId, CohortApplicationStatus status, ApplicantGender gender,
                                                int offset, int limit) {
        return withPeople(queryRepository.findApplications(cohortId, status, gender, offset, limit));
    }

    public long countApplicants(Long cohortId, CohortApplicationStatus status, ApplicantGender gender) {
        return queryRepository.countApplications(cohortId, status, gender);
    }

    public CohortApplicant applicantOf(CohortApplication application) {
        return withPeople(List.of(application)).get(0);
    }

    private List<CohortApplicant> withPeople(List<CohortApplication> applications) {
        Set<Long> userIds = applications.stream().map(CohortApplication::userId).collect(Collectors.toSet());
        Map<Long, String> names = queryRepository.findNames(userIds);
        Map<Long, String> phones = queryRepository.findPhones(userIds);
        Map<Long, List<Long>> roomIds = queryRepository.findRoomIds(userIds);
        return applications.stream()
                .map(a -> new CohortApplicant(a, names.get(a.userId()), phones.get(a.userId()),
                        roomIds.getOrDefault(a.userId(), List.of())))
                .toList();
    }
}
