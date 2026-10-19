package org.son.haruharu.admin.config;
import org.son.haruharu.domain.appupdate.business.AppUpdatePolicyService;
import org.son.haruharu.domain.appupdate.implement.AppUpdatePolicyReader;
import org.son.haruharu.domain.appupdate.implement.AppUpdatePolicyWriter;
import org.son.haruharu.domain.appupdate.storage.AppUpdatePolicyRepositoryImpl;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
@Configuration
@Import({AppUpdatePolicyService.class, AppUpdatePolicyReader.class, AppUpdatePolicyWriter.class, AppUpdatePolicyRepositoryImpl.class})
public class AdminAppUpdatePolicyConfig {}
