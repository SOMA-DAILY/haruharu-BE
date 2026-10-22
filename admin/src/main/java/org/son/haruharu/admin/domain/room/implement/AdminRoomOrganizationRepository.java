package org.son.haruharu.admin.domain.room.implement;

import java.util.*;

public interface AdminRoomOrganizationRepository {
    List<AdminRoomGroup> groups(int offset, int limit);

    long countGroups();

    Optional<AdminRoomGroup> group(Long id);

    AdminRoomGroup createGroup(String name);

    Map<Long, AdminRoomOrganization> findAll(List<Long> roomIds);

    void lockRooms(List<Long> roomIds);

    void save(AdminRoomOrganization value);
}
