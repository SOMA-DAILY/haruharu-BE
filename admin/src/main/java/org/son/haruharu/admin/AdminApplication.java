package org.son.haruharu.admin;

import org.son.haruharu.config.ClockConfig;
import org.son.haruharu.config.JpaConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * 운영 어드민 서버 진입점(ECS Admin).
 * <p>
 * 공유 RDS에 admin 전용 도메인(admin_accounts 등)을 배선한다. 유저용 서버(app)와 달리
 * 웹소켓(STOMP)·스케줄러(@Scheduled)는 클래스패스에 없어 이 서버에서 기동되지 않는다.
 * <p>
 * 엔티티 스캔은 admin 도메인 + 어드민이 실제로 조회하는 core 엔티티 패키지만 열거한다.
 * core 전체를 스캔하면 identity 등 core 전용 빈(CiDiCryptoConverter→IdentityCrypto)까지 끌어와
 * 컨텍스트를 오염시키므로, 필요한 패키지(대시보드: room/matching/user)만 추가한다.
 * 리포지토리 스캔은 admin 도메인 한정(core 데이터는 admin 자체 JPQL 쿼리로 읽는다).
 */
@SpringBootApplication
@EntityScan({
        "org.son.haruharu.admin.domain",
        "org.son.haruharu.domain.room.storage",
        "org.son.haruharu.domain.matching.storage",
        "org.son.haruharu.domain.user.storage"
})
@EnableJpaRepositories("org.son.haruharu.admin.domain")
@Import({JpaConfig.class, ClockConfig.class})
public class AdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }

}
