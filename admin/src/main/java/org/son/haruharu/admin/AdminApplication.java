package org.son.haruharu.admin;

import org.son.haruharu.config.ClockConfig;
import org.son.haruharu.config.JpaConfig;
import org.son.haruharu.config.TimeZoneConfig;
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
 * 컨텍스트를 오염시키므로, 필요한 패키지(대시보드: room/user)만 추가한다.
 * <p>
 * 리포지토리는 admin 도메인과 운영에 필요한 core storage 패키지만 스캔한다. 실제 도메인 빈은
 * {@link org.son.haruharu.admin.config.AdminRoomDomainConfig}에서 필요한 구현만 배선한다.
 */
@SpringBootApplication
@EntityScan({
        "org.son.haruharu.admin.domain",
        "org.son.haruharu.domain.room.storage",
        "org.son.haruharu.domain.user.storage",
        "org.son.haruharu.domain.profile.storage",
        "org.son.haruharu.domain.ticket.storage",
        "org.son.haruharu.domain.payment.storage",
        "org.son.haruharu.domain.video.storage",
        "org.son.haruharu.domain.chat.storage",
        "org.son.haruharu.domain.question.storage",
        // #486: 공통 미션(유형별 정의는 details JSON), 채팅 게시 계획
        "org.son.haruharu.domain.mission.common.storage",
        "org.son.haruharu.domain.mission.chat.storage",
        // #510: 모집 트랙·기수
        "org.son.haruharu.domain.cohort.storage"
})
@EnableJpaRepositories({"org.son.haruharu.admin.domain", "org.son.haruharu.domain.room.storage",
        // #321: RoomInviteService.join 의 유저 행 비관적 락(UserRepositoryImpl→UserJpaRepository) 배선.
        "org.son.haruharu.domain.user.storage",
        // #392: RoomInviteService.join 의 프로필 gender 스냅샷(UserProfileRepositoryImpl→UserProfileJpaRepository) 배선.
        "org.son.haruharu.domain.profile.storage",
        // #486: 미션·게시 계획 저장과 보낸 운영 글 삭제
        "org.son.haruharu.domain.chat.storage",
        "org.son.haruharu.domain.mission.common.storage",
        "org.son.haruharu.domain.mission.chat.storage",
        // #492: 운영진 티켓 지급(TicketService→지갑·원장 JpaRepository) 배선.
        "org.son.haruharu.domain.ticket.storage",
        // #510: 모집 트랙·기수 관리(RecruitTrackService·CohortService) 배선.
        "org.son.haruharu.domain.cohort.storage"})
@Import({JpaConfig.class, ClockConfig.class, TimeZoneConfig.class})
public class AdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }

}
