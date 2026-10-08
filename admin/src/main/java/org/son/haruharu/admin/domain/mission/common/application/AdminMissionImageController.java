package org.son.haruharu.admin.domain.mission.common.application;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.son.haruharu.common.ApiResponse;
import org.son.haruharu.domain.mission.common.business.MissionImageService;
import org.son.haruharu.domain.mission.common.business.dto.MissionImageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Admin Mission Image", description = "미션에 붙일 사진 업로드")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/mission-images")
public class AdminMissionImageController {
    private final MissionImageService images;

    @Operation(summary = "미션 사진 업로드", description = "JPG·PNG 10MB 이하. 응답의 key 를 미션 생성·수정의 image_key 로 보낸다. "
            + "방 생성 전에도 올릴 수 있다.")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MissionImageResponse> upload(@RequestParam("file") MultipartFile file) throws IOException {
        return ApiResponse.ok(images.upload(file.getBytes(), file.getContentType()));
    }
}
