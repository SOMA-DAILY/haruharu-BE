package org.son.haruharu.admin;

import org.son.haruharu.config.JpaConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 운영 어드민 서버 진입점(ECS Admin).
 * <p>
 * core 모듈의 엔티티·JPA 리포지토리를 공유 RDS로 배선한다. 유저용 서버(app)와 달리
 * 웹소켓(STOMP)·스케줄러(@Scheduled)는 클래스패스에 없으므로 이 서버에서 기동되지 않는다.
 * staff 계정 인증(RBAC)·어드민 API는 후속 이슈에서 이 모듈에 추가한다.
 */
@SpringBootApplication
@EntityScan("org.son.haruharu.domain")
@EnableJpaRepositories("org.son.haruharu.domain")
@Import(JpaConfig.class)
public class AdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }

}
