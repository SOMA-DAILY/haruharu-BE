package org.son.haruharu.domain.profile.implement;

import org.son.haruharu.domain.profile.implement.exception.InvalidProfileFieldException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProfileFieldCatalogReader {

    private final ProfileFieldCatalogRepository profileFieldCatalogRepository;

    public ProfileFieldCatalogReader(ProfileFieldCatalogRepository profileFieldCatalogRepository) {
        this.profileFieldCatalogRepository = profileFieldCatalogRepository;
    }

    public List<ProfileFieldCatalog> getAllActive() {
        return profileFieldCatalogRepository.findAllActive();
    }

    public ProfileFieldCatalog getByFieldKey(String fieldKey) {
        return profileFieldCatalogRepository.findByFieldKey(fieldKey)
                .orElseThrow(InvalidProfileFieldException::new);
    }

    public boolean existsByFieldKey(String fieldKey) {
        return profileFieldCatalogRepository.existsByFieldKey(fieldKey);
    }
}
