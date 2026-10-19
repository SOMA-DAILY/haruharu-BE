package org.son.haruharu.domain.chat.implement;

/** 메시지의 발신자 유형. 유저 계정의 역할이나 운영자 권한과는 무관하다. */
public enum ChatSenderType {
    USER,
    SYSTEM;

    public static final String SYSTEM_SENDER_NAME = "하루";
}
