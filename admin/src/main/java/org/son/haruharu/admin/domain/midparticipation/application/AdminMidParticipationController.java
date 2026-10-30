package org.son.haruharu.admin.domain.midparticipation.application;
import java.time.LocalDateTime;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.midparticipation.business.ParticipationAdminService;
import org.son.haruharu.domain.midparticipation.business.dto.ParticipationCampaignView;
import org.son.haruharu.domain.room.business.RoomMemberRemovalService;
import org.son.haruharu.domain.room.implement.MemberRemoval;

@RestController @RequestMapping("/api/v1/admin") @RequiredArgsConstructor
public class AdminMidParticipationController {
    private final ParticipationAdminService service;
    private final org.son.haruharu.admin.domain.midparticipation.business.ParticipationCandidateService candidates;
    private final RoomMemberRemovalService removals;
    public enum GenderFilter { MALE, FEMALE }
    public record UsersRequest(@NotEmpty @Size(max=100) List<@NotNull @Positive Long> userIds) {}
    public record CampaignRequest(@Min(1) @Max(100) int seats,LocalDateTime expiresAt,
            @NotEmpty @Size(max=100) List<@NotNull @Positive Long> userIds) {}
    public record ResendRequest(@Size(max=100) List<@NotNull @Positive Long> userIds,LocalDateTime expiresAt) {}
    public record RemoveRequest(@Size(max=200) String reason) {}
    @GetMapping("/mid-participation/candidates") public ApiResponse<org.son.haruharu.admin.common.PageResponse<org.son.haruharu.admin.domain.midparticipation.implement.ParticipationCandidate>> candidates(
            @RequestParam(required=false) String keyword,@RequestParam(required=false) GenderFilter gender,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return ApiResponse.ok(candidates.candidates(keyword,gender==null?null:gender.name(),page,size));
    }
    @PostMapping("/mid-participation/outreach") public ApiResponse<List<Long>> outreach(@Valid @RequestBody UsersRequest r) {
        return ApiResponse.ok(service.outreach(r.userIds()));
    }
    @GetMapping("/rooms/{roomId}/mid-participation/availability") public ApiResponse<ParticipationAdminService.Availability> availability(@PathVariable Long roomId) { return ApiResponse.ok(service.availability(roomId)); }
    @GetMapping("/rooms/{roomId}/mid-participation") public ApiResponse<List<ParticipationCampaignView>> campaigns(@PathVariable Long roomId,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return ApiResponse.ok(service.campaigns(roomId,page,size));
    }
    @PostMapping("/rooms/{roomId}/mid-participation") public ApiResponse<ParticipationCampaignView> create(@PathVariable Long roomId,@Valid @RequestBody CampaignRequest r) {
        return ApiResponse.ok(service.createCampaign(roomId,r.seats(),r.expiresAt(),r.userIds()));
    }
    @PostMapping("/mid-participation/campaigns/{id}/resend") public ApiResponse<ParticipationCampaignView> resend(@PathVariable Long id,
            @Valid @RequestBody(required=false) ResendRequest r) {
        return ApiResponse.ok(service.resend(id,r==null?null:r.userIds(),r==null?null:r.expiresAt()));
    }
    @PostMapping("/mid-participation/campaigns/{id}/cancel") public ApiResponse<ParticipationCampaignView> cancel(@PathVariable Long id) {
        return ApiResponse.ok(service.cancel(id));
    }
    @PostMapping("/rooms/{roomId}/members/{memberId}/remove") public ApiResponse<org.son.haruharu.domain.room.business.dto.MemberRemovalResponse> remove(@PathVariable Long roomId,
            @PathVariable Long memberId,@Valid @RequestBody(required=false) RemoveRequest r,Authentication auth) {
        return ApiResponse.ok(org.son.haruharu.domain.room.business.dto.MemberRemovalResponse.from(removals.remove(roomId,memberId,r==null?null:r.reason(),(Long)auth.getPrincipal())));
    }
}
