package org.son.haruharu.domain.profile.application;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.profile.application.dto.RoomProfileUpdateRequest;
import org.son.haruharu.domain.profile.business.RoomProfileService;
import org.son.haruharu.domain.profile.business.dto.RoomProfileResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "프로필", description = "데이팅 프로필(nickname·mbti 등) 수정 및 프로필 이미지 업로드·교체")
@RestController
@RequestMapping("/api/v1/users/me/room-profile")
public class RoomProfileController {

    private final RoomProfileService roomProfileService;

    public RoomProfileController(RoomProfileService roomProfileService) {
        this.roomProfileService = roomProfileService;
    }

    @Operation(summary = "방 온보딩 프로필 조회",
            description = "출생연도, 나이(올해 − 출생연도), MBTI, 학과, 요즘 빠져 있는 것과 완료 여부. 값이 없으면 null.")
    @GetMapping
    public ApiResponse<RoomProfileResponse> getRoomProfile(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(roomProfileService.getRoomProfile(userId));
    }

    @Operation(summary = "방 온보딩 프로필 저장",
            description = "4칸만 갱신한다(닉네임 등 다른 프로필 칸은 그대로). 틀린 칸은 400 VALIDATION_ERROR 의 "
                    + "error.fields[{field, message}] 로 내려간다.")
    @PutMapping
    public ApiResponse<RoomProfileResponse> updateRoomProfile(@RequestBody RoomProfileUpdateRequest request,
                                                              Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(roomProfileService.updateRoomProfile(userId, request.toCommand()));
    }
}
