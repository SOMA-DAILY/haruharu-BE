package org.son.haruharu.domain.video.business.command;

public record VideoUploadCommand(
        int slotIndex,
        String videoUrl,
        String thumbnailUrl,
        String caption
) {}
