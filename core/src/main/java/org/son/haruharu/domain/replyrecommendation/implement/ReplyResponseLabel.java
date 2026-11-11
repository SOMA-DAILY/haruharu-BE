package org.son.haruharu.domain.replyrecommendation.implement;

import java.math.BigDecimal;

/**
 * 영상 답장의 효과 라벨(이슈 #560). Depth(답글 버튼 사슬, 학습 라벨)와 섞지 않는다.
 * 답장 메시지를 못 찾았거나 영상 작성자를 모르면 {@link #NONE}(모든 값 null)이다.
 *
 * @param ownerResponded      답장 시각부터 {@code windowMinutes} 안에 영상 작성자가 같은 채팅방에 보낸 숨겨지지 않은 메시지가 있는지
 * @param ownerResponseMinutes 그 첫 메시지까지 걸린 분(소수 둘째 자리). 없으면 null
 * @param windowMinutes       판정에 쓴 창(분)
 * @param bystanderMembers    대조 확인용. 답장 시각에 방에 있던 멤버 중 답장자와 작성자를 뺀 수
 * @param bystandersResponded 그중 같은 창 안에 메시지를 보낸 멤버 수
 */
public record ReplyResponseLabel(Boolean ownerResponded, BigDecimal ownerResponseMinutes, Integer windowMinutes,
                                 Integer bystanderMembers, Integer bystandersResponded) {

    public static final ReplyResponseLabel NONE = new ReplyResponseLabel(null, null, null, null, null);

    public boolean present() {
        return windowMinutes != null;
    }
}
