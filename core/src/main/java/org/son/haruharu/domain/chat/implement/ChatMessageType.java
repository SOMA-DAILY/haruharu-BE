package org.son.haruharu.domain.chat.implement;

/**
 * 메시지 종류(#457). TEXT = 참가자 메시지(영상 답장 포함, video_post_id 로 구분), ADMIN = 운영 글(시스템 유저 '하루').
 * 기존 행은 전부 TEXT(컬럼 default).
 */
public enum ChatMessageType {
    TEXT,
    ADMIN;

    /** 운영 글 표시 이름. 시스템 유저는 채팅방 멤버가 아니라 members 에서 찾을 수 없어 메시지의 sender_name 으로 내린다. */
    public static final String SYSTEM_SENDER_NAME = "하루";
}
