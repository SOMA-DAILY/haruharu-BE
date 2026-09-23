package org.son.haruharu.domain.notification.implement;

/**
 * 푸시 문구의 단일 관리 지점. 타입 추가 = 상수 한 줄 + 발송 트리거 연결.
 * title/body는 AI 초안(2026-07-04, 사용자 요청) — 기획 최종검수 후 확정 대상.
 * title/body의 %d 등 포맷 지정자는 발송 시 PushSendService가 bodyArgs로 치환한다(환불·충전 개수 등 가변값).
 * inboxVisible: 알림함(#112) 저장·조회 대상 여부. 시한성 재촉·마케팅·순수결제는 false.
 * category: 수신 설정 분류(#117). ACTIVITY=opt-out, MARKETING=opt-in, TRANSACTIONAL=항상 발송.
 * quietHoursExempt: 야간 푸시 차단(#429) 예외 여부(#446). 유저 간 실시간 상호작용만 true.
 */
public enum NotificationType {

    // ── A. 세션 진행 (3일 서사) ──
    // 폐기된 자동매칭 알림. 기존 알림/발송 이력의 enum 역직렬화용으로 유지.
    ROOM_MATCHED("🎉 오늘의 인연이 정해졌어요", "3일간 함께할 인연이 도착했어요. 내일 만나요", true, NotificationCategory.ACTIVITY),
    ROOM_STARTED("👋 하루방이 열렸어요", "지금 첫 영상으로 인사를 건네보세요", true, NotificationCategory.ACTIVITY),
    SLOT_START("🎬 새로운 하루를 남길 시간", "서로의 하루를 확인하고, 오늘의 나도 남겨보세요", false, NotificationCategory.ACTIVITY),
    SLOT_DEADLINE_SOON("⏰ 30분 뒤 마감!", "오늘의 하루, 아직 안 남겼어요", false, NotificationCategory.ACTIVITY),
    FINAL_SELECTION_OPEN("💘 마음을 전할 시간이에요", "3일간 지켜본 그 사람, 오늘 선택할 수 있어요", true, NotificationCategory.ACTIVITY),

    // ── B. 소셜 상호작용 ──
    // %1$s=반응자 닉네임, %2$s=반응 동사(좋아해요·궁금해해요·공감해요) — 피그마 3-1a 카피 정렬(#213)
    // %3$s=반응 이모지(ReactionType.emoji, 알 수 없으면 👀) — 반응과 같은 이모지를 제목에 노출(#446)
    REACTION_RECEIVED("%3$s %1$s님이 반응을 남겼어요", "회원님의 영상을 %2$s", true, NotificationCategory.ACTIVITY, true),
    MUTUAL_MATCH("💞 서로의 마음이 닿았어요", "두 분 다 서로를 선택했어요! 지금 대화를 시작해보세요", true, NotificationCategory.ACTIVITY),
    SELECTED_BY_SOMEONE("💕 설레는 소식이 있어요", "누군가 당신을 마음에 담았어요. 누굴까요?", true, NotificationCategory.ACTIVITY),
    // body의 %s는 발신자 닉네임(bodyArgs)으로 치환. 대화 내용은 잠금화면 노출 방지를 위해 미포함.
    VIDEO_REPLY("하루하루", "%s님이 회원님의 영상에 메시지를 남겼어요", true, NotificationCategory.ACTIVITY, true),
    CHAT("💬 새 메시지", "%s님이 메시지를 보냈어요", true, NotificationCategory.ACTIVITY, true),
    // title의 %s는 업로더의 방 가명(pseudonym). 고볼륨·시한성이라 알림함 미저장(#422).
    VIDEO_UPLOADED("🎬 %s님이 하루를 남겼어요", "지금 영상을 확인해보세요", false, NotificationCategory.ACTIVITY, true),
    QUESTION_AVAILABLE("💌 익명 질문이 열렸어요", "궁금했던 마음을 질문으로 전해보세요", true, NotificationCategory.ACTIVITY),
    QUESTION_RECEIVED("💌 익명 질문이 도착했어요", "받은 질문을 확인해 보세요. 답변은 자유예요", true, NotificationCategory.ACTIVITY, true),
    QUESTION_ANSWERED("💌 답변이 도착했어요", "보낸 질문에 새로운 답변이 도착했어요", true, NotificationCategory.ACTIVITY, true),
    // 마감 2시간 전 재촉(#432). 시한성이라 알림함 미저장 — 마감 후 남으면 안내가 거짓이 된다.
    // 문구의 "2시간"은 QuestionDeadlineNotifier.DEADLINE_LEAD_HOURS 와 같이 바꿔야 한다.
    QUESTION_ANSWER_DEADLINE_SOON("⏰ 답을 기다리는 질문이 있어요", "2시간 뒤 마감되면 답할 수 없어요", false, NotificationCategory.ACTIVITY),
    QUESTION_SEND_DEADLINE_SOON("⏰ 질문할 시간이 2시간 남았어요", "궁금했던 걸 지금 물어보세요", false, NotificationCategory.ACTIVITY),

    // ── C. 시스템 / 거래 ──
    // 폐기된 자동매칭 환불 알림의 이력 호환용. 발송 트리거는 제거됨.
    COIN_REFUNDED("☁️ 구름 %d개를 돌려드렸어요", "이번엔 인연이 닿지 않았지만, 다음 기회에 다시 만나요", true, NotificationCategory.TRANSACTIONAL),
    // Figma 5-5 매칭 미성사 화면 카피("위로의 구름을 선물로 드렸어요") 대응(#143)
    MATCH_CONSOLATION("☁️ 위로의 구름이 도착했어요", "이번엔 인연이 닿지 않았지만, 위로의 구름 %d개를 선물로 드렸어요", true, NotificationCategory.TRANSACTIONAL),
    PAYMENT_COMPLETED("☁️ 충전 완료!", "구름 %d개가 충전됐어요. 새로운 인연에 한 걸음 더 가까워졌어요", false, NotificationCategory.TRANSACTIONAL),

    // 폐기된 자동매칭 재참여 알림의 이력 호환용. 발송 트리거는 제거됨.
    WINBACK("💫 새로운 인연이 도착했어요", "오늘 밤 매칭에 당신의 자리를 남겨뒀어요. 지금 신청해보세요", false, NotificationCategory.MARKETING);

    private final String title;
    private final String body;
    private final boolean inboxVisible;
    private final NotificationCategory category;
    private final boolean quietHoursExempt;

    NotificationType(String title, String body, boolean inboxVisible, NotificationCategory category) {
        this(title, body, inboxVisible, category, false);
    }

    NotificationType(String title, String body, boolean inboxVisible, NotificationCategory category,
                     boolean quietHoursExempt) {
        this.title = title;
        this.body = body;
        this.inboxVisible = inboxVisible;
        this.category = category;
        this.quietHoursExempt = quietHoursExempt;
    }

    public String title() {
        return title;
    }

    public String body() {
        return body;
    }

    public boolean inboxVisible() {
        return inboxVisible;
    }

    public NotificationCategory category() {
        return category;
    }

    public boolean quietHoursExempt() {
        return quietHoursExempt;
    }
}
