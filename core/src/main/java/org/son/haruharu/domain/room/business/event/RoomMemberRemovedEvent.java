package org.son.haruharu.domain.room.business.event;
import java.util.List;
public record RoomMemberRemovedEvent(Long roomId,Long userId,List<Long> closedChatIds) {}
