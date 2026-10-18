package org.son.haruharu.domain.cohort.business;

import org.son.haruharu.domain.cohort.business.dto.AssignedMemberResponse;
import org.son.haruharu.domain.cohort.business.event.CohortApplicationsAssignedEvent;
import org.son.haruharu.domain.cohort.business.exception.CohortApplicationNotAssignableException;
import org.son.haruharu.domain.cohort.business.exception.RoomGenderCapacityExceededException;
import org.son.haruharu.domain.cohort.business.exception.RoomNotAssignableException;
import org.son.haruharu.domain.cohort.implement.ApplicantGender;
import org.son.haruharu.domain.cohort.implement.CohortApplication;
import org.son.haruharu.domain.cohort.implement.CohortApplicationReader;
import org.son.haruharu.domain.cohort.implement.CohortApplicationWriter;
import org.son.haruharu.domain.cohort.storage.exception.CohortApplicationNotFoundException;
import org.son.haruharu.domain.room.business.RoomMemberJoiner;
import org.son.haruharu.domain.room.business.exception.RoomNotFoundException;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomComposition;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.son.haruharu.domain.room.implement.RoomReader;
import org.son.haruharu.domain.room.implement.RoomStatus;
import org.son.haruharu.domain.user.implement.UserReader;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 편성 대기 신청자를 방에 넣는 단일 경로(#516). 지금은 운영진("admin:{id}")이 부르고, 나중 자동 매칭도 묶음만 정해 이걸 "system" 으로 부른다.
 * 잠금 순서 방 → 신청(id 순). 하나라도 어긋나면 전부 거절한다(같은 트랜잭션). 참가권은 신청 때 이미 썼으므로 건드리지 않는다.
 */
@Service
@Transactional(readOnly = true)
public class CohortAssignmentService {

    private final RoomReader roomReader;
    private final RoomMemberReader roomMemberReader;
    private final RoomMemberJoiner roomMemberJoiner;
    private final UserReader userReader;
    private final CohortApplicationReader applicationReader;
    private final CohortApplicationWriter applicationWriter;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public CohortAssignmentService(RoomReader roomReader, RoomMemberReader roomMemberReader,
                                   RoomMemberJoiner roomMemberJoiner, UserReader userReader,
                                   CohortApplicationReader applicationReader,
                                   CohortApplicationWriter applicationWriter, ApplicationEventPublisher events,
                                   Clock clock) {
        this.roomReader = roomReader;
        this.roomMemberReader = roomMemberReader;
        this.roomMemberJoiner = roomMemberJoiner;
        this.userReader = userReader;
        this.applicationReader = applicationReader;
        this.applicationWriter = applicationWriter;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public List<AssignedMemberResponse> assign(Long roomId, List<Long> applicationIds, String actor) {
        Room room = roomReader.findByIdForUpdate(roomId).orElseThrow(RoomNotFoundException::new);
        LocalDateTime now = LocalDateTime.now(clock);
        RoomStatus status = room.statusAt(now);
        RoomComposition composition = room.composition();
        if (composition.cohortId() == null || status == RoomStatus.ENDED || status == RoomStatus.CANCELLED) {
            throw new RoomNotAssignableException();
        }

        List<Long> ids = applicationIds.stream().distinct().toList();
        List<CohortApplication> applications = applicationReader.lockAll(ids);
        if (applications.size() != ids.size()) {
            throw new CohortApplicationNotFoundException();
        }
        // 탈퇴·정지한 유저는 신청이 열린 채 남아도 넣지 않는다(탈퇴가 신청을 닫지 않는다).
        Set<Long> activeUserIds = userReader.findActiveIds(applications.stream().map(CohortApplication::userId).toList());

        Map<ApplicantGender, Long> incoming = new EnumMap<>(ApplicantGender.class);
        Map<Long, ApplicantGender> genders = new HashMap<>();
        for (CohortApplication application : applications) {
            ApplicantGender gender = application.assignableGender()
                    .filter(g -> application.cohortId().equals(composition.cohortId()))
                    .filter(g -> activeUserIds.contains(application.userId()))
                    .filter(g -> roomMemberReader.findByRoomIdAndUserId(roomId, application.userId()).isEmpty())
                    .orElseThrow(CohortApplicationNotAssignableException::new);
            genders.put(application.id(), gender);
            incoming.merge(gender, 1L, Long::sum);
        }
        // 정원이 비어 있는(null) 성별은 정하지 않은 것이라 막지 않는다. 정원은 입장 경로와 상관없는 방 전체 인원이다.
        incoming.forEach((gender, count) -> {
            Integer capacity = gender == ApplicantGender.MALE ? composition.maleCapacity() : composition.femaleCapacity();
            if (capacity != null && roomMemberReader.countByRoomIdAndGender(roomId, gender.name()) + count > capacity) {
                throw new RoomGenderCapacityExceededException();
            }
        });

        List<AssignedMemberResponse> assigned = new ArrayList<>();
        for (CohortApplication application : applications) {
            RoomMember member = roomMemberJoiner.join(RoomMember.fromApplication(roomId, application.userId(),
                    genders.get(application.id()).name(), application.birthday(), application.id(), now));
            if (!applicationWriter.assign(application.id(), roomId, now, actor)) {
                throw new CohortApplicationNotAssignableException();
            }
            assigned.add(AssignedMemberResponse.from(member));
        }
        events.publishEvent(new CohortApplicationsAssignedEvent(roomId, ids, actor));
        return assigned;
    }
}
