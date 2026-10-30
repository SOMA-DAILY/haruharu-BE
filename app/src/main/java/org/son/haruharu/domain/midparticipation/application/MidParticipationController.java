package org.son.haruharu.domain.midparticipation.application;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.midparticipation.business.MidParticipationService;
import org.son.haruharu.domain.midparticipation.business.dto.*;
import org.son.haruharu.domain.midparticipation.implement.ParticipationPreference;
import org.son.haruharu.domain.room.business.dto.JoinRoomResponse;
import lombok.RequiredArgsConstructor;

@RestController @RequestMapping("/api/v1/mid-participation") @RequiredArgsConstructor
public class MidParticipationController {
    private final MidParticipationService service;
    public record PreferenceRequest(boolean enabled) {}
    public record SeenRequest(@Min(1) int version) {}
    @GetMapping("/visibility") public ApiResponse<ParticipationVisibilityResponse> visibility(Authentication auth) { return ApiResponse.ok(service.visibility((Long)auth.getPrincipal())); }
    @GetMapping("/prompt") public ApiResponse<ParticipationInvitationView> prompt(Authentication auth) { return ApiResponse.ok(service.prompt((Long)auth.getPrincipal())); }
    @GetMapping public ApiResponse<ParticipationInbox> inbox(Authentication auth,
            @RequestParam(required=false) Long before,@RequestParam(defaultValue="50") int size) {
        return ApiResponse.ok(service.inbox((Long)auth.getPrincipal(),before,size));
    }
    @PutMapping("/preference") public ApiResponse<ParticipationPreferenceResponse> preference(Authentication auth,
            @Valid @RequestBody PreferenceRequest request) {
        return ApiResponse.ok(service.preference((Long)auth.getPrincipal(),request.enabled()));
    }
    @GetMapping("/invitations/{id}") public ApiResponse<ParticipationInvitationView> invitation(Authentication auth,@PathVariable Long id) {
        return ApiResponse.ok(service.invitation((Long)auth.getPrincipal(),id));
    }
    @PostMapping("/invitations/{id}/seen") public ApiResponse<Void> seen(Authentication auth,@PathVariable Long id,@Valid @RequestBody SeenRequest request) {
        service.seen((Long)auth.getPrincipal(),id,request.version());return ApiResponse.ok(null);
    }
    @PostMapping("/invitations/{id}/decline") public ApiResponse<Void> decline(Authentication auth,@PathVariable Long id) {
        service.decline((Long)auth.getPrincipal(),id);return ApiResponse.ok(null);
    }
    @PostMapping("/invitations/{id}/accept") public ApiResponse<JoinRoomResponse> accept(Authentication auth,@PathVariable Long id) {
        return ApiResponse.ok(service.accept((Long)auth.getPrincipal(),id));
    }
}
