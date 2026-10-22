package org.son.haruharu.domain.room.implement;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import org.son.haruharu.config.InteractionGuidanceProperties;
import org.son.haruharu.domain.chat.implement.*;
import org.son.haruharu.domain.room.business.exception.RoomNotFoundException;
import org.springframework.stereotype.Component;

/** 트랜잭션은 Service가 소유한다. 배정은 방 잠금, 최초 행동·소비는 참가자 잠금으로 직렬화한다. */
@Component
@RequiredArgsConstructor
public class InteractionGuidanceManager {
    private final InteractionGuidanceProperties properties;
    private final RoomReader rooms;
    private final RoomMemberReader members;
    private final InteractionGuidanceRepository repository;
    private final ChatRoomReader chats;
    private final ChatRoomMemberReader chatMembers;
    private final VideoReplyTargetReader targets;

    public void enroll(Long roomId, LocalDateTime now) {
        if (!properties.enabled()) return;
        rooms.findById(roomId).ifPresent(room -> assignment(room, now));
    }

    public InteractionGuidanceSnapshot get(Long roomId, Long userId, LocalDateTime now) {
        RoomMember member = members.getByRoomIdAndUserId(roomId, userId);
        Room room = requireRoom(roomId);
        InteractionExperimentAssignment assignment = assignment(room, now);
        InteractionGuidanceProgress progress = assignment == null
                ? InteractionGuidanceProgress.empty() : progress(member.id());
        InteractionExperimentVariant variant = assignment == null
                ? InteractionExperimentVariant.NOT_ENROLLED : assignment.variant();
        return new InteractionGuidanceSnapshot(
                assignment == null ? properties.experimentKey() : assignment.experimentKey(), variant,
                assignment == null ? null : assignment.assignedAt(), now,
                room.startsAt() == null ? null : room.startsAt().plusHours(1),
                progress,
                variant == InteractionExperimentVariant.TOOLTIP && open(room, now) ? progress.nextGuide() : null);
    }

    public InteractionGuideClaimResult claim(Long roomId, Long userId,
            InteractionGuideKind kind, Long videoPostId, String viewerSessionId, LocalDateTime now) {
        RoomMember member = members.getByRoomIdAndUserId(roomId, userId);
        Room room = requireRoom(roomId);
        InteractionExperimentAssignment assignment = assignment(room, now);
        if (assignment == null || assignment.variant() != InteractionExperimentVariant.TOOLTIP)
            return InteractionGuideClaimResult.denied(kind, "NOT_IN_TREATMENT");

        // 처음 상태 행이 없어도 기존 참가자 행을 잠그므로 두 기기의 첫 요청을 함께 직렬화한다.
        members.lockById(member.id());
        List<InteractionGuideClaim> claims = repository.findClaims(member.id());
        for (InteractionGuideClaim claim : claims) {
            if (claim.viewerSessionId().equals(viewerSessionId)) {
                if (claim.kind() != kind)
                    return InteractionGuideClaimResult.denied(kind, "SESSION_ALREADY_CONSUMED");
                if (!claim.videoPostId().equals(videoPostId))
                    return InteractionGuideClaimResult.denied(kind, "REQUEST_MISMATCH");
                return InteractionGuideClaimResult.accepted(claim);
            }
        }
        if (claims.stream().anyMatch(claim -> claim.kind() == kind))
            return InteractionGuideClaimResult.denied(kind, "GUIDE_ALREADY_CONSUMED");
        if (!open(room, now)) return InteractionGuideClaimResult.denied(kind, "NOT_ELIGIBLE_YET_OR_CLOSED");
        InteractionGuidanceProgress progress = progress(member.id());
        if (progress.completed(kind))
            return InteractionGuideClaimResult.denied(kind, "ACTION_ALREADY_COMPLETED");
        if (progress.nextGuide() != kind)
            return InteractionGuideClaimResult.denied(kind, "ANOTHER_GUIDE_FIRST");
        if (!availableTarget(room, userId, videoPostId, now))
            return InteractionGuideClaimResult.denied(kind, "VIDEO_UNAVAILABLE");

        var claim = new InteractionGuideClaim(UUID.randomUUID().toString(), member.id(), kind,
                videoPostId, viewerSessionId, now);
        repository.saveClaim(claim);
        return InteractionGuideClaimResult.accepted(claim);
    }

    private Room requireRoom(Long roomId) {
        return rooms.findById(roomId).orElseThrow(RoomNotFoundException::new);
    }

    private InteractionExperimentAssignment assignment(Room room, LocalDateTime now) {
        if (!properties.enrolls(room)) return null;
        var existing = repository.findAssignment(room.id());
        if (existing.isPresent()) return existing.get();
        // 별도 요청/인스턴스도 동일 방 행을 잠근 뒤 재조회하므로 배정은 한 번만 추첨한다.
        rooms.findByIdForUpdate(room.id()).orElseThrow(RoomNotFoundException::new);
        existing = repository.findAssignment(room.id());
        if (existing.isPresent()) return existing.get();
        var variant = ThreadLocalRandom.current().nextInt(100) < properties.tooltipPercent()
                ? InteractionExperimentVariant.TOOLTIP : InteractionExperimentVariant.CONTROL;
        var assignment = new InteractionExperimentAssignment(room.id(), properties.experimentKey(), variant, now);
        repository.saveAssignment(assignment);
        return assignment;
    }

    private boolean open(Room room, LocalDateTime now) {
        return room.startsAt() != null && !now.isBefore(room.startsAt().plusHours(1))
                && room.statusAt(now) == RoomStatus.ACTIVE;
    }

    private InteractionGuidanceProgress progress(Long memberId) {
        var first = repository.findFirstActions(memberId).orElse(InteractionFirstActions.empty(memberId));
        LocalDateTime reactionConsumed = null;
        LocalDateTime replyConsumed = null;
        for (var claim : repository.findClaims(memberId)) {
            if (claim.kind() == InteractionGuideKind.REACTION) reactionConsumed = claim.claimedAt();
            else replyConsumed = claim.claimedAt();
        }
        return new InteractionGuidanceProgress(first.firstReactedAt(), first.firstVideoRepliedAt(),
                reactionConsumed, replyConsumed);
    }

    private boolean availableTarget(Room room, Long userId, Long videoId, LocalDateTime now) {
        var chat = chats.findByRoomId(room.id());
        if (chat.isEmpty() || chat.get().closeAt() != null
                || chatMembers.findActiveMembership(chat.get().id(), userId).isEmpty()) return false;
        var target = targets.get(room.id(), chat.get().id(), videoId, userId);
        if (target.state() != VideoReplyState.AVAILABLE || target.isOwnedBy(userId)) return false;
        // 과거 회고 영상도 허용하지만 아직 공개되지 않은 미래 슬롯은 안내할 수 없다.
        var slot = target.post().slotStart();
        return slot != null && !slot.isBefore(room.startsAt()) && !slot.isAfter(now)
                && (room.finalSelectionOpensAt() == null || slot.isBefore(room.finalSelectionOpensAt()));
    }
}
