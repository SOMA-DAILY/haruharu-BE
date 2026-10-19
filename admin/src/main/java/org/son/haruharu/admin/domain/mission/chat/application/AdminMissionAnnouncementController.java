package org.son.haruharu.admin.domain.mission.chat.application;

import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.son.haruharu.admin.domain.mission.chat.application.dto.MissionAnnouncementRequest;
import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.mission.chat.business.MissionAnnouncementService;
import org.son.haruharu.domain.mission.chat.business.dto.AnnouncementPageResponse;
import org.son.haruharu.domain.mission.chat.business.dto.MissionAnnouncementResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Admin Mission Announcement", description = "미션의 개별 채팅 안내 관리")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/rooms/{roomId}/missions/{missionId}/announcements")
public class AdminMissionAnnouncementController {
    private final MissionAnnouncementService announcements;

    @GetMapping
    public ApiResponse<AnnouncementPageResponse> announcements(@PathVariable Long roomId, @PathVariable Long missionId,
            @RequestParam(required = false) Long after, @RequestParam(defaultValue = "50") int size) {
        return ApiResponse.ok(announcements.list(roomId, missionId, after, size));
    }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MissionAnnouncementResponse> add(@PathVariable Long roomId, @PathVariable Long missionId,
            @jakarta.validation.Valid @RequestBody MissionAnnouncementRequest request, Authentication auth) {
        return ApiResponse.ok(announcements.add(roomId, missionId, request.toCommand(), actor(auth)));
    }
    @PutMapping("/{announcementId}")
    public ApiResponse<MissionAnnouncementResponse> updateAnnouncement(@PathVariable Long roomId, @PathVariable Long missionId,
            @PathVariable Long announcementId, @jakarta.validation.Valid @RequestBody MissionAnnouncementRequest request, Authentication auth) {
        return ApiResponse.ok(announcements.update(roomId, missionId, announcementId, request.toCommand(), actor(auth)));
    }
    @PostMapping("/{announcementId}/send-now")
    public ApiResponse<MissionAnnouncementResponse> sendNow(@PathVariable Long roomId, @PathVariable Long missionId,
            @PathVariable Long announcementId, Authentication auth) {
        return ApiResponse.ok(announcements.sendNow(roomId, missionId, announcementId, actor(auth)));
    }
    @PostMapping("/{announcementId}/cancel")
    public ApiResponse<MissionAnnouncementResponse> cancelAnnouncement(@PathVariable Long roomId, @PathVariable Long missionId,
            @PathVariable Long announcementId, Authentication auth) {
        return ApiResponse.ok(announcements.cancel(roomId, missionId, announcementId, actor(auth)));
    }
    @DeleteMapping("/{announcementId}/message")
    public ApiResponse<MissionAnnouncementResponse> deleteMessage(@PathVariable Long roomId, @PathVariable Long missionId,
            @PathVariable Long announcementId) {
        return ApiResponse.ok(announcements.deleteSentMessage(roomId, missionId, announcementId));
    }
    private String actor(Authentication auth) { return "admin:" + (Long) auth.getPrincipal(); }
}
