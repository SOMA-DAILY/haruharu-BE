package org.son.haruharu.domain.video.implement;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VideoUploadSessionWriter {

    private final VideoUploadSessionRepository videoUploadSessionRepository;

    public VideoUploadSession insert(VideoUploadSession session) {
        return videoUploadSessionRepository.insert(session);
    }

    public VideoUploadSession save(VideoUploadSession session) {
        return videoUploadSessionRepository.save(session);
    }
}
