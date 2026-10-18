package org.son.haruharu.domain.cohort.implement;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.cohort.storage.exception.CohortNotFoundException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CohortReader {

    private final CohortRepository cohortRepository;

    public Cohort getById(Long id) {
        return cohortRepository.findById(id).orElseThrow(CohortNotFoundException::new);
    }

    /**
     * 기수 행 잠금. 잠금 순서는 트랙 → 기수 → 방 → 신청 → 지갑이다. 기수를 잠근 뒤 트랙을 잠그면 어드민 공개와 교착하므로,
     * 기수만 필요한 경로(신청 등)는 트랙을 잠그지 않는다. 같은 트랜잭션에서 이 기수를 먼저 읽어 두면 잠금 조회가
     * 오래된 값을 돌려주므로 id 나 {@link #getTrackId} 로 시작한다.
     */
    public Cohort getByIdForUpdate(Long id) {
        return cohortRepository.findByIdForUpdate(id).orElseThrow(CohortNotFoundException::new);
    }

    /**
     * 신청 경로의 기수 공유 잠금(#511). 신청끼리는 막지 않고, 같은 기수의 취소·정산(배타 잠금)과는 한 줄로 세운다.
     * 트랙은 잠그지 않는다 — 어드민 공개(트랙 → 기수)와 교착하지 않게.
     */
    public Cohort getByIdForShare(Long id) {
        return cohortRepository.findByIdForShare(id).orElseThrow(CohortNotFoundException::new);
    }

    public Long getTrackId(Long id) {
        return cohortRepository.findTrackIdById(id).orElseThrow(CohortNotFoundException::new);
    }

    public List<Cohort> findAll(Long trackId, CohortStatus status) {
        return cohortRepository.findAll(trackId, status);
    }

    public List<Cohort> findAllOpen() {
        return cohortRepository.findAllByStatus(CohortStatus.OPEN);
    }

    public boolean existsSequence(Long trackId, int sequence) {
        return cohortRepository.existsByTrackIdAndSequence(trackId, sequence);
    }

    public boolean existsOpen(Long trackId) {
        return cohortRepository.existsByTrackIdAndStatus(trackId, CohortStatus.OPEN);
    }
}
