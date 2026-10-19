package org.son.haruharu.domain.cohort.business;

import org.son.haruharu.domain.cohort.business.command.CreateRecruitTrackCommand;
import org.son.haruharu.domain.cohort.business.command.UpdateRecruitTrackCommand;
import org.son.haruharu.domain.cohort.business.dto.RecruitTrackResponse;
import org.son.haruharu.domain.cohort.business.dto.RecruitingTrackResponse;
import org.son.haruharu.domain.cohort.business.dto.RecruitingTracksResponse;
import org.son.haruharu.domain.cohort.business.exception.RecruitTrackHasOpenCohortException;
import org.son.haruharu.domain.cohort.business.exception.RecruitTrackKeyDuplicateException;
import org.son.haruharu.domain.cohort.implement.Cohort;
import org.son.haruharu.domain.cohort.implement.CohortReader;
import org.son.haruharu.domain.cohort.implement.RecruitTrack;
import org.son.haruharu.domain.cohort.implement.RecruitTrackReader;
import org.son.haruharu.domain.cohort.implement.RecruitTrackWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 모집 트랙 관리(어드민)와 앱의 모집 중인 트랙 조회(#510). */
@Service
@Transactional(readOnly = true)
public class RecruitTrackService {

    private final RecruitTrackReader trackReader;
    private final RecruitTrackWriter trackWriter;
    private final CohortReader cohortReader;
    private final Clock clock;

    public RecruitTrackService(RecruitTrackReader trackReader, RecruitTrackWriter trackWriter,
                               CohortReader cohortReader, Clock clock) {
        this.trackReader = trackReader;
        this.trackWriter = trackWriter;
        this.cohortReader = cohortReader;
        this.clock = clock;
    }

    /** 트랙은 손으로 만드는 소수의 카탈로그라 페이징하지 않는다(어드민 상품 목록과 같은 방침). */
    public List<RecruitTrackResponse> getTracks() {
        return trackReader.findAll().stream().map(RecruitTrackResponse::from).toList();
    }

    @Transactional
    public RecruitTrackResponse create(CreateRecruitTrackCommand command) {
        RecruitTrack track = RecruitTrack.create(command.key(), command.label(), command.region(), command.audience(),
                command.minAge(), command.maxAge(), command.questions(), command.displayOrder());
        // 동시에 같은 키로 만들면 uq_recruit_tracks_key 가 최종으로 막는다(409).
        if (trackReader.existsByKey(track.key())) throw new RecruitTrackKeyDuplicateException();
        return RecruitTrackResponse.from(trackWriter.save(track));
    }

    /** 트랙 행을 잠그고 고친다. 공개 중인 기수가 있으면 비활성화하지 않는다 — 앱에서 신청 중인 기수가 사라지지 않게. */
    @Transactional
    public RecruitTrackResponse update(Long trackId, UpdateRecruitTrackCommand command) {
        RecruitTrack track = trackReader.getByIdForUpdate(trackId);
        RecruitTrack revised = track.revise(command.label(), command.region(), command.audience(), command.minAge(),
                command.maxAge(), command.questions(), command.displayOrder(), command.active());
        if (track.active() && !revised.active() && cohortReader.existsOpen(trackId)) {
            throw new RecruitTrackHasOpenCohortException();
        }
        return RecruitTrackResponse.from(trackWriter.save(revised));
    }

    /**
     * 앱 트랙 목록. OPEN 기수가 있는 활성 트랙만 표시 순서대로 내린다.
     * 신청 시작 전·마감 후의 OPEN 기수도 보여 주고 is_accepting 으로 구분한다(앱이 "모집 예정"·"마감"을 그린다).
     */
    public RecruitingTracksResponse getRecruitingTracks() {
        LocalDateTime now = LocalDateTime.now(clock);
        // 트랙당 OPEN 1개는 SQL 의 부분 유니크가 지킨다. 그 인덱스가 없는 환경에서 둘이 생겨도 500 대신 최신 기수를 보여 준다.
        Map<Long, Cohort> openByTrack = cohortReader.findAllOpen().stream()
                .collect(Collectors.toMap(Cohort::trackId, Function.identity(),
                        (a, b) -> a.sequence() >= b.sequence() ? a : b));
        List<RecruitingTrackResponse> tracks = trackReader.findAllByIdIn(List.copyOf(openByTrack.keySet())).stream()
                .filter(RecruitTrack::active)
                .sorted(Comparator.comparingInt(RecruitTrack::displayOrder).thenComparing(RecruitTrack::id))
                .map(track -> RecruitingTrackResponse.of(track, openByTrack.get(track.id()), now))
                .toList();
        return new RecruitingTracksResponse(tracks);
    }
}
