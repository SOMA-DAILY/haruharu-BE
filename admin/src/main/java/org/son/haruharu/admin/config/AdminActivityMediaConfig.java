package org.son.haruharu.admin.config;

import org.son.haruharu.config.S3Config;
import org.son.haruharu.infrastructure.s3.S3StorageAdapter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** 비공개 영상/썸네일 조회 URL 발급에 필요한 어댑터만 가져온다. */
@Configuration
@Import({S3Config.class, S3StorageAdapter.class})
public class AdminActivityMediaConfig {}
