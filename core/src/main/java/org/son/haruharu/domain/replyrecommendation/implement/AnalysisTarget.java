package org.son.haruharu.domain.replyrecommendation.implement;

/** 분석 요청 키(영상·시청 범위·캡션·미디어·전처리 버전)와 만들 때 필요한 값. */
public record AnalysisTarget(
        Long videoId,
        Long roomId,
        Long authorId,
        AccessScope scope,
        String captionRevision,
        String mediaRevision,
        String preprocessVersion
) {}
