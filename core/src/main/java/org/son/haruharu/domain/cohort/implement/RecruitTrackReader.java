package org.son.haruharu.domain.cohort.implement;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.cohort.storage.exception.RecruitTrackNotFoundException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RecruitTrackReader {

    private final RecruitTrackRepository recruitTrackRepository;

    public RecruitTrack getById(Long id) {
        return recruitTrackRepository.findById(id).orElseThrow(RecruitTrackNotFoundException::new);
    }

    public RecruitTrack getByIdForUpdate(Long id) {
        return recruitTrackRepository.findByIdForUpdate(id).orElseThrow(RecruitTrackNotFoundException::new);
    }

    public List<RecruitTrack> findAll() {
        return recruitTrackRepository.findAll();
    }

    public List<RecruitTrack> findAllByIdIn(List<Long> ids) {
        if (ids.isEmpty()) return List.of();
        return recruitTrackRepository.findAllByIdIn(ids);
    }

    public boolean existsByKey(String key) {
        return recruitTrackRepository.existsByKey(key);
    }
}
