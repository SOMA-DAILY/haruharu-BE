package org.son.haruharu.domain.matching.business;

import org.son.haruharu.domain.matching.implement.MatchGroup;
import org.son.haruharu.domain.matching.implement.MatchingQueue;
import org.son.haruharu.domain.matching.implement.MatchingQueueReader;
import org.son.haruharu.domain.matching.implement.MatchingQueueWriter;
import org.son.haruharu.domain.matching.implement.MatchingStrategy;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberWriter;
import org.son.haruharu.domain.room.implement.RoomWriter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 매칭 실행 유스케이스. 스케줄러에서 분리되어 있어 스케줄러·어드민 API·worker 등 어떤 트리거에서도 재사용 가능하다.
 * QUEUED → MATCHED 상태 전이를 한 트랜잭션에서 확정해 (단일 인스턴스에서) 순차 재실행 시 이중 방 생성을 방지한다.
 * 주의: 다중 인스턴스(HA) 환경에서는 동시 실행 시 같은 QUEUED 집합을 각자 읽어 이중 생성될 수 있으므로,
 * HA 전환 시 분산 락(ShedLock 등) 또는 SELECT ... FOR UPDATE 도입이 필요하다.
 */
@Service
public class MatchingExecutor {

    private static final String MALE = "MALE";
    private static final String FEMALE = "FEMALE";
    private static final int ROOM_TOTAL_DAYS = 3;

    private final MatchingQueueReader matchingQueueReader;
    private final MatchingQueueWriter matchingQueueWriter;
    private final MatchingStrategy matchingStrategy;
    private final RoomWriter roomWriter;
    private final RoomMemberWriter roomMemberWriter;

    public MatchingExecutor(
        MatchingQueueReader matchingQueueReader,
        MatchingQueueWriter matchingQueueWriter,
        MatchingStrategy matchingStrategy,
        RoomWriter roomWriter,
        RoomMemberWriter roomMemberWriter
    ) {
        this.matchingQueueReader = matchingQueueReader;
        this.matchingQueueWriter = matchingQueueWriter;
        this.matchingStrategy = matchingStrategy;
        this.roomWriter = roomWriter;
        this.roomMemberWriter = roomMemberWriter;
    }

    /** scheduledDate 당일에 시작될 방을 확정한다. matchedAt은 큐 MATCHED·멤버 입장 시각으로 함께 기록한다. 반환값은 생성된 방 수. */
    @Transactional
    public int execute(LocalDate scheduledDate, LocalDateTime matchedAt) {
        List<MatchingQueue> queued = matchingQueueReader.findAllQueued();
        List<MatchingQueue> males = queued.stream().filter(q -> MALE.equals(q.gender())).toList();
        List<MatchingQueue> females = queued.stream().filter(q -> FEMALE.equals(q.gender())).toList();

        List<MatchGroup> groups = matchingStrategy.match(males, females);
        for (MatchGroup group : groups) {
            Room room = roomWriter.save(Room.create(scheduledDate, ROOM_TOTAL_DAYS));
            List<RoomMember> members = group.all().stream()
                .map(q -> RoomMember.of(room.id(), q.userId(), q.gender(), matchedAt))
                .toList();
            roomMemberWriter.saveAll(members);
            group.all().forEach(q -> matchingQueueWriter.save(q.matched(matchedAt)));
        }
        return groups.size();
    }
}
