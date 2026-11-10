package org.son.haruharu.domain.replyrecommendation.implement;

import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 지금 쓰는 버전(작업기가 등록·교체한다). */
@Component
@RequiredArgsConstructor
public class ActiveVersionReader {

    private final AnalysisPipelineRepository pipeline;

    public Optional<ActiveVersion> findActive() {
        return pipeline.findActiveVersion();
    }
}
