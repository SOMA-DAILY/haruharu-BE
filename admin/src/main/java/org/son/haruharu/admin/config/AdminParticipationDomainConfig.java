package org.son.haruharu.admin.config;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.son.haruharu.domain.midparticipation.business.ParticipationAdminService;
import org.son.haruharu.domain.midparticipation.implement.*;
import org.son.haruharu.domain.midparticipation.storage.ParticipationRepositoryImpl;
import org.son.haruharu.domain.room.business.RoomMemberRemovalService;
import org.son.haruharu.domain.room.implement.RoomParticipationGate;
import org.son.haruharu.domain.room.storage.RoomParticipationRepositoryImpl;
import org.son.haruharu.domain.user.implement.UserReader;
import org.son.haruharu.domain.user.storage.UserRepositoryImpl;
@Configuration
@Import({ParticipationAdminService.class,ParticipationReader.class,ParticipationWriter.class,
        ParticipationRepositoryImpl.class,RoomParticipationGate.class,RoomParticipationRepositoryImpl.class,
        RoomMemberRemovalService.class,UserReader.class,UserRepositoryImpl.class})
public class AdminParticipationDomainConfig {}
