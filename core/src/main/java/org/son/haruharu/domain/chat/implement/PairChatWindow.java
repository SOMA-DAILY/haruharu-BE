package org.son.haruharu.domain.chat.implement;

import java.time.LocalDateTime;

/**
 * 한 방의 1:1 대화 시간 상태(#540).
 * OPEN: 지금 열린 시간의 시작·끝. SCHEDULED: 다음 시간의 시작·끝. NONE: 남은 대화 시간이 없다(모두 null).
 * opensDay 는 방 시작일 기준 일차(1부터, 앱 표기 "DAY n").
 */
public record PairChatWindow(State state, LocalDateTime opensAt, Integer opensDay, LocalDateTime closesAt) {

    public enum State { OPEN, SCHEDULED, NONE }

    private static final PairChatWindow NONE = new PairChatWindow(State.NONE, null, null, null);

    public static PairChatWindow none() { return NONE; }

    public static PairChatWindow open(LocalDateTime opensAt, Integer opensDay, LocalDateTime closesAt) {
        return new PairChatWindow(State.OPEN, opensAt, opensDay, closesAt);
    }

    public static PairChatWindow scheduled(LocalDateTime opensAt, Integer opensDay, LocalDateTime closesAt) {
        return new PairChatWindow(State.SCHEDULED, opensAt, opensDay, closesAt);
    }

    public boolean isOpen() { return state == State.OPEN; }
}
