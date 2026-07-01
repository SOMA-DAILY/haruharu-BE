package org.son.haruharu.domain.profile.storage;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProfileFieldCatalogJpaRepository extends JpaRepository<ProfileFieldCatalogEntity, UUID> {

    List<ProfileFieldCatalogEntity> findByActiveTrueOrderByAvailableFromDayAsc();

    Optional<ProfileFieldCatalogEntity> findByFieldKey(String fieldKey);

    boolean existsByFieldKey(String fieldKey);
}
