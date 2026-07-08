package org.son.haruharu.domain.video.business.dto;

import org.son.haruharu.domain.video.implement.ReactionType;

/** 토글 결과. reacted=false면 반응 해제됨(reactionType=null). */
public record ReactionResponse(boolean reacted, ReactionType reactionType) {}
