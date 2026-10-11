package org.son.haruharu.admin.domain.room.business;

import org.son.haruharu.admin.common.PageResponse;
import org.son.haruharu.admin.domain.room.business.dto.AdminRoomActivityResponse;
import org.son.haruharu.admin.domain.room.business.dto.AdminRoomActivityResponse.*;
import org.son.haruharu.admin.domain.room.implement.AdminRoomActivityReader;
import org.son.haruharu.admin.domain.room.implement.AdminRoomReader;
import org.son.haruharu.admin.domain.room.implement.RoomActivity;
import org.son.haruharu.infrastructure.s3.S3StoragePort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@Transactional(readOnly = true)
public class AdminRoomActivityService {
    private final AdminRoomReader rooms;
    private final AdminRoomActivityReader activities;
    private final S3StoragePort media;

    public AdminRoomActivityService(AdminRoomReader rooms, AdminRoomActivityReader activities, S3StoragePort media) {
        this.rooms = rooms;
        this.activities = activities;
        this.media = media;
    }

    // 방 활동을 조건에 따라 조회하고, 영상/썸네일에는 서명된 조회 URL을 적용해 페이지로 반환
    public PageResponse<AdminRoomActivityResponse> getActivities(Long roomId, Integer day, Long userId,
                                                                String type, int page, int size, String order) {
        var room = rooms.getById(roomId);
        activities.validatePage(page, size, order);
        var filter = activities.filter(room, day, userId, type);
        var members = activities.participants(roomId);
        var events = activities.find(filter, page * size, size, order.equals("asc"));
        var content = events.stream()
                .map(a -> new AdminRoomActivityResponse(a.type(), a.id(), a.occurredAt(), activities.dayOf(room,
                        a.type().equals("VIDEO") && a.slotStart() != null ? a.slotStart() : a.occurredAt()),
                        toParticipant(activities.participant(members, a.actorUserId())),
                        toParticipant(activities.participant(members, a.targetUserId())), a.content(),
                        a.videoId() == null ? null : new Video(a.videoId(), media.createPresignedGetUrl(a.videoUrl()),
                                media.createPresignedImageGetUrl(a.thumbnailUrl()), a.caption(), a.hidden(), a.missionId(), a.slotStart()),
                        a.reactionType(), a.answer(), a.answeredAt(), a.replyType())).toList();
        return PageResponse.of(content, page, size, activities.count(filter));
    }

    // 선택한 일차, 참여자 조건에 해당하는 전체 활동 수와 참여자별 활동 수를 반환
    public Summary getSummary(Long roomId, Integer day, Long userId) {
        var room = rooms.getById(roomId);
        var counts = activities.counts(activities.filter(room, day, userId, null));
        var stats = activities.statistics(counts, null);
        var members = activities.memberStatistics(roomId, userId, counts).stream().map(m -> {
            var s = m.statistics();
            return new MemberCounts(m.participant().userId(), m.participant().name(), s.videos(), s.chats(),
                    s.reactions(), s.reactionsReceived(), s.questionsReceived());
        }).toList();
        return new Summary(new Counts(stats.videos(), stats.chats(), stats.reactions(), stats.questions()), members);
    }

    // 내부 참여자 프로젝션을 관리자 API 응답 타입으로 변환
    private Participant toParticipant(RoomActivity.Participant p) {
        return p == null ? null : new Participant(p.userId(), p.name());
    }
}
