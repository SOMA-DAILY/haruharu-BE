package org.son.haruharu.domain.replyrecommendation.implement;

/** 앱에 보내는 카드. 원문·근거·검수용 예문은 보내지 않는다. */
public record RecommendationCard(String cardId, String category, String title) {}
