package org.son.haruharu.domain.matching.application;

import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.matching.business.MatchingService;
import org.son.haruharu.domain.matching.business.dto.QueueResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/matching")
public class MatchingController {

    private final MatchingService matchingService;

    public MatchingController(MatchingService matchingService) {
        this.matchingService = matchingService;
    }

    @PostMapping("/queue")
    public ApiResponse<QueueResponse> joinQueue(Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        return ApiResponse.ok(matchingService.joinQueue(userId));
    }

    @DeleteMapping("/queue")
    public ApiResponse<Void> leaveQueue(Authentication authentication) {
        UUID userId = (UUID) authentication.getPrincipal();
        matchingService.leaveQueue(userId);
        return ApiResponse.ok(null);
    }
}
