package org.son.haruharu.domain.room.implement;
import java.util.List;
public record RoomVisibility(String version,List<Long> removedRoomIds,List<Long> closedChatIds) {}
