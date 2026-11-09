package org.son.haruharu.domain.replyrecommendation.implement;

/** 지금 쓰는 모델·프롬프트·전처리·참고 사례 묶음. 작업기가 등록하고 앱은 읽기만 한다. */
public record ActiveVersion(String versionId, long revision, String preprocessVersion, String gallerySnapshotId) {}
