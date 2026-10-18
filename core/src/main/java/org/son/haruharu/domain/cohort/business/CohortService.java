package org.son.haruharu.domain.cohort.business;

import org.son.haruharu.domain.cohort.business.command.CreateCohortCommand;
import org.son.haruharu.domain.cohort.business.command.UpdateCohortCommand;
import org.son.haruharu.domain.cohort.business.dto.CohortResponse;
import org.son.haruharu.domain.cohort.business.exception.CohortOpenAlreadyExistsException;
import org.son.haruharu.domain.cohort.business.exception.CohortSequenceDuplicateException;
import org.son.haruharu.domain.cohort.business.exception.RecruitTrackInactiveException;
import org.son.haruharu.domain.cohort.implement.Cohort;
import org.son.haruharu.domain.cohort.implement.CohortReader;
import org.son.haruharu.domain.cohort.implement.CohortStatus;
import org.son.haruharu.domain.cohort.implement.CohortWriter;
import org.son.haruharu.domain.cohort.implement.RecruitTrack;
import org.son.haruharu.domain.cohort.implement.RecruitTrackReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 기수 관리(#510). 같은 트랙의 기수 쓰기는 모두 트랙 행 잠금 → 기수 행 잠금 순서로 한 줄로 세운다.
 * 그래서 "트랙당 OPEN 1개"와 "(트랙, 번호) 유일"을 앱에서 먼저 확인할 수 있고,
 * DB 의 부분 유니크(ux_cohorts_track_open)·유니크(uq_cohorts_track_sequence)가 최종으로 막는다.
 */
@Service
@Transactional(readOnly = true)
public class CohortService {

    private final RecruitTrackReader trackReader;
    private final CohortReader cohortReader;
    private final CohortWriter cohortWriter;
    private final Clock clock;

    public CohortService(RecruitTrackReader trackReader, CohortReader cohortReader, CohortWriter cohortWriter,
                         Clock clock) {
        this.trackReader = trackReader;
        this.cohortReader = cohortReader;
        this.cohortWriter = cohortWriter;
        this.clock = clock;
    }

    /**
     * 조건은 null 이면 거르지 않는다. 트랙 id 순, 같은 트랙은 최신 기수부터.
     * 기수는 트랙마다 한 달에 한두 개씩 운영진이 만드는 소수라 페이징하지 않는다. 수가 늘면 trackId 필터를 필수로 바꾼다.
     */
    public List<CohortResponse> getCohorts(Long trackId, CohortStatus status) {
        List<Cohort> cohorts = cohortReader.findAll(trackId, status);
        Map<Long, RecruitTrack> tracks = trackReader.findAllByIdIn(
                        cohorts.stream().map(Cohort::trackId).distinct().toList()).stream()
                .collect(Collectors.toMap(RecruitTrack::id, Function.identity()));
        LocalDateTime now = now();
        return cohorts.stream().map(cohort -> CohortResponse.of(cohort, tracks.get(cohort.trackId()), now)).toList();
    }

    /** 초안으로 만든다. 비활성 트랙에도 미리 만들어 둘 수 있다(공개만 막는다). */
    @Transactional
    public CohortResponse create(CreateCohortCommand command, String actor) {
        Cohort cohort = Cohort.create(command.trackId(), command.sequence(), command.applyOpensAt(),
                command.applyClosesAt(), command.assignDeadlineAt(), command.periodLabel(), command.targetMale(),
                command.targetFemale(), actor);
        RecruitTrack track = trackReader.getByIdForUpdate(command.trackId());
        if (cohortReader.existsSequence(track.id(), cohort.sequence())) throw new CohortSequenceDuplicateException();
        return CohortResponse.of(cohortWriter.save(cohort), track, now());
    }

    @Transactional
    public CohortResponse update(Long cohortId, UpdateCohortCommand command) {
        Locked locked = lock(cohortId);
        Cohort revised = locked.cohort().revise(command.sequence(), command.applyOpensAt(), command.applyClosesAt(),
                command.assignDeadlineAt(), command.periodLabel(), command.targetMale(), command.targetFemale());
        if (revised.sequence() != locked.cohort().sequence()
                && cohortReader.existsSequence(revised.trackId(), revised.sequence())) {
            throw new CohortSequenceDuplicateException();
        }
        return CohortResponse.of(cohortWriter.save(revised), locked.track(), now());
    }

    /** DRAFT → OPEN. 트랙이 활성이고, 그 트랙에 OPEN 기수가 없을 때만. */
    @Transactional
    public CohortResponse publish(Long cohortId) {
        Locked locked = lock(cohortId);
        if (!locked.track().active()) throw new RecruitTrackInactiveException();
        Cohort opened = locked.cohort().publish(now());
        if (cohortReader.existsOpen(opened.trackId())) throw new CohortOpenAlreadyExistsException();
        return CohortResponse.of(cohortWriter.save(opened), locked.track(), now());
    }

    /** DRAFT·OPEN → CANCELLED. 신청자 반환은 신청 기능(#511 이후)에서 이 전이에 붙인다. */
    @Transactional
    public CohortResponse cancel(Long cohortId) {
        Locked locked = lock(cohortId);
        return CohortResponse.of(cohortWriter.save(locked.cohort().cancel()), locked.track(), now());
    }

    /**
     * 트랙 → 기수 순서로 잠근다. 트랙 id 는 스칼라로 먼저 읽는다 — 기수 엔티티를 미리 읽어 두면
     * 잠금 조회가 영속성 컨텍스트의 오래된 값을 돌려준다.
     */
    private Locked lock(Long cohortId) {
        Long trackId = cohortReader.getTrackId(cohortId);
        RecruitTrack track = trackReader.getByIdForUpdate(trackId);
        return new Locked(track, cohortReader.getByIdForUpdate(cohortId));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private record Locked(RecruitTrack track, Cohort cohort) {
    }
}
