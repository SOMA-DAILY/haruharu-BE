package org.son.haruharu.admin.domain.room.implement;

import java.util.List;

public interface RoomActivityRepository {
    List<RoomActivity> find(RoomActivity.Filter filter, int offset, int limit, boolean oldestFirst);
    long count(RoomActivity.Filter filter);
    List<RoomActivity.Count> counts(RoomActivity.Filter filter);
}
