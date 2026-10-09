package org.son.haruharu.domain.video.implement;

import java.time.LocalDateTime;
import java.util.List;
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

    public List<VideoUploadSession> findPending(Long memberId, LocalDateTime slotStart, LocalDateTime now) {
        return videoUploadSessionRepository.findPending(memberId, slotStart, now);
    }

    public List<VideoUploadSession> findPendingByMember(Long memberId, LocalDateTime now) {
        return videoUploadSessionRepository.findPendingByMember(memberId, now);
    }
}
