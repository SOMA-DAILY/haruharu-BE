package org.son.haruharu.domain.replyrecommendation.implement;

/** 대조 확인용 집계: 답장 시각에 방에 있던 다른 멤버 수와 그중 창 안에 말한 수. */
public record ReplyBystanders(int members, int responded) {}
