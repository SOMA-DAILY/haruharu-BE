package org.son.haruharu.domain.profile.implement;

import java.util.List;
import java.util.Optional;

public interface ProfileFieldCatalogRepository {

    List<ProfileFieldCatalog> findAllActive();

    Optional<ProfileFieldCatalog> findByFieldKey(String fieldKey);

    boolean existsByFieldKey(String fieldKey);

    ProfileFieldCatalog save(ProfileFieldCatalog catalog);
}
