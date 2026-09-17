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

    public PageResponse<AdminRoomActivityResponse> list(Long roomId, Integer day, Long userId,
                                                       String type, int page, int size, String order) {
        var room = rooms.getById(roomId);
        activities.validatePage(page, size, order);
        var filter = activities.filter(room, day, userId, type);
        var members = activities.participants(roomId);
        var events = activities.find(filter, page * size, size, order.equals("asc"));
        var content = events.stream()
                .map(a -> new AdminRoomActivityResponse(a.type(), a.id(), a.occurredAt(), activities.dayOf(room,
                        a.type().equals("VIDEO") && a.slotStart() != null ? a.slotStart() : a.occurredAt()),
                        participant(activities.participant(members, a.actorUserId())),
                        participant(activities.participant(members, a.targetUserId())), a.content(),
                        a.videoId() == null ? null : new Video(a.videoId(), media.createPresignedGetUrl(a.videoUrl()),
                                media.createPresignedImageGetUrl(a.thumbnailUrl()), a.caption(), a.hidden(), a.missionId(), a.slotStart()),
                        a.reactionType(), a.answer(), a.answeredAt())).toList();
        return PageResponse.of(content, page, size, activities.count(filter));
    }

    public Summary summary(Long roomId, Integer day, Long userId) {
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

    private Participant participant(RoomActivity.Participant p) {
        return p == null ? null : new Participant(p.userId(), p.name());
    }
}
