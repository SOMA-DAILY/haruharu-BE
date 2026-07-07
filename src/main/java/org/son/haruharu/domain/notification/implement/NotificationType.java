package org.son.haruharu.domain.notification.implement;

/**
 * 푸시 문구의 단일 관리 지점. 타입 추가 = 상수 한 줄 + 발송 트리거 연결.
 * title/body는 AI 초안(2026-07-04, 사용자 요청) — 기획 최종검수 후 확정 대상.
 */
public enum NotificationType {

    // ── A. 세션 진행 (3일 서사) ──
    ROOM_MATCHED("🎉 오늘의 인연이 정해졌어요", "3일간 하루하루 알아갈 사람들이 도착했어요"),
    ROOM_STARTED("하루방이 열렸어요", "지금 들어가 첫 영상으로 인사를 건네보세요 👋"),
    SLOT_START("새 시간대가 열렸어요 🎬", "지금 이 순간의 하루를 영상으로 남겨보세요"),
    SLOT_DEADLINE_SOON("⏰ 곧 이 시간대가 닫혀요", "30분 뒤 마감이에요. 오늘의 하루, 아직 안 남겼어요"),
    DAY_ADVANCED("새로운 하루가 시작됐어요", "어제보다 한 걸음 더 가까워질 시간이에요"),
    FINAL_SELECTION_OPEN("💘 마음을 전할 시간이에요", "3일간 지켜본 그 사람, 오늘 선택할 수 있어요"),
    FINAL_SELECTION_DEADLINE("⏳ 오늘이 지나면 사라져요", "아직 마음을 정하지 않았어요. 이 인연, 놓치실 건가요?"),

    // ── B. 소셜 상호작용 ──
    REACTION_RECEIVED("누군가 내 영상에 반응했어요 👀", "누가 관심을 보였을까요? 지금 확인해보세요"),
    NEW_VIDEO_POSTED("새 영상이 올라왔어요", "오늘의 그 사람은 어떤 모습일까요?"),
    MUTUAL_MATCH("💞 서로의 마음이 닿았어요", "두 분 다 서로를 선택했어요. 3일의 하루하루가 인연이 된 순간이에요"),
    SELECTED_BY_SOMEONE("설레는 소식이 있어요 💕", "누군가 당신을 마음에 담았어요. 누구일까요?"),

    // ── C. 이탈 방지 / 재참여 ──
    MATCHING_INVITE("오늘 밤, 새로운 인연이 기다려요", "자정에 매칭이 시작돼요. 지금 신청하고 설렘을 예약하세요"),
    WINBACK("하루하루가 당신을 기다렸어요", "그동안 새로운 인연이 늘었어요. 오랜만에 둘러볼까요?"),

    // ── D. 시스템 / 거래 ──
    COIN_REFUNDED("구름 5개를 돌려드렸어요 ☁️", "이번엔 인연이 닿지 않았지만, 다음 기회에 다시 만나요"),
    IDENTITY_VERIFIED("본인인증이 완료됐어요 ✅", "이제 하루하루의 모든 기능을 즐길 수 있어요"),
    PAYMENT_COMPLETED("구름이 충전됐어요 ☁️", "새로운 인연에 한 걸음 더 가까워졌어요");

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
