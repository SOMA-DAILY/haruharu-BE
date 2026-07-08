package org.son.haruharu.domain.meta.application;

import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.meta.business.MetaEnumService;
import org.son.haruharu.domain.meta.business.dto.EnumOptionResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/meta")
public class MetaController {

    private final MetaEnumService metaEnumService;

    public MetaController(MetaEnumService metaEnumService) {
        this.metaEnumService = metaEnumService;
    }

    @GetMapping("/enums")
    public ApiResponse<Map<String, List<EnumOptionResponse>>> getEnums() {
        return ApiResponse.ok(metaEnumService.getEnumOptions());
    }
}
