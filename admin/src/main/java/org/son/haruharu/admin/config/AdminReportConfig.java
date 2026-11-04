package org.son.haruharu.admin.config;

import org.son.haruharu.domain.auth.implement.RefreshTokenWriter;
import org.son.haruharu.domain.auth.storage.RefreshTokenRepositoryImpl;
import org.son.haruharu.domain.user.implement.UserWriter;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 신고 처리(#553)에 필요한 core 빈만 명시적으로 배선한다. UserReader·UserRepositoryImpl 은 AdminParticipationDomainConfig 가 이미 가져온다.
 * 신고 읽기·전이는 admin 전용 JPQL(AdminReportQueryRepository)이라 core report 빈은 필요 없다.
 * 제재 = users.status BANNED(UserWriter) + 리프레시 토큰 폐기(RefreshTokenWriter, JpaRepository 는 AdminApplication 의 EnableJpaRepositories).
 */
@Configuration
@Import({UserWriter.class, RefreshTokenWriter.class, RefreshTokenRepositoryImpl.class})
public class AdminReportConfig {
}
