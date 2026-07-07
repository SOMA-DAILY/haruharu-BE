package org.son.haruharu.domain.notification.implement;

/**
 * 푸시 문구의 단일 관리 지점. 타입 추가 = 상수 한 줄 + 발송 트리거 연결.
 * title/body는 AI 초안(2026-07-04, 사용자 요청) — 기획 최종검수 후 확정 대상.
 * title/body의 %d 등 포맷 지정자는 발송 시 PushSendService가 bodyArgs로 치환한다(환불·충전 개수 등 가변값).
 */
public enum NotificationType {

    // ── A. 세션 진행 (3일 서사) ──
    ROOM_MATCHED("🎉 오늘의 인연이 정해졌어요", "3일간 함께할 인연이 도착했어요. 내일 만나요"),
    ROOM_STARTED("👋 하루방이 열렸어요", "지금 첫 영상으로 인사를 건네보세요"),
    SLOT_START("🎬 새로운 하루를 남길 시간", "서로의 하루를 확인하고, 오늘의 나도 남겨보세요"),
    SLOT_DEADLINE_SOON("⏰ 30분 뒤 마감!", "오늘의 하루, 아직 안 남겼어요"),
    FINAL_SELECTION_OPEN("💘 마음을 전할 시간이에요", "3일간 지켜본 그 사람, 오늘 선택할 수 있어요"),

    // ── B. 소셜 상호작용 ──
    REACTION_RECEIVED("👀 누군가 내 영상에 반응했어요", "누가 관심을 보였을까요?"),
    MUTUAL_MATCH("💞 서로의 마음이 닿았어요", "두 분 다 서로를 선택했어요! 지금 대화를 시작해보세요"),
    SELECTED_BY_SOMEONE("💕 설레는 소식이 있어요", "누군가 당신을 마음에 담았어요. 누굴까요?"),

    // ── C. 시스템 / 거래 ──
    COIN_REFUNDED("☁️ 구름 %d개를 돌려드렸어요", "이번엔 인연이 닿지 않았지만, 다음 기회에 다시 만나요"),
    PAYMENT_COMPLETED("☁️ 충전 완료!", "구름 %d개가 충전됐어요. 새로운 인연에 한 걸음 더 가까워졌어요"),

    // ── D. 재참여 (잠수 유저 복귀 유도) ──
    WINBACK("💫 새로운 인연이 도착했어요", "오늘 밤 매칭에 당신의 자리를 남겨뒀어요. 지금 신청해보세요");

    private final String title;
    private final String body;

    NotificationType(String title, String body) {
        this.title = title;
        this.body = body;
    }

    public String title() {
        return title;
    }

    public String body() {
        return body;
    }
}
