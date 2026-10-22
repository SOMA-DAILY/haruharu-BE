package org.son.haruharu.admin.domain.room.storage;

import jakarta.persistence.*;

import org.son.haruharu.admin.domain.room.implement.AdminRoomOrganization;

@Entity
@Table(
        name = "admin_room_organizations",
        indexes = @Index(name = "idx_admin_room_org_group_type", columnList = "group_id,is_test"))
public class AdminRoomOrganizationEntity {
    @Id private Long roomId;
    private Long groupId;
    private Boolean isTest;

    protected AdminRoomOrganizationEntity() {}

    public AdminRoomOrganizationEntity(AdminRoomOrganization v) {
        roomId = v.roomId();
        groupId = v.groupId();
        isTest = v.isTest();
    }

    public AdminRoomOrganization toDomain() {
        return new AdminRoomOrganization(roomId, groupId, isTest);
    }
}
