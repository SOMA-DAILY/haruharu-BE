package org.son.haruharu.domain.video.business.command;

/** 슬롯은 서버가 현재 슬롯으로 자동 판정하므로 클라 입력에 슬롯 지정이 없다. */
public record VideoUploadCommand(
        String videoUrl,
        String thumbnailUrl,
        String caption
) {}
