package org.son.haruharu.domain.mission.chat.business.dto;
import java.util.List;
public record AnnouncementPageResponse(List<MissionAnnouncementResponse> announcements, Long nextCursor) {}
