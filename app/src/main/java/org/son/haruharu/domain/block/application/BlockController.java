package org.son.haruharu.domain.block.application;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.block.application.dto.CreateBlockRequest;
import org.son.haruharu.domain.block.business.BlockService;
import org.son.haruharu.domain.block.business.dto.BlockResponse;
import org.son.haruharu.domain.block.business.dto.BlocksResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "차단", description = "사용자 차단 — 등록·목록·해제. 목록은 FE가 영상·프로필·그룹 메시지 숨김의 공통 필터로 사용한다")
@RestController
@RequestMapping("/api/v1/blocks")
public class BlockController {

    private final BlockService blockService;

    public BlockController(BlockService blockService) {
        this.blockService = blockService;
    }

    @Operation(summary = "유저 차단",
            description = "다른 유저를 차단한다. 방/1:1 어디에서 차단하든 동일하게 적용된다. "
                    + "중복 차단 시 409(BLOCK_ALREADY_EXISTS), 자기 자신 차단 시 400(CANNOT_BLOCK_SELF), "
                    + "대상이 없으면 404(USER_NOT_FOUND).")
    @PostMapping
    public ApiResponse<BlockResponse> block(Authentication authentication,
                                            @RequestBody @Valid CreateBlockRequest request) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(blockService.block(userId, request.toCommand()));
    }

    @Operation(summary = "내 차단 목록",
            description = "내가 차단한 유저 id 목록. FE가 영상·프로필·그룹 메시지 숨김의 공통 필터로 사용한다.")
    @GetMapping
    public ApiResponse<BlocksResponse> getMyBlocks(Authentication authentication) {
        Long userId = (Long) authentication.getPrincipal();
        return ApiResponse.ok(blockService.getMyBlocks(userId));
    }

    @Operation(summary = "차단 해제",
            description = "내가 건 차단만 해제한다. 차단하지 않은 대상이면 404(BLOCK_NOT_FOUND). "
                    + "상대가 나를 차단한 상태라면 1:1 제한은 유지된다.")
    @DeleteMapping("/{blockedUserId}")
    public ApiResponse<Void> unblock(Authentication authentication, @PathVariable Long blockedUserId) {
        Long userId = (Long) authentication.getPrincipal();
        blockService.unblock(userId, blockedUserId);
        return ApiResponse.ok(null);
    }
}
