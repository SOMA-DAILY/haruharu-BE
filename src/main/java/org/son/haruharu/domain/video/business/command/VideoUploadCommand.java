package org.son.haruharu.domain.video.business.command;

import java.util.UUID;

public record VideoUploadCommand(
        UUID uploadSlotId,
        UUID missionId,
        String videoUrl,
        String thumbnailUrl,
        String caption
) {}
