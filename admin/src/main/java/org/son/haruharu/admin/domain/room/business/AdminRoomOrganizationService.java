package org.son.haruharu.admin.domain.room.business;

import org.son.haruharu.admin.common.PageResponse;
import org.son.haruharu.admin.domain.room.business.exception.*;
import org.son.haruharu.admin.domain.room.implement.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@Transactional(readOnly = true)
public class AdminRoomOrganizationService {
    private final AdminRoomOrganizationManager manager;

    public AdminRoomOrganizationService(AdminRoomOrganizationManager manager) {
        this.manager = manager;
    }

    public PageResponse<AdminRoomGroup> groups(int page, int size) {
        int p = Math.max(0, page), s = Math.max(1, Math.min(100, size));
        return PageResponse.of(manager.groups(p * s, s), p, s, manager.countGroups());
    }

    @Transactional
    public AdminRoomGroup createGroup(String name) {
        try {
            return manager.createGroup(name);
        } catch (DataIntegrityViolationException e) {
            throw new AdminRoomGroupDuplicateException();
        }
    }

    @Transactional
    public void assign(List<Long> ids, Long groupId, boolean clearGroup, Boolean isTest) {
        if ((groupId == null && !clearGroup && isTest == null) || (groupId != null && clearGroup))
            throw new AdminRoomOrganizationInvalidException();
        manager.assign(ids, groupId, clearGroup, isTest);
    }
}
