package org.son.haruharu.domain.room.business.command;

import org.son.haruharu.domain.room.implement.InteractionGuideKind;

public record ClaimInteractionGuideCommand(InteractionGuideKind kind, Long videoPostId, String viewerSessionId) {}
