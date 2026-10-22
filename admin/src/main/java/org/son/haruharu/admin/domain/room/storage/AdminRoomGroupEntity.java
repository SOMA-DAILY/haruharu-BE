package org.son.haruharu.admin.domain.room.storage;

import jakarta.persistence.*;

import org.son.haruharu.admin.domain.room.implement.AdminRoomGroup;

@Entity
@Table(
        name = "admin_room_groups",
        uniqueConstraints =
                @UniqueConstraint(name = "uk_admin_room_group_name", columnNames = "name"))
public class AdminRoomGroupEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    protected AdminRoomGroupEntity() {}

    public AdminRoomGroupEntity(String name) {
        this.name = name;
    }

    public AdminRoomGroup toDomain() {
        return new AdminRoomGroup(id, name);
    }
}
