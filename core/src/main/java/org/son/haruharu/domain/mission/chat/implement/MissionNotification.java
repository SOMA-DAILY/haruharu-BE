package org.son.haruharu.domain.mission.chat.implement;

import org.son.haruharu.domain.notification.implement.NotificationType;

/** 유형이 결정한 알림. 실행기는 커밋 후 공통 수신자에게 전달한다. */
public record MissionNotification(NotificationType type, String title, String body) {}
