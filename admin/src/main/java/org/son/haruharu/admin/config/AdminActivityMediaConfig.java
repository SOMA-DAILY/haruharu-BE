package org.son.haruharu.admin.config;

import org.son.haruharu.config.S3Config;
import org.son.haruharu.domain.profile.implement.UserImageReader;
import org.son.haruharu.domain.profile.storage.UserImageRepositoryImpl;
import org.son.haruharu.infrastructure.s3.S3StorageAdapter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** 비공개 영상·썸네일·참여자 대표사진 조회에 필요한 빈만 가져온다. */
@Configuration
@Import({S3Config.class, S3StorageAdapter.class, UserImageReader.class, UserImageRepositoryImpl.class})
public class AdminActivityMediaConfig {}
