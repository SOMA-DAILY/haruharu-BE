package org.son.haruharu.admin.domain.room.implement;

import org.son.haruharu.admin.domain.room.business.exception.AdminRoomGroupNotFoundException;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class AdminRoomOrganizationManager {
    private final AdminRoomOrganizationRepository repo;

    public AdminRoomOrganizationManager(AdminRoomOrganizationRepository repo) {
        this.repo = repo;
    }

    public List<AdminRoomGroup> groups(int offset, int limit) {
        return repo.groups(offset, limit);
    }

    public long countGroups() {
        return repo.countGroups();
    }

    public AdminRoomGroup group(Long id) {
        return repo.group(id).orElseThrow(AdminRoomGroupNotFoundException::new);
    }

    public Map<Long, AdminRoomOrganization> findAll(List<Long> ids) {
        return ids.isEmpty() ? Map.of() : repo.findAll(ids);
    }

    public AdminRoomGroup createGroup(String name) {
        return repo.createGroup(name.strip());
    }

    public void assign(List<Long> ids, Long groupId, boolean clearGroup, Boolean isTest) {
        if (groupId != null) group(groupId);
        // Stable lock order serializes partial metadata edits, including first assignment.
        var ordered = ids.stream().distinct().sorted().toList();
        repo.lockRooms(ordered);
        var existing = repo.findAll(ordered);
        for (Long id : ordered) {
            var old = existing.getOrDefault(id, new AdminRoomOrganization(id, null, null));
            repo.save(
                    new AdminRoomOrganization(
                            id,
                            clearGroup ? null : groupId != null ? groupId : old.groupId(),
                            isTest != null ? isTest : old.isTest()));
        }
    }

    public void initialize(Long roomId, Long groupId, Boolean isTest) {
        Long selected = groupId;
        if (selected == null)
            selected = groups(0, 1).stream().findFirst().map(AdminRoomGroup::id).orElse(null);
        assign(List.of(roomId), selected, false, isTest == null ? false : isTest);
    }
}
