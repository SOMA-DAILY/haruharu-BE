package org.son.haruharu.domain.room.business;

import org.son.haruharu.domain.block.implement.BlockReader;
import org.son.haruharu.domain.room.business.event.MutualMatchEvent;
import org.son.haruharu.domain.room.implement.FinalSelection;
import org.son.haruharu.domain.room.implement.FinalSelectionReader;
import org.son.haruharu.domain.room.implement.MatchResult;
import org.son.haruharu.domain.room.implement.MatchResultReader;
import org.son.haruharu.domain.room.implement.MatchResultWriter;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 최종선택 동시공개 resolve(#343). result_at 도달 시 방 전체 최종선택을 훑어 상호선택쌍을 한 번에
 * 매칭 성사(MatchResult)시킨다. 즉시판정(select 시점 성사)을 대체 — 마감 전 조기 노출을 막는다.
 * <p>
 * 멱등: 이미 성사된 쌍(match_results 존재)은 건너뛴다. result_at 이후 여러 번 sweep 돼도 안전하다.
 * MutualMatchEvent 를 발행해 DIRECT 채팅 자동 생성·매칭 푸시 파이프라인(기존)을 그대로 태운다.
 * <p>
 * 방 하나를 단일 트랜잭션으로 resolve 한다(단일 인스턴스 배치 전제 — MatchingExecutor 와 동일). 분산 HA 로
 * 확장해 두 인스턴스가 동시에 sweep 하면 UNIQUE(member_a,member_b) 위반으로 뒤쪽 쌍이 롤백되고 다음 sweep 에서
 * 복구되나, 그 방·그 sweep 의 다른 쌍 성사·알림이 지연될 수 있다. 그때는 쌍 단위 트랜잭션/분산락을 재검토한다.
 */
@Service
public class FinalSelectionResolver {

    private final RoomMemberReader roomMemberReader;
    private final FinalSelectionReader finalSelectionReader;
    private final MatchResultReader matchResultReader;
    private final MatchResultWriter matchResultWriter;
    private final BlockReader blockReader;
    private final ApplicationEventPublisher eventPublisher;

    public FinalSelectionResolver(RoomMemberReader roomMemberReader, FinalSelectionReader finalSelectionReader,
                                  MatchResultReader matchResultReader, MatchResultWriter matchResultWriter,
                                  BlockReader blockReader, ApplicationEventPublisher eventPublisher) {
        this.roomMemberReader = roomMemberReader;
        this.finalSelectionReader = finalSelectionReader;
        this.matchResultReader = matchResultReader;
        this.matchResultWriter = matchResultWriter;
        this.blockReader = blockReader;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 방의 상호선택쌍을 성사시킨다. 반환값은 이번에 새로 성사시킨 쌍 수(모니터링용).
     * <p>REQUIRES_NEW 로 방마다 독립 트랜잭션을 연다(reconciler sweep 안에서 방 단위 격리 — 한 방 실패가 다른 방을 롤백하지 않게).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int resolveForRoom(Long roomId, LocalDateTime now) {
        List<RoomMember> members = roomMemberReader.getByRoomId(roomId);
        if (members.isEmpty()) {
            return 0;
        }
        List<Long> memberIds = members.stream().map(RoomMember::id).toList();
        Map<Long, Long> memberToUser = members.stream()
                .collect(Collectors.toMap(RoomMember::id, RoomMember::userId));

        // roomMemberId → 그 멤버가 고른 상대(기권이면 없음)
        Map<Long, Long> choiceByMember = finalSelectionReader.findByRoomMemberIds(memberIds).stream()
                .filter(sel -> sel.selectedMemberId() != null)
                .collect(Collectors.toMap(FinalSelection::roomMemberId, FinalSelection::selectedMemberId));

        int matched = 0;
        Set<Long> processed = new HashSet<>(); // 한 방에서 쌍 중복 성사 방지(a→b, b→a 두 번 도는 것)
        for (Map.Entry<Long, Long> entry : choiceByMember.entrySet()) {
            Long meId = entry.getKey();
            Long theirId = entry.getValue();
            if (processed.contains(meId)) {
                continue;
            }
            // 자기매칭 방어: select()가 CannotSelectSelfException 으로 막지만, 우회 데이터(수동 DB·구시드)가
            // 들어와도 memberA==memberB 행이 생기지 않게 resolve 에서도 막는다.
            if (meId.equals(theirId)) {
                continue;
            }
            // 상호선택 여부: 상대도 나를 골랐는가
            if (!meId.equals(choiceByMember.get(theirId))) {
                continue;
            }
            processed.add(meId);
            processed.add(theirId);
            // 차단 관계면 매칭 불성립(#408) — MatchResult 자체를 만들지 않아 DIRECT 채팅방도 생성되지 않는다.
            if (blockReader.isBlockedBetween(memberToUser.get(meId), memberToUser.get(theirId))) {
                continue;
            }
            // 멱등: 이미 성사된 쌍은 건너뛴다(재sweep 안전).
            if (matchResultReader.isMatched(meId, theirId)) {
                continue;
            }
            MatchResult saved = matchResultWriter.save(MatchResult.of(meId, theirId, now));
            eventPublisher.publishEvent(new MutualMatchEvent(
                    roomId, saved.id(), memberToUser.get(meId), memberToUser.get(theirId)));
            matched++;
        }
        return matched;
    }
}
