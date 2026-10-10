package org.son.haruharu.admin.config;

import org.son.haruharu.domain.chat.implement.ChatMessageReader;
import org.son.haruharu.domain.chat.implement.ChatMessageWriter;
import org.son.haruharu.domain.chat.storage.ChatMessageRepositoryImpl;
import org.son.haruharu.domain.mission.chat.business.MissionAnnouncementService;
import org.son.haruharu.domain.mission.chat.implement.MissionAnnouncementReader;
import org.son.haruharu.domain.mission.chat.implement.MissionAnnouncementWriter;
import org.son.haruharu.domain.mission.chat.storage.MissionAnnouncementRepositoryImpl;
import org.son.haruharu.domain.mission.common.business.MissionImageService;
import org.son.haruharu.domain.mission.common.business.RoomMissionService;
import org.son.haruharu.domain.mission.common.implement.MissionImageStore;
import org.son.haruharu.domain.mission.common.implement.MissionTypeRegistry;
import org.son.haruharu.domain.mission.common.implement.RoomMissionReader;
import org.son.haruharu.domain.mission.common.implement.RoomMissionWriter;
import org.son.haruharu.domain.mission.common.storage.RoomMissionRepositoryImpl;
import org.son.haruharu.domain.mission.types.announcement.implement.AnnouncementMissionHandler;
import org.son.haruharu.domain.mission.types.profilereveal.implement.ProfileRevealMissionHandler;
import org.son.haruharu.domain.mission.types.recording.implement.RecordingMissionHandler;
import org.son.haruharu.domain.profile.implement.UserProfileReader;
import org.son.haruharu.domain.profile.storage.UserProfileRepositoryImpl;
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
        // business — 방·초대코드 생성 유스케이스에 필요한 서비스만 가져온다.
        RoomService.class,
        RoomInviteService.class,
        // implement — 위 서비스가 쓰는 협력자만.
        RoomReader.class,
        RoomWriter.class,
        RoomMemberReader.class,
        RoomMemberWriter.class,
        RoomInviteReader.class,
        RoomInviteWriter.class,
        InviteCodeGenerator.class,
        // #392: RoomInviteService.join 이 프로필 gender 를 방 멤버로 스냅샷하는 데 필요.
        UserProfileReader.class,
        UserProfileRepositoryImpl.class,
        // storage — 위 협력자가 의존하는 포트 구현체(JpaRepository 는 AdminApplication 의 EnableJpaRepositories).
        RoomRepositoryImpl.class,
        RoomMemberRepositoryImpl.class,
        RoomInviteRepositoryImpl.class,
        // #486: 미션·안내 계획 저장(발송은 app 서버 잡) + 보낸 운영 글 삭제(chat_messages.admin_deleted_at).
        RoomMissionService.class, MissionAnnouncementService.class,
        // 미션 사진 업로드·확인. S3 어댑터는 AdminActivityMediaConfig 가 등록한다.
        MissionImageService.class, MissionImageStore.class,
        RoomMissionReader.class, RoomMissionWriter.class,
        MissionAnnouncementReader.class, MissionAnnouncementWriter.class,
        MissionTypeRegistry.class, AnnouncementMissionHandler.class, ProfileRevealMissionHandler.class,
        RecordingMissionHandler.class,
        RoomMissionRepositoryImpl.class, MissionAnnouncementRepositoryImpl.class,
        ChatMessageReader.class,
        ChatMessageWriter.class,
        ChatMessageRepositoryImpl.class
})
public class AdminRoomDomainConfig {
}
