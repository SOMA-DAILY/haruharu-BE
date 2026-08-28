package org.son.haruharu.admin.config;

import org.son.haruharu.domain.mission.business.RoomMissionSlotService;
import org.son.haruharu.domain.mission.implement.RoomMissionSlotWriter;
import org.son.haruharu.domain.mission.storage.RoomMissionSlotRepositoryImpl;
import org.son.haruharu.domain.room.business.RoomInviteService;
import org.son.haruharu.domain.room.business.RoomService;
import org.son.haruharu.domain.room.implement.InviteCodeGenerator;
import org.son.haruharu.domain.room.implement.RoomInviteReader;
import org.son.haruharu.domain.room.implement.RoomInviteWriter;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomMemberWriter;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomWriter;
import org.son.haruharu.domain.room.storage.RoomInviteRepositoryImpl;
import org.son.haruharu.domain.room.storage.RoomMemberRepositoryImpl;
import org.son.haruharu.domain.room.storage.RoomRepositoryImpl;
import org.son.haruharu.domain.user.implement.UserReader;
import org.son.haruharu.domain.user.storage.UserRepositoryImpl;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * 어드민이 방을 <b>생성·운영</b>하기 위한 core room 빈 배선(#318).
 * <p>
 * 어드민은 지금까지 core 데이터를 자체 JPQL(Admin*QueryRepository)로 <b>읽기만</b> 했지만,
 * 방 생성·코드 발급은 이미 core 에 있는 도메인 로직(#311·#312)이라 중복 구현하지 않고 그대로 쓴다.
 * <p>
 * 패키지 스캔이 아니라 <b>필요한 빈만 명시적으로 열거</b>한다 — AdminApplication 의 EntityScan 과 같은 방침이다.
 * 스캔하면 이 기능과 무관한 MatchResult·FinalSelection 협력자까지 어드민 컨텍스트에
 * 딸려오고, 나중에 그쪽에 타 도메인 의존이 생기면 어드민이 조용히 깨진다.
 */
@Configuration
@Import({
        // business — 방·미션 슬롯·초대코드 생성 유스케이스에 필요한 서비스만 가져온다.
        RoomService.class,
        RoomInviteService.class,
        RoomMissionSlotService.class,
        // implement — 위 서비스가 쓰는 협력자만.
        RoomReader.class,
        RoomWriter.class,
        RoomMemberReader.class,
        RoomMemberWriter.class,
        RoomInviteReader.class,
        RoomInviteWriter.class,
        InviteCodeGenerator.class,
        RoomMissionSlotWriter.class,
        // RoomInviteService.join 이 유저 행 비관적 락(#321)을 걸어 UserReader 에 의존한다. admin 은 join 을
        // 호출하지 않지만 생성자 주입이라 빈이 있어야 컨텍스트가 뜬다 → UserReader·포트 구현체를 함께 배선.
        UserReader.class,
        // storage — 위 협력자가 의존하는 포트 구현체(JpaRepository 는 AdminApplication 의 EnableJpaRepositories).
        RoomRepositoryImpl.class,
        RoomMemberRepositoryImpl.class,
        RoomInviteRepositoryImpl.class,
        RoomMissionSlotRepositoryImpl.class,
        UserRepositoryImpl.class
})
public class AdminRoomDomainConfig {
}
