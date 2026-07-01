package org.son.haruharu.domain.video.business.command;

import java.util.UUID;

public record VideoUploadCommand(
        int slotIndex,
        UUID missionId,
        String videoUrl,
        String thumbnailUrl,
        String caption
) {}
