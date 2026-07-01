package org.son.haruharu.domain.profile.storage;

import org.son.haruharu.domain.profile.implement.ProfileFieldCatalog;
import org.son.haruharu.domain.profile.implement.ProfileFieldCatalogRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ProfileFieldCatalogRepositoryImpl implements ProfileFieldCatalogRepository {

    private final ProfileFieldCatalogJpaRepository profileFieldCatalogJpaRepository;

    public ProfileFieldCatalogRepositoryImpl(ProfileFieldCatalogJpaRepository profileFieldCatalogJpaRepository) {
        this.profileFieldCatalogJpaRepository = profileFieldCatalogJpaRepository;
    }

    @Override
    public List<ProfileFieldCatalog> findAllActive() {
        return profileFieldCatalogJpaRepository.findByActiveTrueOrderByAvailableFromDayAsc()
                .stream()
                .map(ProfileFieldCatalogEntity::toDomain)
                .toList();
    }

    @Override
    public Optional<ProfileFieldCatalog> findByFieldKey(String fieldKey) {
        return profileFieldCatalogJpaRepository.findByFieldKey(fieldKey)
                .map(ProfileFieldCatalogEntity::toDomain);
    }

    @Override
    public boolean existsByFieldKey(String fieldKey) {
        return profileFieldCatalogJpaRepository.existsByFieldKey(fieldKey);
    }

    @Override
    public ProfileFieldCatalog save(ProfileFieldCatalog catalog) {
        return profileFieldCatalogJpaRepository.save(ProfileFieldCatalogEntity.from(catalog)).toDomain();
    }
}
