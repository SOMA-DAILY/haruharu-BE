package org.son.haruharu.domain.mission.chat.business.command;

import java.time.LocalDateTime;
import org.son.haruharu.domain.mission.chat.implement.MissionAnnouncementType;

public record MissionAnnouncementCommand(MissionAnnouncementType announcementType, LocalDateTime runAt,
                                          String content, Boolean push, String pushTitle, String pushBody) {}
