package org.son.haruharu.domain.chat.implement;
import org.son.haruharu.domain.mission.business.dto.MissionImageResponse;
public record ChatMission(Long missionId, Long roomId, String templateId, String title, Long imageId, MissionImageResponse image) {
    public ChatMission(Long missionId,Long roomId,String templateId,String title) { this(missionId,roomId,templateId,title,null,null); }
    public ChatMission withImage(MissionImageResponse image) { return new ChatMission(missionId,roomId,templateId,title,imageId,image); }
}
