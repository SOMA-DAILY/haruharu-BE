package org.son.haruharu.domain.profile.implement;

import org.springframework.stereotype.Component;

@Component
public class ProfileFieldCatalogWriter {

    private final ProfileFieldCatalogRepository profileFieldCatalogRepository;

    public ProfileFieldCatalogWriter(ProfileFieldCatalogRepository profileFieldCatalogRepository) {
        this.profileFieldCatalogRepository = profileFieldCatalogRepository;
    }

    public ProfileFieldCatalog save(ProfileFieldCatalog catalog) {
        return profileFieldCatalogRepository.save(catalog);
    }
}
