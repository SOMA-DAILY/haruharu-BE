package org.son.haruharu.domain.video.implement;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import org.son.haruharu.domain.video.implement.exception.VideoUploadUnavailableException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VideoUploadSessionReader {

    private final VideoUploadSessionRepository videoUploadSessionRepository;

    public Optional<VideoUploadSession> findById(UUID id) {
        return videoUploadSessionRepository.findById(id);
    }

    /** 없는 세션은 만료·취소와 같게 취급한다(409). */
    public VideoUploadSession getById(UUID id) {
        return videoUploadSessionRepository.findById(id)
                .orElseThrow(VideoUploadUnavailableException::new);
    }

    public boolean hasPending(Long memberId, LocalDateTime slotStart, LocalDateTime now) {
        return videoUploadSessionRepository.existsPending(memberId, slotStart, now);
    }
}
