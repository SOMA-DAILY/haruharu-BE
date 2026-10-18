package org.son.haruharu.domain.cohort.implement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RecruitTrackWriter {

    private final RecruitTrackRepository recruitTrackRepository;

    public RecruitTrack save(RecruitTrack track) {
        return recruitTrackRepository.save(track);
    }
}
