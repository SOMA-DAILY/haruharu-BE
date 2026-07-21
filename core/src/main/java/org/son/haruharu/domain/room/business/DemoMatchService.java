package org.son.haruharu.domain.room.business;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.son.haruharu.domain.chat.business.ChatService;
import org.son.haruharu.domain.coin.business.CoinService;
import org.son.haruharu.domain.room.business.dto.DemoMatchResultResponse;
import org.son.haruharu.domain.room.business.exception.DemoPartnerNotFoundException;
import org.son.haruharu.domain.room.implement.DemoRoomFinder;
import org.son.haruharu.domain.room.implement.MatchResult;
import org.son.haruharu.domain.room.implement.MatchResultReader;
import org.son.haruharu.domain.room.implement.MatchResultWriter;
import org.son.haruharu.domain.room.implement.Room;
import org.son.haruharu.domain.room.implement.RoomMember;
import org.son.haruharu.domain.room.implement.RoomMemberReader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 데모 최종선택 결과 + 채팅 시드(#266). 앱스토어 리젝 사유가 "채팅의 pre-populated content"였으므로,
 * 성사 시 별도 데모 테이블이 아니라 <b>실제 chat 도메인</b>에 방·대화를 만들어 FE가 기존 채팅 화면을 그대로 쓰게 한다.
 * <p>
 * 데모 방 조회는 {@link DemoRoomFinder}에 위임하고(단일 진입점·순환 의존 방지),
 * 채팅 생성·정리는 소유 도메인인 {@link ChatService}에 위임한다(Service-to-Service 조율).
 */
@Service
@Transactional(readOnly = true)
public class DemoMatchService {

    private final DemoRoomFinder demoRoomFinder;
    private final RoomMemberReader roomMemberReader;
    private final MatchResultReader matchResultReader;
    private final MatchResultWriter matchResultWriter;
    private final ChatService chatService;
    private final CoinService coinService;
    private final ConsolationRewardProperties consolationRewardProperties;
    private final Clock clock;

    public DemoMatchService(DemoRoomFinder demoRoomFinder,
                            RoomMemberReader roomMemberReader,
                            MatchResultReader matchResultReader,
                            MatchResultWriter matchResultWriter,
                            ChatService chatService,
                            CoinService coinService,
                            ConsolationRewardProperties consolationRewardProperties,
                            Clock clock) {
        this.demoRoomFinder = demoRoomFinder;
        this.roomMemberReader = roomMemberReader;
        this.matchResultReader = matchResultReader;
        this.matchResultWriter = matchResultWriter;
        this.chatService = chatService;
        this.coinService = coinService;
        this.consolationRewardProperties = consolationRewardProperties;
        this.clock = clock;
    }

    /**
     * 데모 최종선택 결과 생성. MATCHED면 가상 상대와 성사 처리 + 실제 채팅방·대화 시드,
     * FAILED면 실서비스와 동일 정책으로 위로 구름을 지급한다. 두 경우 모두 재호출에 멱등.
     */
    @Transactional
    public DemoMatchResultResponse createResult(Long userId, DemoMatchOutcome outcome) {
        Room room = demoRoomFinder.require(userId);
        if (outcome == DemoMatchOutcome.FAILED) {
            // 실서비스 미매칭과 동일한 위로 구름 정책 재사용(원장 refId=roomId 기준 멱등).
            coinService.grantMatchConsolation(userId, room.id(), consolationRewardProperties.amount());
            return new DemoMatchResultResponse(room.id(), outcome.name(), null);
        }

        List<RoomMember> members = roomMemberReader.getByRoomId(room.id());
        RoomMember me = members.stream()
                .filter(m -> m.userId().equals(userId))
                .findFirst()
                .orElseThrow(DemoPartnerNotFoundException::new);

        // 이미 성사된 상태면 그 결과를 재사용한다(중복 INSERT는 UNIQUE(member_a,member_b) 위반).
        // 이때 상대는 반드시 저장된 결과에서 되짚어야 한다 — 다시 고르면 멤버 조회 순서에 따라
        // 매칭 결과의 상대와 채팅 상대가 어긋날 수 있다(조회에 ORDER BY 없음).
        MatchResult existing = matchResultReader.findByMember(me.id()).orElse(null);
        MatchResult matchResult;
        RoomMember partner;
        if (existing == null) {
            partner = pickPartner(members, me);
            matchResult = matchResultWriter.save(
                    MatchResult.of(me.id(), partner.id(), LocalDateTime.now(clock)));
        } else {
            matchResult = existing;
            partner = findMemberById(members, partnerMemberIdOf(existing, me.id()));
        }

        Long chatRoomId = chatService.seedDemoChat(matchResult.id(), me.userId(), partner.userId());
        return new DemoMatchResultResponse(room.id(), outcome.name(), chatRoomId);
    }

    /**
     * 데모 매칭·채팅 정리(#266). 리셋이 참가신청부터 재시연되게 하려면 성사 결과와 채팅이 사라져야 한다.
     * 전역 soft-delete + UNIQUE 제약 때문에 soft 삭제로는 재생성이 막혀 하드삭제한다.
     */
    @Transactional
    public void purge(List<Long> roomMemberIds) {
        roomMemberIds.stream()
                .map(matchResultReader::findByMember)
                .flatMap(Optional::stream)
                .map(MatchResult::id)
                .distinct()
                .forEach(chatService::purgeDemoChat);
        matchResultWriter.hardDeleteByMemberIds(roomMemberIds);
    }

    /** 저장된 매칭 결과에서 내가 아닌 쪽의 멤버 id. */
    private Long partnerMemberIdOf(MatchResult matchResult, Long myMemberId) {
        return matchResult.memberAId().equals(myMemberId) ? matchResult.memberBId() : matchResult.memberAId();
    }

    private RoomMember findMemberById(List<RoomMember> members, Long memberId) {
        return members.stream()
                .filter(m -> m.id().equals(memberId))
                .findFirst()
                .orElseThrow(DemoPartnerNotFoundException::new);
    }

    /** 반대 성별 가상 멤버 우선, 없으면 아무 가상 멤버. 본인만 있는 방이면 상대가 없어 예외. */
    private RoomMember pickPartner(List<RoomMember> members, RoomMember me) {
        List<RoomMember> others = members.stream()
                .filter(m -> !m.id().equals(me.id()))
                .toList();
        return others.stream()
                .filter(m -> m.gender() != null && !m.gender().equals(me.gender()))
                .findFirst()
                .or(() -> others.stream().findFirst())
                .orElseThrow(DemoPartnerNotFoundException::new);
    }
}
