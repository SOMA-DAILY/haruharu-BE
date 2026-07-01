package org.son.haruharu.domain.profile.storage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.son.haruharu.common.entity.BaseEntity;
import org.son.haruharu.domain.profile.implement.ProfileFieldCatalog;

import java.util.UUID;

@Entity
@Table(
        name = "profile_field_catalog",
        uniqueConstraints = @UniqueConstraint(columnNames = "field_key")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProfileFieldCatalogEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "field_key", nullable = false, length = 50)
    private String fieldKey;

    // 열쇠 차감 비용(room_members.point_sum에서 차감).
    @Column(name = "unlock_cost", nullable = false)
    private int unlockCost;

    // 해금 가능 최소 방 진행일(currentDay >= 이 값일 때 해금 가능).
    @Column(name = "available_from_day", nullable = false)
    private int availableFromDay;

    @Column(nullable = false)
    private boolean active;

    public static ProfileFieldCatalogEntity from(ProfileFieldCatalog catalog) {
        var entity = new ProfileFieldCatalogEntity();
        entity.id = catalog.id();
        entity.fieldKey = catalog.fieldKey();
        entity.unlockCost = catalog.unlockCost();
        entity.availableFromDay = catalog.availableFromDay();
        entity.active = catalog.active();
        return entity;
    }

    public ProfileFieldCatalog toDomain() {
        return new ProfileFieldCatalog(id, fieldKey, unlockCost, availableFromDay, active);
    }
}
