package org.son.haruharu.domain.meta.application;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.meta.business.MetaEnumService;
import org.son.haruharu.domain.meta.business.MetaService;
import org.son.haruharu.domain.meta.business.dto.EnumOptionResponse;
import org.son.haruharu.domain.meta.business.dto.MetaResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "메타", description = "클라이언트 선택지용 enum 메타 제공 (인증 불필요 공개 API)")
@RestController
@RequestMapping("/api/v1/meta")
public class MetaController {

    private final MetaEnumService metaEnumService;
    private final MetaService metaService;

    public MetaController(MetaEnumService metaEnumService, MetaService metaService) {
        this.metaEnumService = metaEnumService;
        this.metaService = metaService;
    }

    @Operation(summary = "정책 상수 메타 조회",
            description = "유저 상태와 무관한 전역 정책 상수를 반환한다(matching.enter_cost 등). 인증이 필요 없는 공개 API다.")
    @GetMapping
    public ApiResponse<MetaResponse> getMeta() {
        return ApiResponse.ok(metaService.getMeta());
    }

    @Operation(summary = "enum 선택지 메타 조회",
            description = "클라이언트 선택지용 enum 메타를 key별 code+label 목록으로 반환한다. 인증이 필요 없는 공개 API다.")
    @GetMapping("/enums")
    public ApiResponse<Map<String, List<EnumOptionResponse>>> getEnums() {
        return ApiResponse.ok(metaEnumService.getEnumOptions());
    }
}
