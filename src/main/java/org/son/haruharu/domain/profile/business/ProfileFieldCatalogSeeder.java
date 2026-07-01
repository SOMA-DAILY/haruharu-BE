package org.son.haruharu.domain.profile.business;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.son.haruharu.domain.profile.implement.ProfileField;
import org.son.haruharu.domain.profile.implement.ProfileFieldCatalog;
import org.son.haruharu.domain.profile.implement.ProfileFieldCatalogReader;
import org.son.haruharu.domain.profile.implement.ProfileFieldCatalogWriter;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 해금 가능한 프로필 항목 카탈로그 시드. field_key별로 없을 때만 삽입(멱등, 신규 항목 자동 반영).
 * 비용·해금 가능 Day는 운영 확정 전 잠정값 — 추후 어드민에서 조정.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProfileFieldCatalogSeeder implements ApplicationRunner {

    private final ProfileFieldCatalogReader profileFieldCatalogReader;
    private final ProfileFieldCatalogWriter profileFieldCatalogWriter;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        seed(ProfileField.AFFILIATION, 10, 3);
        seed(ProfileField.HEIGHT, 15, 3);
        seed(ProfileField.MBTI, 5, 3);
    }

    private void seed(ProfileField field, int unlockCost, int availableFromDay) {
        if (profileFieldCatalogReader.existsByFieldKey(field.key())) {
            return;
        }
        profileFieldCatalogWriter.save(ProfileFieldCatalog.of(field.key(), unlockCost, availableFromDay));
        log.info("프로필 항목 카탈로그 시드: {} (cost={}, fromDay={})", field.key(), unlockCost, availableFromDay);
    }
}
