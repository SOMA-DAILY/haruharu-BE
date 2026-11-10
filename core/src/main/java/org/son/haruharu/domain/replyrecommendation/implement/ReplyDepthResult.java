package org.son.haruharu.domain.replyrecommendation.implement;

/** 답장 하나의 Depth 계산 결과. 미관측이면 depth·width 는 null 이다. */
public record ReplyDepthResult(ReplyDepthStatus status, Integer depth, Integer width, String unobservedReason) {

    /** 답장 메시지가 없거나 운영자 삭제·퇴장 멤버로 숨겨졌다. */
    public static final String REPLY_UNAVAILABLE = "REPLY_UNAVAILABLE";
    /** 경로 중간 메시지가 지워져 그 뒤 답글의 연결을 확정할 수 없다. */
    public static final String CHAIN_BROKEN = "CHAIN_BROKEN";
    /** 경로 길이가 상한을 넘었다. */
    public static final String DEPTH_LIMIT = "DEPTH_LIMIT";
    /** 계산이 계속 실패해 미관측으로 닫았다(뒤 답장의 라벨을 막지 않게). */
    public static final String LABEL_FAILED = "LABEL_FAILED";

    public static ReplyDepthResult observed(int depth, int width) {
        return new ReplyDepthResult(ReplyDepthStatus.OBSERVED, depth, width, null);
    }

    public static ReplyDepthResult unobserved(String reason) {
        return new ReplyDepthResult(ReplyDepthStatus.UNOBSERVED, null, null, reason);
    }
}
